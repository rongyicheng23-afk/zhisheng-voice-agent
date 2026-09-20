package com.wc.knowledge;

import com.wc.result.result.R;
import com.wc.access.AccessControlService;
import com.wc.access.AccessDeniedException;
import com.wc.utils.AuthContextUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** User-facing knowledge-base endpoints. The LoginInterceptor protects this path. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;
    private final AccessControlService access;

    public KnowledgeController(KnowledgeService service, AccessControlService access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/documents")
    public R upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "validFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @RequestParam(value = "validUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility,
            @RequestParam(value = "teamMemberId", required = false) List<Integer> teamMemberIds
    ) {
        try {
            Integer userId = AuthContextUtil.currentUserId();
            access.requireKnowledgeManager(userId);
            return R.OK(service.upload(userId, file, title, source, version, validFrom, validUntil, visibility, teamMemberIds));
        } catch (AccessDeniedException error) {
            return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(500, "资料上传失败", null);
        }
    }

    @PostMapping("/documents/{documentId}/publish")
    public R publish(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.publish(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(503, "RAG 索引服务不可用，请确认服务和向量模型已启动", null);
        }
    }

    /** Submit a draft for review. A document never enters retrieval before approval. */
    @PostMapping("/documents/{documentId}/submit-review")
    public R submitReview(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.submitReview(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        }
    }

    /** The owner is the local demo's material administrator. */
    @PostMapping("/documents/{documentId}/approve")
    public R approve(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.approve(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(503, "RAG 索引服务不可用，请确认服务和向量模型已启动", null);
        }
    }

    @PostMapping("/documents/{documentId}/offline")
    public R offline(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.offline(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        }
    }

    @PostMapping("/documents/{documentId}/restore-draft")
    public R restoreDraft(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.restoreToDraft(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        }
    }

    @PostMapping("/documents/{documentId}/reindex")
    public R reindex(@PathVariable Long documentId) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.reindex(userId, documentId));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(503, "RAG 索引服务不可用，请确认服务和向量模型已启动", null);
        }
    }

    @PostMapping("/documents/{documentId}/visibility")
    public R updateVisibility(
            @PathVariable Long documentId,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "teamMemberId", required = false) List<Integer> teamMemberIds
    ) {
        try {
            Integer userId = AuthContextUtil.currentUserId(); access.requireKnowledgeManager(userId);
            return R.OK(service.updateVisibility(userId, documentId, visibility, teamMemberIds));
        } catch (AccessDeniedException error) { return new R(403, error.getMessage(), null);
        } catch (IllegalArgumentException error) { return new R(400, error.getMessage(), null); }
    }

    @GetMapping("/documents")
    public R list() {
        return R.OK(service.list(AuthContextUtil.currentUserId()));
    }

    @PostMapping("/search")
    public R search(@RequestParam("query") String query) {
        try {
            return R.OK(service.retrieve(AuthContextUtil.currentUserId(), query));
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(503, "RAG 检索服务不可用，请确认服务和向量模型已启动", null);
        }
    }
}
