package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.config.MinioInfo;
import com.wc.entity.UserInfo;
import com.wc.entity.UserVoiceprintHistory;
import com.wc.mapper.UserVoiceprintHistoryMapper;
import com.wc.service.UserInfoService;
import com.wc.service.UserVoiceprintHistoryService;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import com.wc.voiceprint.service.VoiceprintService;
import com.wc.vo.UserVoiceprintHistoryVO;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserVoiceprintHistoryServiceImpl extends ServiceImpl<UserVoiceprintHistoryMapper, UserVoiceprintHistory>
        implements UserVoiceprintHistoryService {

    @Resource
    private UserVoiceprintHistoryMapper userVoiceprintHistoryMapper;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinioInfo minioInfo;
    @Resource
    private VoiceprintService voiceprintService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> compareAndStore(Integer userId, MultipartFile file1, MultipartFile file2) throws Exception {
        UserInfo userInfo = requireUser(userId);
        validateAudio(file1, "请上传音频 A");
        validateAudio(file2, "请上传音频 B");

        UserVoiceprintHistory history = createPendingHistory(
                userInfo.getId(),
                safeFilename(file1.getOriginalFilename(), "audio_a.wav"),
                normalizeContentType(file1.getContentType()),
                file1.getSize(),
                safeFilename(file2.getOriginalFilename(), "audio_b.wav"),
                normalizeContentType(file2.getContentType()),
                file2.getSize()
        );

        try {
            uploadLeftToMinio(history, userId, history.getLeftFilename(), file1.getBytes(), history.getLeftContentType());
            uploadRightToMinio(history, userId, history.getRightFilename(), file2.getBytes(), history.getRightContentType());

            VoiceprintCompareResult compareResult = voiceprintService.compare(file1, file2);
            history.setScore(compareResult.getScore());
            history.setThresholdValue(compareResult.getThreshold());
            history.setSamePerson(compareResult.getSamePerson());
            history.setResultMessage(compareResult.getMessage());
            history.setStatus("SUCCESS");
            history.setRawResult(toJson(buildRawResult(compareResult, history)));
            history.setErrorMessage(null);
            history.setUpdateTime(new Date());
            userVoiceprintHistoryMapper.updateById(history);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("historyId", history.getId());
            result.put("userId", userId);
            result.put("leftFilename", history.getLeftFilename());
            result.put("rightFilename", history.getRightFilename());
            result.put("leftObject", history.getLeftObject());
            result.put("rightObject", history.getRightObject());
            result.put("score", history.getScore());
            result.put("threshold", history.getThresholdValue());
            result.put("samePerson", history.getSamePerson());
            result.put("message", history.getResultMessage());
            return result;
        } catch (Exception ex) {
            markFailed(history, ex);
            throw ex;
        }
    }

    @Override
    public List<UserVoiceprintHistoryVO> listHistoryViewByUserId(Integer userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserVoiceprintHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserVoiceprintHistory::getUid, userId)
                .orderByDesc(UserVoiceprintHistory::getCreateTime)
                .orderByDesc(UserVoiceprintHistory::getId);
        List<UserVoiceprintHistory> histories = userVoiceprintHistoryMapper.selectList(wrapper);
        List<UserVoiceprintHistoryVO> result = new ArrayList<>(histories.size());
        for (UserVoiceprintHistory history : histories) {
            result.add(toHistoryView(history));
        }
        return result;
    }

    @Override
    public UserVoiceprintHistoryVO getHistoryViewById(Integer historyId, Integer userId) {
        return toHistoryView(getHistoryById(historyId, userId));
    }

    @Override
    public void deleteHistory(Integer historyId, Integer userId) throws Exception {
        UserVoiceprintHistory history = getHistoryById(historyId, userId);
        removeObjectQuietly(history.getLeftBucket(), history.getLeftObject());
        removeObjectQuietly(history.getRightBucket(), history.getRightObject());
        userVoiceprintHistoryMapper.deleteById(history.getId());
    }

    @Override
    public void downloadLeftAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception {
        UserVoiceprintHistory history = getHistoryById(historyId, userId);
        if (!StringUtils.hasText(history.getLeftBucket()) || !StringUtils.hasText(history.getLeftObject())) {
            throw new IllegalArgumentException("当前记录还没有可下载的音频 A");
        }
        writeAudioResponseHeaders(response, history.getLeftFilename(), history.getLeftContentType());
        streamObject(history.getLeftBucket(), history.getLeftObject(), response);
    }

    @Override
    public void downloadRightAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception {
        UserVoiceprintHistory history = getHistoryById(historyId, userId);
        if (!StringUtils.hasText(history.getRightBucket()) || !StringUtils.hasText(history.getRightObject())) {
            throw new IllegalArgumentException("当前记录还没有可下载的音频 B");
        }
        writeAudioResponseHeaders(response, history.getRightFilename(), history.getRightContentType());
        streamObject(history.getRightBucket(), history.getRightObject(), response);
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

    private UserVoiceprintHistory createPendingHistory(
            Integer userId,
            String leftFilename,
            String leftContentType,
            long leftFileSize,
            String rightFilename,
            String rightContentType,
            long rightFileSize
    ) {
        UserVoiceprintHistory history = new UserVoiceprintHistory();
        history.setUid(userId);
        history.setLeftFilename(leftFilename);
        history.setLeftContentType(leftContentType);
        history.setLeftFileSize(leftFileSize);
        history.setRightFilename(rightFilename);
        history.setRightContentType(rightContentType);
        history.setRightFileSize(rightFileSize);
        history.setStatus("PENDING");
        history.setCreateTime(new Date());
        history.setUpdateTime(new Date());
        userVoiceprintHistoryMapper.insert(history);
        return history;
    }

    private void uploadLeftToMinio(UserVoiceprintHistory history, Integer userId, String filename, byte[] bytes, String contentType) throws Exception {
        String object = buildObjectPath(userId, "voiceprint/left", filename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );
        history.setLeftBucket(minioInfo.getBucket());
        history.setLeftObject(object);
        history.setLeftFileSize((long) bytes.length);
        history.setStatus("LEFT_UPLOADED");
        history.setUpdateTime(new Date());
        userVoiceprintHistoryMapper.updateById(history);
    }

    private void uploadRightToMinio(UserVoiceprintHistory history, Integer userId, String filename, byte[] bytes, String contentType) throws Exception {
        String object = buildObjectPath(userId, "voiceprint/right", filename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );
        history.setRightBucket(minioInfo.getBucket());
        history.setRightObject(object);
        history.setRightFileSize((long) bytes.length);
        history.setStatus("RIGHT_UPLOADED");
        history.setUpdateTime(new Date());
        userVoiceprintHistoryMapper.updateById(history);
    }

    private UserVoiceprintHistory getHistoryById(Integer historyId, Integer userId) {
        UserVoiceprintHistory history = userVoiceprintHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("声纹比对历史记录不存在");
        }
        requireUser(userId);
        if (!userId.equals(history.getUid())) {
            throw new IllegalArgumentException("无权查看其他用户的声纹历史");
        }
        return history;
    }

    private void markFailed(UserVoiceprintHistory history, Exception ex) {
        history.setStatus("FAILED");
        history.setErrorMessage(truncate(ex.getMessage(), 1000));
        history.setUpdateTime(new Date());
        userVoiceprintHistoryMapper.updateById(history);
    }

    private Map<String, Object> buildRawResult(VoiceprintCompareResult compareResult, UserVoiceprintHistory history) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("status", compareResult.getStatus());
        raw.put("score", compareResult.getScore());
        raw.put("threshold", compareResult.getThreshold());
        raw.put("samePerson", compareResult.getSamePerson());
        raw.put("message", compareResult.getMessage());
        raw.put("leftObject", history.getLeftObject());
        raw.put("rightObject", history.getRightObject());
        return raw;
    }

    private void writeAudioResponseHeaders(HttpServletResponse response, String filename, String contentType) throws Exception {
        response.setContentType(normalizeContentType(contentType));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(safeFilename(filename, "audio.wav"), StandardCharsets.UTF_8)
        );
    }

    private void streamObject(String bucket, String objectName, HttpServletResponse response) throws Exception {
        try (GetObjectResponse object = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        )) {
            object.transferTo(response.getOutputStream());
        }
    }

    private void removeObjectQuietly(String bucket, String objectName) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception ignored) {
            // Keep history deletion idempotent when MinIO objects were already removed.
        }
    }

    private UserVoiceprintHistoryVO toHistoryView(UserVoiceprintHistory history) {
        UserVoiceprintHistoryVO view = new UserVoiceprintHistoryVO();
        view.setId(history.getId());
        view.setUserId(history.getUid());
        view.setLeftBucket(history.getLeftBucket());
        view.setLeftObject(history.getLeftObject());
        view.setLeftFilename(history.getLeftFilename());
        view.setLeftContentType(history.getLeftContentType());
        view.setLeftFileSize(history.getLeftFileSize());
        view.setRightBucket(history.getRightBucket());
        view.setRightObject(history.getRightObject());
        view.setRightFilename(history.getRightFilename());
        view.setRightContentType(history.getRightContentType());
        view.setRightFileSize(history.getRightFileSize());
        view.setScore(history.getScore());
        view.setThresholdValue(history.getThresholdValue());
        view.setSamePerson(history.getSamePerson());
        view.setResultMessage(history.getResultMessage());
        view.setStatus(history.getStatus());
        view.setErrorMessage(history.getErrorMessage());
        view.setCreateTime(history.getCreateTime());
        view.setUpdateTime(history.getUpdateTime());

        boolean hasLeftAudio = StringUtils.hasText(history.getLeftBucket()) && StringUtils.hasText(history.getLeftObject());
        boolean hasRightAudio = StringUtils.hasText(history.getRightBucket()) && StringUtils.hasText(history.getRightObject());
        view.setHasLeftAudio(hasLeftAudio);
        view.setHasRightAudio(hasRightAudio);
        view.setLeftAudioUrl(hasLeftAudio ? "/api/voiceprint/history/" + history.getId() + "/audio-a" : null);
        view.setRightAudioUrl(hasRightAudio ? "/api/voiceprint/history/" + history.getId() + "/audio-b" : null);
        return view;
    }

    private String buildObjectPath(Integer userId, String type, String filename) {
        return "user-audio/" + userId
                + "/"
                + type
                + "/"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "_"
                + sanitizeFilename(filename);
    }

    private String sanitizeFilename(String filename) {
        return safeFilename(filename, "audio.wav").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String safeFilename(String filename, String defaultName) {
        return StringUtils.hasText(filename) ? filename : defaultName;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "";
        }
    }
}
