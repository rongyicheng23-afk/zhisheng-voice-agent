package com.wc.service;

import com.wc.vo.UserTtsHistoryVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserTtsHistoryService {

    Map<String, Object> synthesizeAndStore(
            Integer userId,
            MultipartFile audio,
            String text,
            String emotion,
            String language,
            String format
    ) throws Exception;

    List<UserTtsHistoryVO> listHistoryViewByUserId(Integer userId);

    UserTtsHistoryVO getHistoryViewById(Integer historyId, Integer userId);

    void deleteHistory(Integer historyId, Integer userId) throws Exception;

    void downloadSourceAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception;

    void downloadResultAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception;
}
