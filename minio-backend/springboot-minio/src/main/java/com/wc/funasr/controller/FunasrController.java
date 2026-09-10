package com.wc.funasr.controller;

import com.wc.funasr.service.FunasrService;
import com.wc.result.result.R;
import com.wc.service.UserAudioHistoryService;
import com.wc.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/funasr")
public class FunasrController {

    private final FunasrService funasrService;
    private final UserAudioHistoryService userAudioHistoryService;

    public FunasrController(FunasrService funasrService, UserAudioHistoryService userAudioHistoryService) {
        this.funasrService = funasrService;
        this.userAudioHistoryService = userAudioHistoryService;
    }

    @GetMapping("/health")
    public R health() {
        return R.OK(funasrService.health());
    }

    @PostMapping("/asr")
    public R asr(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchSizeS", required = false) Integer batchSizeS,
            @RequestParam(value = "hotword", required = false) String hotword
    ) {
        try {
            return R.OK(userAudioHistoryService.transcribeAndStore(currentUserId(), file, batchSizeS, hotword));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @PostMapping("/realtime")
    public R realtime(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "wavName", required = false) String wavName,
            @RequestParam(value = "mode", defaultValue = "2pass") String mode,
            @RequestParam(value = "chunkSize", defaultValue = "5,10,5") String chunkSize,
            @RequestParam(value = "chunkInterval", defaultValue = "10") int chunkInterval,
            @RequestParam(value = "encoderChunkLookBack", defaultValue = "4") int encoderChunkLookBack,
            @RequestParam(value = "decoderChunkLookBack", defaultValue = "1") int decoderChunkLookBack,
            @RequestParam(value = "hotwords", required = false) String hotwords
    ) {
        try {
            return R.OK(userAudioHistoryService.transcribeRealtimeAndStore(
                    currentUserId(),
                    file,
                    wavName,
                    mode,
                    chunkSize,
                    chunkInterval,
                    encoderChunkLookBack,
                    decoderChunkLookBack,
                    hotwords
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
            return R.OK(userAudioHistoryService.listHistoryViewByUserId(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}")
    public R historyDetail(@PathVariable("historyId") Integer historyId) {
        try {
            return R.OK(userAudioHistoryService.getHistoryViewById(historyId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{historyId}/audio")
    public void downloadAudio(@PathVariable("historyId") Integer historyId, HttpServletResponse response) throws Exception {
        try {
            userAudioHistoryService.downloadAudio(historyId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/history/{historyId}")
    public R deleteHistory(@PathVariable("historyId") Integer historyId) {
        try {
            userAudioHistoryService.deleteHistory(historyId, currentUserId());
            return R.OK();
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/minio/files")
    public R minioFiles() {
        try {
            return R.OK(userAudioHistoryService.listMinioObjectsByUserId(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/minio/audio")
    public void downloadMinioAudio(@RequestParam("object") String object, HttpServletResponse response) throws Exception {
        try {
            userAudioHistoryService.downloadMinioObject(currentUserId(), object, response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    private Integer currentUserId() {
        return AuthContextUtil.currentUserId();
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
