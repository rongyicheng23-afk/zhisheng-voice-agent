package com.wc.tts.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class TtsClientConfig {

    private final TtsProperties ttsProperties;

    public TtsClientConfig(TtsProperties ttsProperties) {
        this.ttsProperties = ttsProperties;
    }

    @Bean
    public RestTemplate ttsRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(ttsProperties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(ttsProperties.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public OkHttpClient ttsOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(ttsProperties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(ttsProperties.getReadTimeoutMs()))
                .build();
    }
}
