package com.wc.tts.controller;

import com.wc.result.result.R;
import com.wc.service.UserTtsHistoryService;
import com.wc.tts.service.TtsService;
import com.wc.utils.ThreadLocalUtil;
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
@RequestMapping("/api/tts")
public class TtsController {

    private final TtsService ttsService;
    private final UserTtsHistoryService userTtsHistoryService;

    public TtsController(TtsService ttsService, UserTtsHistoryService userTtsHistoryService) {
        this.ttsService = ttsService;
        this.userTtsHistoryService = userTtsHistoryService;
    }

    @GetMapping("/health")
    public R health() {
        return R.OK(ttsService.health());
    }

    @PostMapping("/synthesize")
    public R synthesize(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("text") String text,
            @RequestParam(value = "emotion", required = false) String emotion,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "format", required = false) String format
    ) {
        try {
            return R.OK(userTtsHistoryService.synthesizeAndStore(
                    currentUserId(),
                    audio,
                    text,
                    emotion,
                    language,
                    format
            ));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history")
    public R history() {
        try {
            return R.OK(userTtsHistoryService.listHistoryViewByUserId(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}")
    public R historyDetail(@PathVariable("historyId") Integer historyId) {
        try {
            return R.OK(userTtsHistoryService.getHistoryViewById(historyId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}/source-audio")
    public void downloadSourceAudio(@PathVariable("historyId") Integer historyId, HttpServletResponse response) throws Exception {
        try {
            userTtsHistoryService.downloadSourceAudio(historyId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/history/{historyId}/result-audio")
    public void downloadResultAudio(@PathVariable("historyId") Integer historyId, HttpServletResponse response) throws Exception {
        try {
            userTtsHistoryService.downloadResultAudio(historyId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/history/{historyId}")
    public R deleteHistory(@PathVariable("historyId") Integer historyId) {
        try {
            userTtsHistoryService.deleteHistory(historyId, currentUserId());
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
