package com.wc.service;

import com.wc.meeting.model.MeetingCorrectionRequest;
import com.wc.vo.UserMeetingNoteVO;
import com.wc.vo.UserMeetingRevisionVO;
import com.wc.vo.UserMeetingStatsVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserMeetingNoteService {

    Map<String, Object> createMeetingNote(
            Integer userId,
            String title,
            String sceneType,
            String selectedSpeakerIds,
            MultipartFile file,
            Integer batchSizeS,
            String hotword,
            Boolean autoDiarization
    ) throws Exception;

    Map<String, Object> createMeetingNoteFromHistory(
            Integer userId,
            Integer historyId,
            String title,
            String sceneType,
            String selectedSpeakerIds,
            Integer batchSizeS,
            String hotword,
            Boolean autoDiarization
    ) throws Exception;

    List<UserMeetingNoteVO> listHistoryViewByUserId(Integer userId);

    List<UserMeetingNoteVO> listHistoryViewByUserId(Integer userId, String keyword, String sceneType);

    List<UserMeetingNoteVO> listHistoryViewByUserId(
            Integer userId,
            String keyword,
            String sceneType,
            String status,
            Boolean hasTodos,
            String dateFrom,
            String dateTo
    );

    UserMeetingStatsVO getMeetingStats(Integer userId);

    UserMeetingNoteVO getHistoryViewById(Integer meetingId, Integer userId);

    List<UserMeetingRevisionVO> listRevisions(Integer meetingId, Integer userId);

    UserMeetingNoteVO applyCorrection(Integer meetingId, Integer userId, MeetingCorrectionRequest request);

    void downloadRawAudio(Integer meetingId, Integer userId, HttpServletResponse response) throws Exception;

    void downloadSegmentAudio(Integer meetingId, Integer segmentId, Integer userId, HttpServletResponse response) throws Exception;

    void exportMeetingNote(Integer meetingId, Integer userId, String format, String template, HttpServletResponse response) throws Exception;
}
