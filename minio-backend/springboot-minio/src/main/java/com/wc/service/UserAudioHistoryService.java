package com.wc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wc.entity.UserAudioHistory;
import com.wc.vo.UserAudioHistoryVO;
import com.wc.vo.UserMinioAudioObjectVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserAudioHistoryService extends IService<UserAudioHistory> {

    Map<String, Object> transcribeAndStore(Integer userId, MultipartFile file, Integer batchSizeS, String hotword) throws Exception;

    Map<String, Object> transcribeRealtimeAndStore(
            Integer userId,
            MultipartFile file,
            String wavName,
            String mode,
            String chunkSize,
            int chunkInterval,
            int encoderChunkLookBack,
            int decoderChunkLookBack,
            String hotwords
    ) throws Exception;

    List<UserAudioHistory> listByUserId(Integer userId);

    List<UserAudioHistoryVO> listHistoryViewByUserId(Integer userId);

    UserAudioHistory getHistoryById(Integer historyId);

    UserAudioHistory getHistoryById(Integer historyId, Integer userId);

    UserAudioHistoryVO getHistoryViewById(Integer historyId, Integer userId);

    void deleteHistory(Integer historyId, Integer userId) throws Exception;

    void downloadAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception;

    List<UserMinioAudioObjectVO> listMinioObjectsByUserId(Integer userId) throws Exception;

    void downloadMinioObject(Integer userId, String object, HttpServletResponse response) throws Exception;

    UserAudioHistory createPendingStreamHistory(Integer userId, String funasrMode);

    void finishStreamHistory(
            Integer historyId,
            Integer userId,
            String originalFilename,
            String contentType,
            byte[] audioBytes,
            String funasrMode,
            String transcription,
            String rawResult,
            String errorMessage
    ) throws Exception;
}
