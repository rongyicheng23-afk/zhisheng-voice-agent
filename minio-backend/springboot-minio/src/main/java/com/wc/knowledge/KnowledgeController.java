package com.wc.knowledge;

import com.wc.utils.AuthContextUtil;
import com.wc.service.UserInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
public class KnowledgeController {
    private final KnowledgeService knowledge;
    private final UserInfoService users;
    private final byte[] internalKey;
    public KnowledgeController(KnowledgeService knowledge, UserInfoService users,
                               @Value("${realtime.gateway.internal-key:}") String key) {
        this.knowledge = knowledge; this.users = users; this.internalKey = key.getBytes(StandardCharsets.UTF_8);
    }
    public record StatusRequest(String status, int revision) {}
    public record Query(String question) {}
    public record InternalQuery(int userId, String question) {}
    public record CompareRequest(String beforeId, String afterId) {}
    public record ReviewedPublishRequest(String reviewToken) {}
    private int user() {
        int id = AuthContextUtil.currentUserId();
        if (id <= 0 || users.getUserById(id) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }
    private <T> ResponseEntity<T> result(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
    @GetMapping("/api/knowledge/documents")
    public ResponseEntity<List<KnowledgeService.Document>> list() { return result(knowledge.list(user())); }
    @PostMapping("/api/knowledge/compare")
    public ResponseEntity<KnowledgeComparison.Result> compare(@RequestBody CompareRequest request) {
        return result(knowledge.compare(user(), request.beforeId(), request.afterId()));
    }
    @GetMapping("/api/knowledge/documents/{id}/publication-preview")
    public ResponseEntity<KnowledgeService.PublicationReview> review(@PathVariable String id) {
        return result(knowledge.review(user(), id));
    }
    @PostMapping("/api/knowledge/documents/{id}/publish-reviewed")
    public ResponseEntity<KnowledgeService.Document> publishReviewed(@PathVariable String id, @RequestBody ReviewedPublishRequest request) {
        return result(knowledge.publishReviewed(user(), id, request.reviewToken()));
    }
    @PostMapping("/api/knowledge/documents")
    public ResponseEntity<KnowledgeService.Document> create(@RequestBody KnowledgeService.Draft request) {
        return result(knowledge.create(user(), request));
    }
    @PostMapping("/api/knowledge/documents/{id}/status")
    public ResponseEntity<KnowledgeService.Document> transition(@PathVariable String id, @RequestBody StatusRequest request) {
        return result(knowledge.transition(user(), id, request.status(), request.revision()));
    }
    @PostMapping("/api/knowledge/search")
    public ResponseEntity<KnowledgeService.SearchResult> search(@RequestBody Query query) {
        return result(knowledge.search(user(), query.question()));
    }
    @PostMapping("/api/realtime/internal/knowledge/search")
    public ResponseEntity<KnowledgeService.SearchResult> internal(
            @RequestHeader(value="X-Realtime-Gateway-Key", defaultValue="") String key,
            @RequestBody InternalQuery query) {
        if (internalKey.length == 0 || !MessageDigest.isEqual(internalKey, key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (query.userId() <= 0 || users.getUserById(query.userId()) == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return result(knowledge.search(query.userId(), query.question()));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("message", "资料库暂不可用，请检查数据库连接及 knowledge.sql 初始化"));
    }
}
