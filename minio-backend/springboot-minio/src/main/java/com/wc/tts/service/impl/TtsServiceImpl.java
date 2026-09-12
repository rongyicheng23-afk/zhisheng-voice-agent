package com.wc.tts.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wc.tts.config.TtsProperties;
import com.wc.tts.model.TtsSynthesisResult;
import com.wc.tts.model.TtsRequestLimits;
import com.wc.tts.model.TtsUpstreamException;
import com.wc.tts.service.TtsService;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TtsServiceImpl implements TtsService {

    private final RestTemplate ttsRestTemplate;
    private final OkHttpClient ttsOkHttpClient;
    private final TtsProperties ttsProperties;

    public TtsServiceImpl(
            @Qualifier("ttsRestTemplate") RestTemplate ttsRestTemplate,
            @Qualifier("ttsOkHttpClient") OkHttpClient ttsOkHttpClient,
            TtsProperties ttsProperties
    ) {
        this.ttsRestTemplate = ttsRestTemplate;
        this.ttsOkHttpClient = ttsOkHttpClient;
        this.ttsProperties = ttsProperties;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("httpBaseUrl", ttsProperties.getHttpBaseUrl());

        try {
            JsonNode healthResponse = ttsRestTemplate.getForObject(
                    buildUrl(ttsProperties.getHttpBaseUrl(), ttsProperties.getHealthPath()),
                    JsonNode.class
            );
            result.put("httpReachable", true);
            result.put("httpResponse", healthResponse);
        } catch (Exception ex) {
            result.put("httpReachable", false);
            result.put("httpError", ex.getMessage());
        }

        return result;
    }

    @Override
    public TtsSynthesisResult synthesize(
            MultipartFile audio,
            String text,
            String emotion,
            String language,
            String format
    ) throws IOException {
        TtsRequestLimits.validateAudio(audio);
        TtsRequestLimits.validateText(text);

        byte[] audioBytes = audio.getBytes();
        MediaType mediaType = MediaType.parse(
                StringUtils.hasText(audio.getContentType()) ? audio.getContentType() : "application/octet-stream"
        );

        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "audio",
                        safeFilename(audio.getOriginalFilename(), "reference.wav"),
                        RequestBody.create(audioBytes, mediaType)
                )
                .addFormDataPart("text", text.trim())
                .addFormDataPart("emotion", StringUtils.hasText(emotion) ? emotion.trim() : "neutral")
                .addFormDataPart("language", StringUtils.hasText(language) ? language.trim() : "zh-cn");

        if (StringUtils.hasText(format)) {
            multipartBuilder.addFormDataPart("format", format.trim());
        }

        MultipartBody requestBody = multipartBuilder.build();
        if (requestBody.contentLength() > TtsRequestLimits.MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("完整上传请求不能超过20 MB，请缩小参考音频");
        }
        String requestUrl = buildUrl(ttsProperties.getHttpBaseUrl(), ttsProperties.getSynthesizePath());
        Request request = new Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build();

        try (Response response = ttsOkHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw switch (response.code()) {
                    case 503, 429 -> new TtsUpstreamException(503, "语音合成服务忙，请稍后手动重试");
                    case 413 -> new TtsUpstreamException(413, "完整上传请求不能超过20 MB，请缩小参考音频");
                    case 400 -> new TtsUpstreamException(400, "合成参数无效，请检查文本、参考音频、语言和情感");
                    default -> new TtsUpstreamException(502, "语音合成服务异常，请稍后重试");
                };
            }
            // Bound allocation even when an upstream response has no Content-Length.
            byte[] responseBytes = response.body() == null ? new byte[0]
                    : response.body().byteStream().readNBytes(32 * 1024 * 1024 + 1);
            if (responseBytes.length <= 44 || responseBytes.length > 32 * 1024 * 1024) {
                throw new TtsUpstreamException(502, "语音合成返回的音频无效");
            }

            TtsSynthesisResult result = new TtsSynthesisResult();
            result.setAudioBytes(responseBytes);
            result.setContentType(response.header("Content-Type"));
            result.setUpstreamFilename(extractFilenameFromDisposition(response.header("Content-Disposition")));
            result.setContentLength(responseBytes.length);
            return result;
        } catch (TtsUpstreamException ex) {
            throw ex;
        } catch (java.net.SocketTimeoutException ex) {
            throw new TtsUpstreamException(504, "语音合成等待超时，服务端可能仍在处理，请稍后查看历史再重试");
        } catch (IOException ex) {
            throw new TtsUpstreamException(502, "语音合成连接中断，服务端可能仍在处理，请稍后查看历史再重试");
        }
    }

    private String buildUrl(String baseUrl, String path) {
        String base = StringUtils.trimTrailingCharacter(baseUrl, '/');
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String safeFilename(String filename, String defaultName) {
        return StringUtils.hasText(filename) ? filename : defaultName;
    }

    private String extractFilenameFromDisposition(String contentDisposition) {
        if (!StringUtils.hasText(contentDisposition)) {
            return null;
        }
        for (String part : contentDisposition.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("filename=")) {
                return trimmed.substring("filename=".length()).replace("\"", "").trim();
            }
        }
        return null;
    }
}
