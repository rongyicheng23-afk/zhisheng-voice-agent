package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.config.MinioInfo;
import com.wc.entity.UserAudioHistory;
import com.wc.entity.UserInfo;
import com.wc.funasr.service.FunasrService;
import com.wc.mapper.UserAudioHistoryMapper;
import com.wc.service.UserAudioHistoryService;
import com.wc.service.UserInfoService;
import com.wc.vo.UserAudioHistoryVO;
import com.wc.vo.UserMinioAudioObjectVO;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAudioHistoryServiceImpl extends ServiceImpl<UserAudioHistoryMapper, UserAudioHistory>
        implements UserAudioHistoryService {

    @Resource
    private UserAudioHistoryMapper userAudioHistoryMapper;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinioInfo minioInfo;
    @Resource
    private FunasrService funasrService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> transcribeAndStore(Integer userId, MultipartFile file, Integer batchSizeS, String hotword) throws Exception {
        UserInfo userInfo = requireUser(userId);

        UserAudioHistory history = createPendingHistory(
                userInfo.getId(),
                safeFilename(file.getOriginalFilename()),
                normalizeContentType(file.getContentType()),
                file.getSize(),
                "asr",
                "asr"
        );

        try {
            byte[] bytes = file.getBytes();
            uploadToMinio(history, userId, history.getOriginalFilename(), bytes, history.getContentType(), "asr");

            JsonNode response = funasrService.transcribeAudio(file, batchSizeS, hotword);
            history.setStatus("SUCCESS");
            history.setTranscription(extractAsrText(response));
            history.setRawResult(toJson(response));
            history.setErrorMessage(null);
            history.setUpdateTime(new Date());
            userAudioHistoryMapper.updateById(history);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("historyId", history.getId());
            result.put("userId", userId);
            result.put("object", history.getObject());
            result.put("transcription", history.getTranscription());
            result.put("funasrResponse", response);
            return result;
        } catch (Exception ex) {
            markFailed(history, ex);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> transcribeRealtimeAndStore(
            Integer userId,
            MultipartFile file,
            String wavName,
            String mode,
            String chunkSize,
            int chunkInterval,
            int encoderChunkLookBack,
            int decoderChunkLookBack,
            String hotwords
    ) throws Exception {
        UserInfo userInfo = requireUser(userId);
        String filename = StringUtils.hasText(wavName) ? ensurePcmSuffix(wavName) : safeFilename(file.getOriginalFilename());

        UserAudioHistory history = createPendingHistory(
                userInfo.getId(),
                filename,
                normalizeContentType(file.getContentType()),
                file.getSize(),
                "realtime",
                StringUtils.hasText(mode) ? mode : "2pass"
        );

        try {
            byte[] bytes = file.getBytes();
            uploadToMinio(history, userId, history.getOriginalFilename(), bytes, history.getContentType(), "realtime");

            Map<String, Object> response = funasrService.transcribeRealtimePcm(
                    file,
                    wavName,
                    mode,
                    chunkSize,
                    chunkInterval,
                    encoderChunkLookBack,
                    decoderChunkLookBack,
                    hotwords
            );
            history.setStatus("SUCCESS");
            history.setTranscription(extractRealtimeText(response));
            history.setRawResult(toJson(response));
            history.setErrorMessage(null);
            history.setUpdateTime(new Date());
            userAudioHistoryMapper.updateById(history);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("historyId", history.getId());
            result.put("userId", userId);
            result.put("object", history.getObject());
            result.put("transcription", history.getTranscription());
            result.put("funasrResponse", response);
            return result;
        } catch (Exception ex) {
            markFailed(history, ex);
            throw ex;
        }
    }

    @Override
    public List<UserAudioHistory> listByUserId(Integer userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserAudioHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAudioHistory::getUid, userId)
                .orderByDesc(UserAudioHistory::getCreateTime)
                .orderByDesc(UserAudioHistory::getId);
        return userAudioHistoryMapper.selectList(wrapper);
    }

    @Override
    public List<UserAudioHistoryVO> listHistoryViewByUserId(Integer userId) {
        List<UserAudioHistory> histories = listByUserId(userId);
        List<UserAudioHistoryVO> result = new ArrayList<>(histories.size());
        for (UserAudioHistory history : histories) {
            result.add(toHistoryView(history));
        }
        return result;
    }

    @Override
    public UserAudioHistory getHistoryById(Integer historyId) {
        UserAudioHistory history = userAudioHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new IllegalArgumentException("音频历史记录不存在");
        }
        return history;
    }

    @Override
    public UserAudioHistory getHistoryById(Integer historyId, Integer userId) {
        UserAudioHistory history = getHistoryById(historyId);
        requireHistoryOwner(history, userId);
        return history;
    }

    @Override
    public UserAudioHistoryVO getHistoryViewById(Integer historyId, Integer userId) {
        return toHistoryView(getHistoryById(historyId, userId));
    }

    @Override
    public void deleteHistory(Integer historyId, Integer userId) throws Exception {
        UserAudioHistory history = getHistoryById(historyId, userId);
        removeObjectQuietly(history.getBucket(), history.getObject());
        userAudioHistoryMapper.deleteById(history.getId());
    }

    @Override
    public void downloadAudio(Integer historyId, Integer userId, HttpServletResponse response) throws Exception {
        UserAudioHistory history = getHistoryById(historyId, userId);
        if (!StringUtils.hasText(history.getBucket()) || !StringUtils.hasText(history.getObject())) {
            throw new IllegalArgumentException("当前记录还没有可下载的音频文件");
        }

        response.setContentType(normalizeContentType(history.getContentType()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(history.getOriginalFilename(), StandardCharsets.UTF_8)
        );

        streamObject(history.getBucket(), history.getObject(), response);
    }

    @Override
    public List<UserMinioAudioObjectVO> listMinioObjectsByUserId(Integer userId) throws Exception {
        requireUser(userId);
        List<UserMinioAudioObjectVO> result = new ArrayList<>();
        String prefix = buildUserPrefix(userId);

        for (io.minio.Result<Item> itemResult : minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build())) {
            Item item = itemResult.get();
            if (item.isDir()) {
                continue;
            }

            UserMinioAudioObjectVO view = new UserMinioAudioObjectVO();
            view.setBucket(minioInfo.getBucket());
            view.setObject(item.objectName());
            view.setFilename(extractFilename(item.objectName()));
            view.setSize(item.size());
            view.setLastModified(toDate(item.lastModified()));
            view.setRequestMode(extractRequestMode(item.objectName(), userId));
            view.setContentType(detectContentType(item.objectName()));
            view.setAudioUrl("/api/funasr/minio/audio?object=" + urlEncode(item.objectName()));
            result.add(view);
        }

        result.sort((left, right) -> {
            Date leftTime = left.getLastModified();
            Date rightTime = right.getLastModified();
            if (leftTime == null && rightTime == null) {
                return right.getObject().compareTo(left.getObject());
            }
            if (leftTime == null) {
                return 1;
            }
            if (rightTime == null) {
                return -1;
            }
            return rightTime.compareTo(leftTime);
        });
        return result;
    }

    @Override
    public void downloadMinioObject(Integer userId, String object, HttpServletResponse response) throws Exception {
        requireUser(userId);
        if (!StringUtils.hasText(object)) {
            throw new IllegalArgumentException("object 不能为空");
        }
        String normalizedObject = object.trim();
        ensureObjectBelongsToUser(userId, normalizedObject);

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(normalizedObject)
                        .build()
        );

        String filename = extractFilename(normalizedObject);
        response.setContentType(normalizeContentType(stat.contentType()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8)
        );

        streamObject(minioInfo.getBucket(), normalizedObject, response);
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
            // History deletion should not fail just because the object is already gone.
        }
    }

    @Override
    public UserAudioHistory createPendingStreamHistory(Integer userId, String funasrMode) {
        requireUser(userId);
        UserAudioHistory history = new UserAudioHistory();
        history.setUid(userId);
        history.setOriginalFilename("stream_" + System.currentTimeMillis() + ".pcm");
        history.setContentType("application/octet-stream");
        history.setFileSize(0L);
        history.setRequestMode("realtime-ws");
        history.setFunasrMode(StringUtils.hasText(funasrMode) ? funasrMode : "2pass");
        history.setStatus("PENDING");
        history.setCreateTime(new Date());
        history.setUpdateTime(new Date());
        userAudioHistoryMapper.insert(history);
        return history;
    }

    @Override
    public void finishStreamHistory(
            Integer historyId,
            Integer userId,
            String originalFilename,
            String contentType,
            byte[] audioBytes,
            String funasrMode,
            String transcription,
            String rawResult,
            String errorMessage
    ) throws Exception {
        UserAudioHistory history = getHistoryById(historyId);
        requireUser(userId);

        history.setUid(userId);
        history.setOriginalFilename(ensurePcmSuffix(StringUtils.hasText(originalFilename) ? originalFilename : history.getOriginalFilename()));
        history.setContentType(normalizeContentType(contentType));
        history.setFileSize((long) audioBytes.length);
        history.setRequestMode("realtime-ws");
        history.setFunasrMode(StringUtils.hasText(funasrMode) ? funasrMode : history.getFunasrMode());

        if (audioBytes.length > 0) {
            uploadToMinio(history, userId, history.getOriginalFilename(), audioBytes, history.getContentType(), "realtime-ws");
        }

        history.setTranscription(transcription);
        history.setRawResult(rawResult);
        history.setErrorMessage(truncate(errorMessage, 1000));
        history.setStatus(StringUtils.hasText(errorMessage) ? "FAILED" : "SUCCESS");
        history.setUpdateTime(new Date());
        userAudioHistoryMapper.updateById(history);
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

    private void requireHistoryOwner(UserAudioHistory history, Integer userId) {
        requireUser(userId);
        if (!userId.equals(history.getUid())) {
            throw new IllegalArgumentException("无权查看其他用户的音频历史");
        }
    }

    private UserAudioHistory createPendingHistory(
            Integer userId,
            String originalFilename,
            String contentType,
            long fileSize,
            String requestMode,
            String funasrMode
    ) {
        UserAudioHistory history = new UserAudioHistory();
        history.setUid(userId);
        history.setOriginalFilename(originalFilename);
        history.setContentType(contentType);
        history.setFileSize(fileSize);
        history.setRequestMode(requestMode);
        history.setFunasrMode(funasrMode);
        history.setStatus("PENDING");
        history.setCreateTime(new Date());
        history.setUpdateTime(new Date());
        userAudioHistoryMapper.insert(history);
        return history;
    }

    private void uploadToMinio(
            UserAudioHistory history,
            Integer userId,
            String originalFilename,
            byte[] bytes,
            String contentType,
            String requestMode
    ) throws Exception {
        String object = buildObjectPath(userId, requestMode, originalFilename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );
        history.setBucket(minioInfo.getBucket());
        history.setObject(object);
        history.setFileSize((long) bytes.length);
        history.setStatus("UPLOADED");
        history.setUpdateTime(new Date());
        userAudioHistoryMapper.updateById(history);
    }

    private void markFailed(UserAudioHistory history, Exception ex) {
        history.setStatus("FAILED");
        history.setErrorMessage(truncate(ex.getMessage(), 1000));
        history.setUpdateTime(new Date());
        userAudioHistoryMapper.updateById(history);
    }

    private String buildObjectPath(Integer userId, String requestMode, String filename) {
        return buildUserPrefix(userId)
                + "/"
                + requestMode
                + "/"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "_"
                + sanitizeFilename(filename);
    }

    private String safeFilename(String filename) {
        return StringUtils.hasText(filename) ? filename : "audio.wav";
    }

    private String buildUserPrefix(Integer userId) {
        return "user-audio/" + userId;
    }

    private String sanitizeFilename(String filename) {
        return safeFilename(filename).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private String ensurePcmSuffix(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "stream.pcm";
        }
        if (filename.toLowerCase().endsWith(".pcm")) {
            return filename;
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) + ".pcm" : filename + ".pcm";
    }

    private String extractAsrText(JsonNode response) {
        JsonNode transcriptionNode = response.path("transcription");
        if (transcriptionNode.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode item : transcriptionNode) {
                String text = item.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    texts.add(text);
                }
            }
            return String.join("\n", texts);
        }
        String text = response.path("text").asText("");
        return StringUtils.hasText(text) ? text : "";
    }

    private String extractRealtimeText(Map<String, Object> response) {
        JsonNode messagesNode = objectMapper.valueToTree(response.get("messages"));
        if (!messagesNode.isArray()) {
            return "";
        }

        LinkedHashSet<String> allTexts = new LinkedHashSet<>();
        LinkedHashSet<String> offlineTexts = new LinkedHashSet<>();

        for (JsonNode message : messagesNode) {
            String text = message.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                continue;
            }
            allTexts.add(text);
            String mode = message.path("mode").asText("");
            if (mode.contains("offline")) {
                offlineTexts.add(text);
            }
        }

        if (!offlineTexts.isEmpty()) {
            return String.join("\n", offlineTexts);
        }
        return String.join("\n", allTexts);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private UserAudioHistoryVO toHistoryView(UserAudioHistory history) {
        UserAudioHistoryVO view = new UserAudioHistoryVO();
        view.setId(history.getId());
        view.setUserId(history.getUid());
        view.setBucket(history.getBucket());
        view.setObject(history.getObject());
        view.setOriginalFilename(history.getOriginalFilename());
        view.setContentType(history.getContentType());
        view.setFileSize(history.getFileSize());
        view.setRequestMode(history.getRequestMode());
        view.setFunasrMode(history.getFunasrMode());
        view.setStatus(history.getStatus());
        view.setTranscription(history.getTranscription());
        view.setErrorMessage(history.getErrorMessage());
        view.setCreateTime(history.getCreateTime());
        view.setUpdateTime(history.getUpdateTime());
        boolean hasAudio = StringUtils.hasText(history.getBucket()) && StringUtils.hasText(history.getObject());
        view.setHasAudio(hasAudio);
        view.setAudioUrl(hasAudio ? "/api/funasr/history/" + history.getId() + "/audio" : null);
        return view;
    }

    private void ensureObjectBelongsToUser(Integer userId, String object) {
        String prefix = buildUserPrefix(userId) + "/";
        if (!object.startsWith(prefix)) {
            throw new IllegalArgumentException("无权访问其他用户的音频文件");
        }
    }

    private String extractFilename(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return "audio.wav";
        }
        int index = objectName.lastIndexOf('/');
        return index >= 0 ? objectName.substring(index + 1) : objectName;
    }

    private String extractRequestMode(String objectName, Integer userId) {
        String prefix = buildUserPrefix(userId) + "/";
        if (!StringUtils.hasText(objectName) || !objectName.startsWith(prefix)) {
            return "";
        }
        String remain = objectName.substring(prefix.length());
        int slash = remain.indexOf('/');
        return slash > 0 ? remain.substring(0, slash) : remain;
    }

    private Date toDate(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return Date.from(zonedDateTime.toInstant());
    }

    private String detectContentType(String objectName) {
        String lowerObjectName = StringUtils.hasText(objectName) ? objectName.toLowerCase() : "";
        if (lowerObjectName.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerObjectName.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerObjectName.endsWith(".pcm")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
