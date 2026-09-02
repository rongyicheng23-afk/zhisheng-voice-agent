package com.wc.service;

import com.wc.vo.UserSpeakerProfileVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserSpeakerProfileService {

    UserSpeakerProfileVO registerProfile(Integer userId, String speakerName, String speakerRole, MultipartFile sampleAudio) throws Exception;

    List<UserSpeakerProfileVO> listProfilesByUserId(Integer userId);

    void deleteProfile(Integer profileId, Integer userId);

    void downloadSampleAudio(Integer profileId, Integer userId, HttpServletResponse response) throws Exception;
}
