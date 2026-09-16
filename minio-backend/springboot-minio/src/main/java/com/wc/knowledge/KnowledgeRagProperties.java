package com.wc.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knowledge.rag")
public class KnowledgeRagProperties {
    private String baseUrl = "http://127.0.0.1:18082";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { this.connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { this.readTimeoutMs = value; }
}
