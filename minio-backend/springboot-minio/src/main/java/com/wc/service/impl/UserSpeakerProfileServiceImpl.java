package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wc.config.MinioInfo;
import com.wc.entity.UserInfo;
import com.wc.entity.UserSpeakerProfile;
import com.wc.mapper.UserSpeakerProfileMapper;
import com.wc.service.UserInfoService;
import com.wc.service.UserSpeakerProfileService;
import com.wc.vo.UserSpeakerProfileVO;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class UserSpeakerProfileServiceImpl extends ServiceImpl<UserSpeakerProfileMapper, UserSpeakerProfile>
        implements UserSpeakerProfileService {

    @Resource
    private UserSpeakerProfileMapper userSpeakerProfileMapper;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinioInfo minioInfo;

    @Override
    public UserSpeakerProfileVO registerProfile(Integer userId, String speakerName, String speakerRole, MultipartFile sampleAudio) throws Exception {
        UserInfo userInfo = requireUser(userId);
        String normalizedName = normalizeRequiredText(speakerName, "发言人名称不能为空", 64);
        String normalizedRole = normalizeOptionalText(speakerRole, 64);
        validateAudio(sampleAudio, "请上传发言人样本音频");
        ensureSpeakerNameAvailable(userInfo.getId(), normalizedName);

        UserSpeakerProfile profile = new UserSpeakerProfile();
        profile.setUid(userInfo.getId());
        profile.setSpeakerName(normalizedName);
        profile.setSpeakerRole(normalizedRole);
        profile.setSampleFilename(safeFilename(sampleAudio.getOriginalFilename(), normalizedName + ".wav"));
        profile.setSampleContentType(normalizeContentType(sampleAudio.getContentType()));
        profile.setSampleFileSize(sampleAudio.getSize());
        profile.setStatus("ACTIVE");
        profile.setCreateTime(new Date());
        profile.setUpdateTime(new Date());
        userSpeakerProfileMapper.insert(profile);

        try {
            byte[] bytes = sampleAudio.getBytes();
            String object = buildObjectPath(userId, profile.getSampleFilename());
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioInfo.getBucket())
                            .object(object)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(normalizeContentType(profile.getSampleContentType()))
                            .build()
            );
            profile.setSampleBucket(minioInfo.getBucket());
            profile.setSampleObject(object);
            profile.setSampleFileSize((long) bytes.length);
            profile.setUpdateTime(new Date());
            userSpeakerProfileMapper.updateById(profile);
            return toProfileView(profile);
        } catch (Exception ex) {
            userSpeakerProfileMapper.deleteById(profile.getId());
            throw ex;
        }
    }

    @Override
    public List<UserSpeakerProfileVO> listProfilesByUserId(Integer userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserSpeakerProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSpeakerProfile::getUid, userId)
                .ne(UserSpeakerProfile::getStatus, "DELETED")
                .orderByDesc(UserSpeakerProfile::getCreateTime)
                .orderByDesc(UserSpeakerProfile::getId);
        List<UserSpeakerProfile> profiles = userSpeakerProfileMapper.selectList(wrapper);
        List<UserSpeakerProfileVO> result = new ArrayList<>(profiles.size());
        for (UserSpeakerProfile profile : profiles) {
            result.add(toProfileView(profile));
        }
        return result;
    }

    @Override
    public void deleteProfile(Integer profileId, Integer userId) {
        UserSpeakerProfile profile = getProfileById(profileId, userId);
        profile.setStatus("DELETED");
        profile.setUpdateTime(new Date());
        userSpeakerProfileMapper.updateById(profile);
    }

    @Override
    public void downloadSampleAudio(Integer profileId, Integer userId, HttpServletResponse response) throws Exception {
        UserSpeakerProfile profile = getProfileById(profileId, userId);
        if (!StringUtils.hasText(profile.getSampleBucket()) || !StringUtils.hasText(profile.getSampleObject())) {
            throw new IllegalArgumentException("当前发言人档案没有可下载的样本音频");
        }
        response.setContentType(normalizeContentType(profile.getSampleContentType()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(safeFilename(profile.getSampleFilename(), "speaker_sample.wav"), StandardCharsets.UTF_8)
        );
        try (GetObjectResponse object = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(profile.getSampleBucket())
                        .object(profile.getSampleObject())
                        .build()
        )) {
            object.transferTo(response.getOutputStream());
        }
    }

    private UserInfo requireUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        UserInfo userInfo = userInfoService.getUserById(userId);
        if (userInfo == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return userInfo;
    }

    private void validateAudio(MultipartFile audio, String message) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureSpeakerNameAvailable(Integer userId, String speakerName) {
        LambdaQueryWrapper<UserSpeakerProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSpeakerProfile::getUid, userId)
                .eq(UserSpeakerProfile::getSpeakerName, speakerName)
                .ne(UserSpeakerProfile::getStatus, "DELETED")
                .last("LIMIT 1");
        UserSpeakerProfile existing = userSpeakerProfileMapper.selectOne(wrapper);
        if (existing != null) {
            throw new IllegalArgumentException("当前账号下已存在同名发言人，请更换名称");
        }
    }

    private UserSpeakerProfile getProfileById(Integer profileId, Integer userId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId 不能为空");
        }
        UserSpeakerProfile profile = userSpeakerProfileMapper.selectById(profileId);
        if (profile == null || "DELETED".equals(profile.getStatus())) {
            throw new IllegalArgumentException("发言人档案不存在");
        }
        requireUser(userId);
        if (!userId.equals(profile.getUid())) {
            throw new IllegalArgumentException("无权操作其他用户的发言人档案");
        }
        return profile;
    }

    private UserSpeakerProfileVO toProfileView(UserSpeakerProfile profile) {
        UserSpeakerProfileVO view = new UserSpeakerProfileVO();
        view.setId(profile.getId());
        view.setUserId(profile.getUid());
        view.setSpeakerName(profile.getSpeakerName());
        view.setSpeakerRole(profile.getSpeakerRole());
        view.setSampleBucket(profile.getSampleBucket());
        view.setSampleObject(profile.getSampleObject());
        view.setSampleFilename(profile.getSampleFilename());
        view.setSampleContentType(profile.getSampleContentType());
        view.setSampleFileSize(profile.getSampleFileSize());
        view.setStatus(profile.getStatus());
        view.setCreateTime(profile.getCreateTime());
        view.setUpdateTime(profile.getUpdateTime());
        boolean hasSampleAudio = StringUtils.hasText(profile.getSampleBucket()) && StringUtils.hasText(profile.getSampleObject());
        view.setHasSampleAudio(hasSampleAudio);
        view.setSampleAudioUrl(hasSampleAudio ? "/api/speaker/" + profile.getId() + "/audio" : null);
        return view;
    }

    private String buildObjectPath(Integer userId, String filename) {
        return "user-audio/" + userId
                + "/speaker/sample/"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "_"
                + sanitizeFilename(filename);
    }

    private String sanitizeFilename(String filename) {
        return safeFilename(filename, "speaker_sample.wav").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String safeFilename(String filename, String defaultName) {
        return StringUtils.hasText(filename) ? filename : defaultName;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private String normalizeRequiredText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
