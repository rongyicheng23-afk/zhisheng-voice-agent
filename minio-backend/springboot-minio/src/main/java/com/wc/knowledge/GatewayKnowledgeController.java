package com.wc.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Internal gateway bridge. User permissions are enforced before any RAG data is returned. */
@RestController
@RequestMapping("/api/knowledge/internal")
public class GatewayKnowledgeController {
    private final KnowledgeService service;
    private final byte[] gatewayKey;

    public GatewayKnowledgeController(
            KnowledgeService service,
            @Value("${realtime.gateway.internal-key:}") String gatewayKey
    ) {
        this.service = service;
        this.gatewayKey = gatewayKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/retrieve")
    public ResponseEntity<Map<String, Object>> retrieve(
            @RequestHeader(value = "X-Realtime-Gateway-Key", defaultValue = "") String suppliedKey,
            @RequestBody RetrieveRequest request
    ) {
        if (gatewayKey.length == 0 || !MessageDigest.isEqual(gatewayKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid gateway key");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid user id");
        }
        return ResponseEntity.ok(service.retrieve(request.userId(), request.query()));
    }

    public record RetrieveRequest(Integer userId, String query) {}
}
