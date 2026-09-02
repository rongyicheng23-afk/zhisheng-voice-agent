package com.wc.service;

import com.wc.vo.UserVoiceprintHistoryVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserVoiceprintHistoryService {

    Map<String, Object> compareAndStore(Integer userId, MultipartFile file1, MultipartFile file2) throws Exception;

    List<UserVoiceprintHistoryVO> listHistoryViewByUserId(Integer userId);

    UserVoiceprintHistoryVO getHistoryViewById(Integer historyId, Integer userId);

    void deleteHistory(Integer historyId, Integer userId) throws Exception;

    void downloadLeftAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception;

    void downloadRightAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception;
}
