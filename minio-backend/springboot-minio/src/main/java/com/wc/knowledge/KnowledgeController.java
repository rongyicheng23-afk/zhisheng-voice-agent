package com.wc.knowledge;

import com.wc.result.result.R;
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

/** User-facing knowledge-base endpoints. The LoginInterceptor protects this path. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping("/documents")
    public R upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "validFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @RequestParam(value = "validUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil
    ) {
        try {
            return R.OK(service.upload(AuthContextUtil.currentUserId(), file, title, source, version, validFrom, validUntil));
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(500, "资料上传失败", null);
        }
    }

    @PostMapping("/documents/{documentId}/publish")
    public R publish(@PathVariable Long documentId) {
        try {
            return R.OK(service.publish(AuthContextUtil.currentUserId(), documentId));
        } catch (IllegalArgumentException error) {
            return new R(400, error.getMessage(), null);
        } catch (Exception error) {
            return new R(503, "RAG 索引服务不可用，请确认服务和向量模型已启动", null);
        }
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
