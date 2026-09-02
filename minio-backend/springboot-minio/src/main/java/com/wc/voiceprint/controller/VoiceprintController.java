package com.wc.voiceprint.controller;

import com.wc.result.result.R;
import com.wc.service.UserVoiceprintHistoryService;
import com.wc.utils.ThreadLocalUtil;
import com.wc.voiceprint.service.VoiceprintService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/voiceprint")
public class VoiceprintController {

    private final VoiceprintService voiceprintService;
    private final UserVoiceprintHistoryService userVoiceprintHistoryService;

    public VoiceprintController(VoiceprintService voiceprintService, UserVoiceprintHistoryService userVoiceprintHistoryService) {
        this.voiceprintService = voiceprintService;
        this.userVoiceprintHistoryService = userVoiceprintHistoryService;
    }

    @GetMapping("/health")
    public R health() {
        return R.OK(voiceprintService.health());
    }

    @PostMapping("/compare")
    public R compare(
            @RequestParam("file1") MultipartFile file1,
            @RequestParam("file2") MultipartFile file2
    ) {
        try {
            return R.OK(userVoiceprintHistoryService.compareAndStore(currentUserId(), file1, file2));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history")
    public R history() {
        try {
            return R.OK(userVoiceprintHistoryService.listHistoryViewByUserId(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}")
    public R historyDetail(@PathVariable("historyId") Integer historyId) {
        try {
            return R.OK(userVoiceprintHistoryService.getHistoryViewById(historyId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}/audio-a")
    public void downloadLeftAudio(@PathVariable("historyId") Integer historyId, HttpServletResponse response) throws Exception {
        try {
            userVoiceprintHistoryService.downloadLeftAudio(historyId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/history/{historyId}/audio-b")
    public void downloadRightAudio(@PathVariable("historyId") Integer historyId, HttpServletResponse response) throws Exception {
        try {
            userVoiceprintHistoryService.downloadRightAudio(historyId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/history/{historyId}")
    public R deleteHistory(@PathVariable("historyId") Integer historyId) {
        try {
            userVoiceprintHistoryService.deleteHistory(historyId, currentUserId());
            return R.OK();
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    private Integer currentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        Object idValue = claims.get("id");
        if (idValue instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(idValue));
    }

    private void sendDownloadError(HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(status);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(message);
        }
    }
}
