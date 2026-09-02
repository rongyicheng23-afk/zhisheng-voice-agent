package com.wc.funasr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "funasr")
public class FunasrProperties {

    private String httpBaseUrl = "http://127.0.0.1:8002";

    private String asrPath = "/asr";

    private String healthPath = "/";

    private String wsUrl = "ws://127.0.0.1:10095";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 60000;

    private int realtimeFrameDurationMs = 60;

    private int realtimeIdleTimeoutMs = 1500;

    private int realtimeOverallTimeoutMs = 10000;

    public String getHttpBaseUrl() {
        return httpBaseUrl;
    }

    public void setHttpBaseUrl(String httpBaseUrl) {
        this.httpBaseUrl = httpBaseUrl;
    }

    public String getAsrPath() {
        return asrPath;
    }

    public void setAsrPath(String asrPath) {
        this.asrPath = asrPath;
    }

    public String getHealthPath() {
        return healthPath;
    }

    public void setHealthPath(String healthPath) {
        this.healthPath = healthPath;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
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

    public int getRealtimeFrameDurationMs() {
        return realtimeFrameDurationMs;
    }

    public void setRealtimeFrameDurationMs(int realtimeFrameDurationMs) {
        this.realtimeFrameDurationMs = realtimeFrameDurationMs;
    }

    public int getRealtimeIdleTimeoutMs() {
        return realtimeIdleTimeoutMs;
    }

    public void setRealtimeIdleTimeoutMs(int realtimeIdleTimeoutMs) {
        this.realtimeIdleTimeoutMs = realtimeIdleTimeoutMs;
    }

    public int getRealtimeOverallTimeoutMs() {
        return realtimeOverallTimeoutMs;
    }

    public void setRealtimeOverallTimeoutMs(int realtimeOverallTimeoutMs) {
        this.realtimeOverallTimeoutMs = realtimeOverallTimeoutMs;
    }
}
