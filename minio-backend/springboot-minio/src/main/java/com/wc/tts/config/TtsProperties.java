package com.wc.tts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {

    private String httpBaseUrl = "http://127.0.0.1:8003";

    private String synthesizePath = "/synthesize";

    private String healthPath = "/health";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 300000;

    public String getHttpBaseUrl() {
        return httpBaseUrl;
    }

    public void setHttpBaseUrl(String httpBaseUrl) {
        this.httpBaseUrl = httpBaseUrl;
    }

    public String getSynthesizePath() {
        return synthesizePath;
    }

    public void setSynthesizePath(String synthesizePath) {
        this.synthesizePath = synthesizePath;
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
