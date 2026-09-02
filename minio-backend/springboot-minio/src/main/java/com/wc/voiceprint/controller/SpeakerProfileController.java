package com.wc.voiceprint.controller;

import com.wc.result.result.R;
import com.wc.service.UserSpeakerProfileService;
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
@RequestMapping("/api/speaker")
public class SpeakerProfileController {

    private final UserSpeakerProfileService userSpeakerProfileService;

    public SpeakerProfileController(UserSpeakerProfileService userSpeakerProfileService) {
        this.userSpeakerProfileService = userSpeakerProfileService;
    }

    @PostMapping("/register")
    public R register(
            @RequestParam("speakerName") String speakerName,
            @RequestParam(value = "speakerRole", required = false) String speakerRole,
            @RequestParam("sampleAudio") MultipartFile sampleAudio
    ) {
        try {
            return R.OK(userSpeakerProfileService.registerProfile(currentUserId(), speakerName, speakerRole, sampleAudio));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/list")
    public R list() {
        try {
            return R.OK(userSpeakerProfileService.listProfilesByUserId(currentUserId()));
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @DeleteMapping("/{profileId}")
    public R delete(@PathVariable("profileId") Integer profileId) {
        try {
            userSpeakerProfileService.deleteProfile(profileId, currentUserId());
            return R.OK();
        } catch (IllegalArgumentException ex) {
            return new R(400, ex.getMessage(), null);
        } catch (Exception ex) {
            return new R(500, ex.getMessage(), null);
        }
    }

    @GetMapping("/{profileId}/audio")
    public void downloadSampleAudio(@PathVariable("profileId") Integer profileId, HttpServletResponse response) throws Exception {
        try {
            userSpeakerProfileService.downloadSampleAudio(profileId, currentUserId(), response);
        } catch (IllegalArgumentException ex) {
            sendDownloadError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
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
