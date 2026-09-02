package com.wc.funasr.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FunasrClientConfig {

    private final FunasrProperties funasrProperties;

    public FunasrClientConfig(FunasrProperties funasrProperties) {
        this.funasrProperties = funasrProperties;
    }

    @Bean
    public RestTemplate funasrRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(funasrProperties.getReadTimeoutMs()))
                .build();
    }

    @Bean
    public HttpClient funasrHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                .build();
    }

    @Bean
    public OkHttpClient funasrOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(funasrProperties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(funasrProperties.getReadTimeoutMs()))
                .build();
    }
}
