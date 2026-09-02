package com.wc.voiceprint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "voiceprint")
public class VoiceprintProperties {

    private String httpBaseUrl = "http://127.0.0.1:8004";

    private String comparePath = "/verify";

    private String healthPath = "/";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 300000;

    public String getHttpBaseUrl() {
        return httpBaseUrl;
    }

    public void setHttpBaseUrl(String httpBaseUrl) {
        this.httpBaseUrl = httpBaseUrl;
    }

    public String getComparePath() {
        return comparePath;
    }

    public void setComparePath(String comparePath) {
        this.comparePath = comparePath;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
