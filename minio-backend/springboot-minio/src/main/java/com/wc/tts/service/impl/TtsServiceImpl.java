package com.wc.tts.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wc.tts.config.TtsProperties;
import com.wc.tts.model.TtsSynthesisResult;
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
        validateAudio(audio);
        validateText(text);

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

        String requestUrl = buildUrl(ttsProperties.getHttpBaseUrl(), ttsProperties.getSynthesizePath());
        Request request = new Request.Builder()
                .url(requestUrl)
                .post(multipartBuilder.build())
                .build();

        try (Response response = ttsOkHttpClient.newCall(request).execute()) {
            byte[] responseBytes = response.body() == null ? new byte[0] : response.body().bytes();
            if (!response.isSuccessful()) {
                String errorBody = new String(responseBytes);
                throw new IOException(response.code() + " " + response.message()
                        + " on POST request for \"" + requestUrl + "\": \"" + errorBody + "\"");
            }

            TtsSynthesisResult result = new TtsSynthesisResult();
            result.setAudioBytes(responseBytes);
            result.setContentType(response.header("Content-Type"));
            result.setUpstreamFilename(extractFilenameFromDisposition(response.header("Content-Disposition")));
            result.setContentLength(responseBytes.length);
            return result;
        }
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("请上传参考音频");
        }
    }

    private void validateText(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("请输入要合成的文本");
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
