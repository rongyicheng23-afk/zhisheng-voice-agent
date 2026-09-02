package com.wc.voiceprint.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class VoiceprintClientConfig {

    private final VoiceprintProperties voiceprintProperties;

    public VoiceprintClientConfig(VoiceprintProperties voiceprintProperties) {
        this.voiceprintProperties = voiceprintProperties;
    }

    @Bean
    public RestTemplate voiceprintRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(voiceprintProperties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(voiceprintProperties.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public OkHttpClient voiceprintOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(voiceprintProperties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(voiceprintProperties.getReadTimeoutMs()))
                .build();
    }
}
