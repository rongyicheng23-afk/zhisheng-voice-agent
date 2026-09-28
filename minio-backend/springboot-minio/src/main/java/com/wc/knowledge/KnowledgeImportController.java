package com.wc.knowledge;

import com.wc.service.UserInfoService;
import com.wc.utils.AuthContextUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeImportController {
    private final KnowledgeImportService imports;
    private final UserInfoService users;
    public KnowledgeImportController(KnowledgeImportService imports, UserInfoService users) {
        this.imports = imports; this.users = users;
    }
    @PostMapping(value="/import-preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeImportService.Preview> preview(@RequestParam("file") MultipartFile file) {
        int user = AuthContextUtil.currentUserId();
        if (user <= 0 || users.getUserById(user) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.extract(file));
    }
    @ExceptionHandler(KnowledgeImportService.ImportFailure.class)
    public ResponseEntity<Map<String, String>> invalid(KnowledgeImportService.ImportFailure error) {
        return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(Map.of("message", error.getMessage()));
    }
}
