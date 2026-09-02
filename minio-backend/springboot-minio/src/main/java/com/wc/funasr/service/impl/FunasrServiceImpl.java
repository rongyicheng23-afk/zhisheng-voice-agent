package com.wc.funasr.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wc.funasr.config.FunasrProperties;
import com.wc.funasr.service.FunasrService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FunasrServiceImpl implements FunasrService {

    private final RestTemplate funasrRestTemplate;
    private final OkHttpClient funasrOkHttpClient;
    private final HttpClient funasrHttpClient;
    private final FunasrProperties funasrProperties;
    private final ObjectMapper objectMapper;

    public FunasrServiceImpl(
            @Qualifier("funasrRestTemplate") RestTemplate funasrRestTemplate,
            @Qualifier("funasrOkHttpClient") OkHttpClient funasrOkHttpClient,
            @Qualifier("funasrHttpClient") HttpClient funasrHttpClient,
            FunasrProperties funasrProperties,
            ObjectMapper objectMapper
    ) {
        this.funasrRestTemplate = funasrRestTemplate;
        this.funasrOkHttpClient = funasrOkHttpClient;
        this.funasrHttpClient = funasrHttpClient;
        this.funasrProperties = funasrProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("httpBaseUrl", funasrProperties.getHttpBaseUrl());
        result.put("wsUrl", funasrProperties.getWsUrl());

        try {
            JsonNode healthResponse = funasrRestTemplate.getForObject(
                    buildUrl(funasrProperties.getHttpBaseUrl(), funasrProperties.getHealthPath()),
                    JsonNode.class
            );
            result.put("httpReachable", true);
            result.put("httpResponse", healthResponse);
        } catch (Exception ex) {
            result.put("httpReachable", false);
            result.put("httpError", ex.getMessage());
        }

        result.put("wsConfigured", StringUtils.hasText(funasrProperties.getWsUrl()));
        return result;
    }

    @Override
    public JsonNode transcribeAudio(MultipartFile file, Integer batchSizeS, String hotword) throws IOException {
        validateFile(file);
        byte[] fileBytes = file.getBytes();
        okhttp3.MediaType fileMediaType = okhttp3.MediaType.parse(
                StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream"
        );

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "file",
                        safeFilename(file.getOriginalFilename()),
                        RequestBody.create(fileBytes, fileMediaType)
                )
                .addFormDataPart("batch_size_s", String.valueOf(batchSizeS == null ? 300 : batchSizeS));
        if (StringUtils.hasText(hotword)) {
            multipartBuilder.addFormDataPart("hotword", hotword);
        }

        Request request = new Request.Builder()
                .url(buildUrl(funasrProperties.getHttpBaseUrl(), funasrProperties.getAsrPath()))
                .post(multipartBuilder.build())
                .build();

        try (Response response = funasrOkHttpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message() + " on POST request for \"" +
                        buildUrl(funasrProperties.getHttpBaseUrl(), funasrProperties.getAsrPath()) + "\": \"" + body + "\"");
            }
            return StringUtils.hasText(body) ? objectMapper.readTree(body) : objectMapper.createObjectNode();
        }
    }

    @Override
    public Map<String, Object> transcribeRealtimePcm(
            MultipartFile file,
            String wavName,
            String mode,
            String chunkSize,
            int chunkInterval,
            int encoderChunkLookBack,
            int decoderChunkLookBack,
            String hotwords
    ) throws Exception {
        validateFile(file);
        validatePcmFile(file);

        String resolvedMode = StringUtils.hasText(mode) ? mode : "2pass";
        String resolvedWavName = StringUtils.hasText(wavName) ? wavName : stripExtension(safeFilename(file.getOriginalFilename()));
        int[] parsedChunkSize = parseChunkSize(chunkSize);
        int frameBytes = Math.max(32, funasrProperties.getRealtimeFrameDurationMs() * 32);
        byte[] audioBytes = file.getBytes();

        CollectingWebSocketListener listener = new CollectingWebSocketListener(objectMapper);
        WebSocket webSocket = funasrHttpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                .subprotocols("binary")
                .buildAsync(URI.create(funasrProperties.getWsUrl()), listener)
                .join();

        try {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("mode", resolvedMode);
            config.put("wav_name", resolvedWavName);
            config.put("is_speaking", true);
            config.put("chunk_interval", chunkInterval);
            config.put("encoder_chunk_look_back", encoderChunkLookBack);
            config.put("decoder_chunk_look_back", decoderChunkLookBack);

            ArrayNode chunkNode = config.putArray("chunk_size");
            for (int value : parsedChunkSize) {
                chunkNode.add(value);
            }
            if (StringUtils.hasText(hotwords)) {
                config.put("hotwords", hotwords);
            }

            webSocket.sendText(objectMapper.writeValueAsString(config), true).join();

            for (int offset = 0; offset < audioBytes.length; offset += frameBytes) {
                int currentLength = Math.min(frameBytes, audioBytes.length - offset);
                webSocket.sendBinary(ByteBuffer.wrap(audioBytes, offset, currentLength), true).join();
            }

            webSocket.sendText("{\"is_speaking\":false}", true).join();
            List<JsonNode> messages = listener.awaitMessages(
                    funasrProperties.getRealtimeIdleTimeoutMs(),
                    funasrProperties.getRealtimeOverallTimeoutMs()
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("filename", safeFilename(file.getOriginalFilename()));
            result.put("wavName", resolvedWavName);
            result.put("mode", resolvedMode);
            result.put("chunkSize", Arrays.toString(parsedChunkSize));
            result.put("chunkInterval", chunkInterval);
            result.put("messageCount", messages.size());
            result.put("messages", messages);
            return result;
        } finally {
            closeQuietly(webSocket);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传音频文件");
        }
    }

    private void validatePcmFile(MultipartFile file) {
        String filename = safeFilename(file.getOriginalFilename()).toLowerCase();
        if (!filename.endsWith(".pcm")) {
            throw new IllegalArgumentException("实时识别接口目前要求上传 16k/16bit/单声道 PCM 文件（.pcm）");
        }
    }

    private int[] parseChunkSize(String chunkSize) {
        String value = StringUtils.hasText(chunkSize) ? chunkSize : "5,10,5";
        int[] parsed = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .mapToInt(Integer::parseInt)
                .toArray();
        if (parsed.length != 3) {
            throw new IllegalArgumentException("chunkSize 格式必须是类似 5,10,5 的 3 段数字");
        }
        return parsed;
    }

    private String buildUrl(String baseUrl, String path) {
        String base = StringUtils.trimTrailingCharacter(baseUrl, '/');
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String safeFilename(String filename) {
        return StringUtils.hasText(filename) ? filename : "audio.pcm";
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }

    private void closeQuietly(WebSocket webSocket) {
        if (webSocket == null) {
            return;
        }
        try {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        } catch (Exception ignored) {
            webSocket.abort();
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    private static final class CollectingWebSocketListener implements WebSocket.Listener {

        private final ObjectMapper objectMapper;
        private final List<JsonNode> messages = new CopyOnWriteArrayList<>();
        private final StringBuilder textBuffer = new StringBuilder();
        private final AtomicLong lastMessageAt = new AtomicLong(System.currentTimeMillis());

        private CollectingWebSocketListener(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                lastMessageAt.set(System.currentTimeMillis());
                try {
                    messages.add(objectMapper.readTree(message));
                } catch (Exception ex) {
                    ObjectNode rawMessage = objectMapper.createObjectNode();
                    rawMessage.put("raw", message);
                    rawMessage.put("parseError", ex.getMessage());
                    messages.add(rawMessage);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            lastMessageAt.set(System.currentTimeMillis());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            lastMessageAt.set(System.currentTimeMillis());
        }

        private List<JsonNode> awaitMessages(int idleTimeoutMs, int overallTimeoutMs) throws InterruptedException {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < overallTimeoutMs) {
                boolean hasMessages = !messages.isEmpty();
                boolean quietEnough = System.currentTimeMillis() - lastMessageAt.get() >= idleTimeoutMs;
                if (hasMessages && quietEnough) {
                    return List.copyOf(messages);
                }
                TimeUnit.MILLISECONDS.sleep(100);
            }
            return List.copyOf(messages);
        }
    }
}
