package com.wc.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.entity.KnowledgeChunk;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP adapter for the local FAISS/BGE service. It never makes authorization decisions. */
@Component
public class KnowledgeRagClient {
    private final KnowledgeRagProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KnowledgeRagClient(KnowledgeRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public void index(Collection<KnowledgeChunk> chunks) {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", chunk.getDocumentId());
            item.put("chunkId", chunk.getId());
            item.put("content", chunk.getContent());
            documents.add(item);
        }
        request("/rag/index/documents", Map.of("documents", documents));
    }

    public List<RagHit> search(String query, Collection<Long> allowedDocumentIds, int topK) {
        List<Long> ids = new ArrayList<>(allowedDocumentIds);
        if (ids.isEmpty()) return List.of();
        JsonNode root = request("/rag/search", Map.of(
                "query", query,
                "documentIds", ids,
                "topK", topK
        ));
        List<RagHit> hits = new ArrayList<>();
        for (JsonNode node : root.path("results")) {
            if (node.path("documentId").canConvertToLong() && node.path("chunkId").canConvertToLong()) {
                hits.add(new RagHit(
                        node.path("documentId").asLong(),
                        node.path("chunkId").asLong(),
                        node.path("score").asDouble()
                ));
            }
        }
        return hits;
    }

    private JsonNode request(String path, Object payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl().replaceAll("/+$", "") + path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("本地 RAG 服务不可用，HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用本地 RAG 服务被中断", error);
        } catch (Exception error) {
            throw new IllegalStateException("调用本地 RAG 服务失败", error);
        }
    }

    public record RagHit(long documentId, long chunkId, double score) {}
}
