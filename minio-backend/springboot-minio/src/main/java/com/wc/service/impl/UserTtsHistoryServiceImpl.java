package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.config.MinioInfo;
import com.wc.entity.UserInfo;
import com.wc.entity.UserTtsHistory;
import com.wc.mapper.UserTtsHistoryMapper;
import com.wc.service.UserInfoService;
import com.wc.service.UserTtsHistoryService;
import com.wc.tts.model.TtsSynthesisResult;
import com.wc.tts.service.TtsService;
import com.wc.vo.UserTtsHistoryVO;
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
public class UserTtsHistoryServiceImpl extends ServiceImpl<UserTtsHistoryMapper, UserTtsHistory>
        implements UserTtsHistoryService {

    @Resource
    private UserTtsHistoryMapper userTtsHistoryMapper;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinioInfo minioInfo;
    @Resource
    private TtsService ttsService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> synthesizeAndStore(
            Integer userId,
            MultipartFile audio,
            String text,
            String emotion,
            String language,
            String format
    ) throws Exception {
        UserInfo userInfo = requireUser(userId);
        validateText(text);
        validateAudio(audio);

        String sourceFilename = safeFilename(audio.getOriginalFilename(), "reference.wav");
        UserTtsHistory history = createPendingHistory(
                userInfo.getId(),
                text.trim(),
                StringUtils.hasText(emotion) ? emotion.trim() : "neutral",
                StringUtils.hasText(language) ? language.trim() : "zh-cn",
                StringUtils.hasText(format) ? format.trim() : "wav",
                sourceFilename,
                normalizeContentType(audio.getContentType()),
                audio.getSize()
        );

        try {
            byte[] sourceBytes = audio.getBytes();
            uploadSourceToMinio(history, userId, sourceFilename, sourceBytes, history.getSourceContentType());

            TtsSynthesisResult synthesisResult = ttsService.synthesize(
                    audio,
                    history.getInputText(),
                    history.getEmotion(),
                    history.getLanguage(),
                    history.getRequestedFormat()
            );

            String resultContentType = normalizeAudioContentType(synthesisResult.getContentType());
            String resultFilename = buildResultFilename(history.getId(), sourceFilename, resultContentType);
            uploadResultToMinio(history, userId, resultFilename, synthesisResult.getAudioBytes(), resultContentType);

            history.setStatus("SUCCESS");
            history.setRawResult(toJson(buildRawResult(history, synthesisResult)));
            history.setErrorMessage(null);
            history.setUpdateTime(new Date());
            userTtsHistoryMapper.updateById(history);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("historyId", history.getId());
            result.put("userId", userId);
            result.put("text", history.getInputText());
            result.put("emotion", history.getEmotion());
            result.put("language", history.getLanguage());
            result.put("requestedFormat", history.getRequestedFormat());
            result.put("sourceFilename", history.getSourceFilename());
            result.put("sourceObject", history.getSourceObject());
            result.put("resultFilename", history.getResultFilename());
            result.put("resultObject", history.getResultObject());
            result.put("resultContentType", history.getResultContentType());
            result.put("resultFileSize", history.getResultFileSize());
            return result;
        } catch (Exception ex) {
            markFailed(history, ex);
            throw ex;
        }
    }

    @Override
    public List<UserTtsHistoryVO> listHistoryViewByUserId(Integer userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserTtsHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTtsHistory::getUid, userId)
                .orderByDesc(UserTtsHistory::getCreateTime)
                .orderByDesc(UserTtsHistory::getId);
        List<UserTtsHistory> histories = userTtsHistoryMapper.selectList(wrapper);
        List<UserTtsHistoryVO> result = new ArrayList<>(histories.size());
        for (UserTtsHistory history : histories) {
            result.add(toHistoryView(history));
        }
        return result;
    }

    @Override
    public UserTtsHistoryVO getHistoryViewById(Integer historyId, Integer userId) {
        return toHistoryView(getHistoryById(historyId, userId));
    }

    @Override
    public void deleteHistory(Integer historyId, Integer userId) throws Exception {
        UserTtsHistory history = getHistoryById(historyId, userId);
        removeObjectQuietly(history.getSourceBucket(), history.getSourceObject());
        removeObjectQuietly(history.getResultBucket(), history.getResultObject());
        userTtsHistoryMapper.deleteById(history.getId());
    }

    @Override
    public void downloadSourceAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception {
        UserTtsHistory history = getHistoryById(historyId, userId);
        if (!StringUtils.hasText(history.getSourceBucket()) || !StringUtils.hasText(history.getSourceObject())) {
            throw new IllegalArgumentException("当前记录还没有可下载的参考音频");
        }
        writeAudioResponseHeaders(response, history.getSourceFilename(), history.getSourceContentType());
        streamObject(history.getSourceBucket(), history.getSourceObject(), response);
    }

    @Override
    public void downloadResultAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception {
        UserTtsHistory history = getHistoryById(historyId, userId);
        if (!StringUtils.hasText(history.getResultBucket()) || !StringUtils.hasText(history.getResultObject())) {
            throw new IllegalArgumentException("当前记录还没有可下载的生成音频");
        }
        writeAudioResponseHeaders(response, history.getResultFilename(), history.getResultContentType());
        streamObject(history.getResultBucket(), history.getResultObject(), response);
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

    private void validateText(String text) {
        com.wc.tts.model.TtsRequestLimits.validateText(text);
    }

    private void validateAudio(MultipartFile audio) {
        com.wc.tts.model.TtsRequestLimits.validateAudio(audio);
    }

    private UserTtsHistory createPendingHistory(
            Integer userId,
            String text,
            String emotion,
            String language,
            String format,
            String sourceFilename,
            String sourceContentType,
            long sourceFileSize
    ) {
        UserTtsHistory history = new UserTtsHistory();
        history.setUid(userId);
        history.setInputText(text);
        history.setEmotion(emotion);
        history.setLanguage(language);
        history.setRequestedFormat(format);
        history.setSourceFilename(sourceFilename);
        history.setSourceContentType(sourceContentType);
        history.setSourceFileSize(sourceFileSize);
        history.setStatus("PENDING");
        history.setCreateTime(new Date());
        history.setUpdateTime(new Date());
        userTtsHistoryMapper.insert(history);
        return history;
    }

    private void uploadSourceToMinio(
            UserTtsHistory history,
            Integer userId,
            String filename,
            byte[] bytes,
            String contentType
    ) throws Exception {
        String object = buildObjectPath(userId, "tts/source", filename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );

        history.setSourceBucket(minioInfo.getBucket());
        history.setSourceObject(object);
        history.setSourceFileSize((long) bytes.length);
        history.setStatus("SOURCE_UPLOADED");
        history.setUpdateTime(new Date());
        userTtsHistoryMapper.updateById(history);
    }

    private void uploadResultToMinio(
            UserTtsHistory history,
            Integer userId,
            String filename,
            byte[] bytes,
            String contentType
    ) throws Exception {
        String object = buildObjectPath(userId, "tts/result", filename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );

        history.setResultBucket(minioInfo.getBucket());
        history.setResultObject(object);
        history.setResultFilename(filename);
        history.setResultContentType(normalizeContentType(contentType));
        history.setResultFileSize((long) bytes.length);
        history.setUpdateTime(new Date());
        userTtsHistoryMapper.updateById(history);
    }

    private UserTtsHistory getHistoryById(Integer historyId, Integer userId) {
        UserTtsHistory history = userTtsHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("文字转语音历史记录不存在");
        }
        requireUser(userId);
        if (!userId.equals(history.getUid())) {
            throw new IllegalArgumentException("无权查看其他用户的文字转语音历史");
        }
        return history;
    }

    private void markFailed(UserTtsHistory history, Exception ex) {
        history.setStatus("FAILED");
        history.setErrorMessage(truncate(ex.getMessage(), 1000));
        history.setUpdateTime(new Date());
        userTtsHistoryMapper.updateById(history);
    }

    private Map<String, Object> buildRawResult(UserTtsHistory history, TtsSynthesisResult synthesisResult) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("status", history.getStatus());
        raw.put("requestedFormat", history.getRequestedFormat());
        raw.put("upstreamFilename", synthesisResult.getUpstreamFilename());
        raw.put("resultContentType", history.getResultContentType());
        raw.put("resultFileSize", history.getResultFileSize());
        raw.put("sourceObject", history.getSourceObject());
        raw.put("resultObject", history.getResultObject());
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

    private UserTtsHistoryVO toHistoryView(UserTtsHistory history) {
        UserTtsHistoryVO view = new UserTtsHistoryVO();
        view.setId(history.getId());
        view.setUserId(history.getUid());
        view.setInputText(history.getInputText());
        view.setEmotion(history.getEmotion());
        view.setLanguage(history.getLanguage());
        view.setRequestedFormat(history.getRequestedFormat());
        view.setSourceBucket(history.getSourceBucket());
        view.setSourceObject(history.getSourceObject());
        view.setSourceFilename(history.getSourceFilename());
        view.setSourceContentType(history.getSourceContentType());
        view.setSourceFileSize(history.getSourceFileSize());
        view.setResultBucket(history.getResultBucket());
        view.setResultObject(history.getResultObject());
        view.setResultFilename(history.getResultFilename());
        view.setResultContentType(history.getResultContentType());
        view.setResultFileSize(history.getResultFileSize());
        view.setStatus(history.getStatus());
        view.setErrorMessage(history.getErrorMessage());
        view.setCreateTime(history.getCreateTime());
        view.setUpdateTime(history.getUpdateTime());

        boolean hasSourceAudio = StringUtils.hasText(history.getSourceBucket()) && StringUtils.hasText(history.getSourceObject());
        boolean hasResultAudio = StringUtils.hasText(history.getResultBucket()) && StringUtils.hasText(history.getResultObject());
        view.setHasSourceAudio(hasSourceAudio);
        view.setHasResultAudio(hasResultAudio);
        view.setSourceAudioUrl(hasSourceAudio ? "/api/tts/history/" + history.getId() + "/source-audio" : null);
        view.setResultAudioUrl(hasResultAudio ? "/api/tts/history/" + history.getId() + "/result-audio" : null);
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

    private String buildResultFilename(Integer historyId, String sourceFilename, String contentType) {
        String extension = resolveAudioExtension(contentType);
        return "tts_result_" + historyId + "_" + sanitizeBaseName(sourceFilename) + "." + extension;
    }

    private String sanitizeBaseName(String filename) {
        String safe = safeFilename(filename, "reference.wav");
        int dot = safe.lastIndexOf('.');
        String baseName = dot > 0 ? safe.substring(0, dot) : safe;
        return baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
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

    private String normalizeAudioContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "audio/wav";
        }
        String normalized = contentType.trim().toLowerCase();
        if (normalized.contains("wav")) {
            return "audio/wav";
        }
        if (normalized.contains("mpeg") || normalized.contains("mp3")) {
            return "audio/mpeg";
        }
        return contentType;
    }

    private String resolveAudioExtension(String contentType) {
        String normalized = normalizeAudioContentType(contentType).toLowerCase();
        if (normalized.contains("mpeg") || normalized.contains("mp3")) {
            return "mp3";
        }
        return "wav";
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
