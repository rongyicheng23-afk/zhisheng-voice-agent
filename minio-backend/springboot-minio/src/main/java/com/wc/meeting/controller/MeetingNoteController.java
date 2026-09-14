package com.wc.meeting.controller;

import com.wc.meeting.model.MeetingCorrectionRequest;
import com.wc.result.result.R;
import com.wc.service.UserMeetingNoteService;
import com.wc.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/meeting")
public class MeetingNoteController {

    private final UserMeetingNoteService userMeetingNoteService;

    public MeetingNoteController(UserMeetingNoteService userMeetingNoteService) {
        this.userMeetingNoteService = userMeetingNoteService;
    }

    @PostMapping("/upload")
    public R upload(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sceneType", defaultValue = "meeting") String sceneType,
            @RequestParam(value = "selectedSpeakerIds", required = false) String selectedSpeakerIds,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchSizeS", required = false) Integer batchSizeS,
            @RequestParam(value = "hotword", required = false) String hotword,
            @RequestParam(value = "autoDiarization", defaultValue = "true") Boolean autoDiarization
    ) {
        try {
            return R.OK(userMeetingNoteService.createMeetingNote(
                    currentUserId(),
                    title,
                    sceneType,
                    selectedSpeakerIds,
                    file,
                    batchSizeS,
                    hotword,
                    autoDiarization
            ));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @PostMapping("/from-history/{historyId}")
    public R createFromHistory(
            @PathVariable("historyId") Integer historyId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sceneType", defaultValue = "meeting") String sceneType,
            @RequestParam(value = "selectedSpeakerIds", required = false) String selectedSpeakerIds,
            @RequestParam(value = "batchSizeS", required = false) Integer batchSizeS,
            @RequestParam(value = "hotword", required = false) String hotword,
            @RequestParam(value = "autoDiarization", defaultValue = "true") Boolean autoDiarization
    ) {
        try {
            return R.OK(userMeetingNoteService.createMeetingNoteFromHistory(
                    currentUserId(),
                    historyId,
                    title,
                    sceneType,
                    selectedSpeakerIds,
                    batchSizeS,
                    hotword,
                    autoDiarization
            ));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history")
    public R history(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sceneType", required = false) String sceneType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hasTodos", required = false) Boolean hasTodos,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo
    ) {
        try {
            return R.OK(userMeetingNoteService.listHistoryViewByUserId(
                    currentUserId(),
                    keyword,
                    sceneType,
                    status,
                    hasTodos,
                    dateFrom,
                    dateTo
            ));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/stats")
    public R stats() {
        try {
            return R.OK(userMeetingNoteService.getMeetingStats(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{meetingId}")
    public R historyDetail(@PathVariable("meetingId") Integer meetingId) {
        try {
            return R.OK(userMeetingNoteService.getHistoryViewById(meetingId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{meetingId}/revisions")
    public R revisions(@PathVariable("meetingId") Integer meetingId) {
        try {
            return R.OK(userMeetingNoteService.listRevisions(meetingId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @PutMapping("/history/{meetingId}/correction")
    public R applyCorrection(
            @PathVariable("meetingId") Integer meetingId,
            @RequestBody MeetingCorrectionRequest request
    ) {
        try {
            return R.OK(userMeetingNoteService.applyCorrection(meetingId, currentUserId(), request));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{meetingId}/diarization-report")
    public R diarizationReport(@PathVariable("meetingId") Integer meetingId) {
        try {
            return R.OK(userMeetingNoteService.getDiarizationReport(meetingId, currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/history/{meetingId}/audio")
    public void downloadRawAudio(@PathVariable("meetingId") Integer meetingId, HttpServletResponse response) throws Exception {
        try {
            userMeetingNoteService.downloadRawAudio(meetingId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/history/{meetingId}/segments/{segmentId}/audio")
    public void downloadSegmentAudio(
            @PathVariable("meetingId") Integer meetingId,
            @PathVariable("segmentId") Integer segmentId,
            HttpServletResponse response
    ) throws Exception {
        try {
            userMeetingNoteService.downloadSegmentAudio(meetingId, segmentId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/history/{meetingId}/export")
    public void exportMeetingNote(
            @PathVariable("meetingId") Integer meetingId,
            @RequestParam(value = "format", defaultValue = "txt") String format,
            @RequestParam(value = "template", required = false) String template,
            HttpServletResponse response
    ) throws Exception {
        try {
            userMeetingNoteService.exportMeetingNote(meetingId, currentUserId(), format, template, response);
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
