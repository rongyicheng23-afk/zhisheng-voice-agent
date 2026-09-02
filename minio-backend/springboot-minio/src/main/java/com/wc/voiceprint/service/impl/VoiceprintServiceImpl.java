package com.wc.voiceprint.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.voiceprint.config.VoiceprintProperties;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import com.wc.voiceprint.service.VoiceprintService;
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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VoiceprintServiceImpl implements VoiceprintService {

    private final RestTemplate voiceprintRestTemplate;
    private final OkHttpClient voiceprintOkHttpClient;
    private final VoiceprintProperties voiceprintProperties;
    private final ObjectMapper objectMapper;

    public VoiceprintServiceImpl(
            @Qualifier("voiceprintRestTemplate") RestTemplate voiceprintRestTemplate,
            @Qualifier("voiceprintOkHttpClient") OkHttpClient voiceprintOkHttpClient,
            VoiceprintProperties voiceprintProperties,
            ObjectMapper objectMapper
    ) {
        this.voiceprintRestTemplate = voiceprintRestTemplate;
        this.voiceprintOkHttpClient = voiceprintOkHttpClient;
        this.voiceprintProperties = voiceprintProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("httpBaseUrl", voiceprintProperties.getHttpBaseUrl());
        try {
            JsonNode healthResponse = voiceprintRestTemplate.getForObject(
                    buildUrl(voiceprintProperties.getHttpBaseUrl(), voiceprintProperties.getHealthPath()),
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
    public VoiceprintCompareResult compare(MultipartFile file1, MultipartFile file2) throws IOException {
        validateAudio(file1, "请上传音频 A");
        validateAudio(file2, "请上传音频 B");

        Request request = new Request.Builder()
                .url(buildUrl(voiceprintProperties.getHttpBaseUrl(), voiceprintProperties.getComparePath()))
                .post(new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                                "file1",
                                safeFilename(file1.getOriginalFilename(), "audio_a.wav"),
                                RequestBody.create(file1.getBytes(), resolveMediaType(file1.getContentType()))
                        )
                        .addFormDataPart(
                                "file2",
                                safeFilename(file2.getOriginalFilename(), "audio_b.wav"),
                                RequestBody.create(file2.getBytes(), resolveMediaType(file2.getContentType()))
                        )
                        .build())
                .build();

        try (Response response = voiceprintOkHttpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + response.message()
                        + " on POST request for \"" + buildUrl(voiceprintProperties.getHttpBaseUrl(), voiceprintProperties.getComparePath())
                        + "\": \"" + body + "\"");
            }

            JsonNode json = StringUtils.hasText(body) ? objectMapper.readTree(body) : objectMapper.createObjectNode();

            VoiceprintCompareResult result = new VoiceprintCompareResult();
            result.setStatus(json.path("status").asText(""));
            result.setFile1Name(json.path("file1_name").asText(""));
            result.setFile2Name(json.path("file2_name").asText(""));
            if (json.hasNonNull("score")) {
                result.setScore(json.path("score").decimalValue());
            }
            if (json.hasNonNull("threshold")) {
                result.setThreshold(json.path("threshold").decimalValue());
            }
            if (json.has("is_same_person")) {
                result.setSamePerson(json.path("is_same_person").asBoolean());
            }
            result.setMessage(json.path("message").asText(""));
            return result;
        }
    }

    private void validateAudio(MultipartFile file, String errorMessage) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private MediaType resolveMediaType(String contentType) {
        return MediaType.parse(StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
    }

    private String buildUrl(String baseUrl, String path) {
        String base = StringUtils.trimTrailingCharacter(baseUrl, '/');
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String safeFilename(String filename, String defaultName) {
        return StringUtils.hasText(filename) ? filename : defaultName;
    }
}
