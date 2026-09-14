package com.wc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.config.MinioInfo;
import com.wc.entity.UserAudioHistory;
import com.wc.entity.UserInfo;
import com.wc.entity.UserMeetingNote;
import com.wc.entity.UserMeetingRevision;
import com.wc.entity.UserMeetingSegment;
import com.wc.entity.UserSpeakerProfile;
import com.wc.funasr.service.FunasrService;
import com.wc.mapper.UserMeetingNoteMapper;
import com.wc.mapper.UserMeetingRevisionMapper;
import com.wc.mapper.UserMeetingSegmentMapper;
import com.wc.mapper.UserSpeakerProfileMapper;
import com.wc.meeting.model.MeetingCorrectionRequest;
import com.wc.meeting.model.MeetingExportTemplate;
import com.wc.meeting.model.MeetingSegmentCorrectionItem;
import com.wc.meeting.diarization.SpeakerDiarizationAdapter;
import com.wc.service.UserAudioHistoryService;
import com.wc.service.UserInfoService;
import com.wc.service.UserMeetingNoteService;
import com.wc.utils.InMemoryMultipartFile;
import com.wc.vo.UserMeetingInsightSectionVO;
import com.wc.vo.UserMeetingDecisionInsightVO;
import com.wc.vo.UserMeetingNoteVO;
import com.wc.vo.UserMeetingRoleInsightVO;
import com.wc.vo.UserMeetingRevisionVO;
import com.wc.vo.UserMeetingSegmentVO;
import com.wc.vo.UserMeetingSpeakerBlockVO;
import com.wc.vo.UserMeetingStatsVO;
import com.wc.vo.UserMeetingTodoChainVO;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import com.wc.voiceprint.service.VoiceprintService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserMeetingNoteServiceImpl extends ServiceImpl<UserMeetingNoteMapper, UserMeetingNote>
        implements UserMeetingNoteService {

    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("[。！？!?；;\\n]+");
    private static final Pattern NON_CHINESE_PATTERN = Pattern.compile("[^\\u4e00-\\u9fa5a-zA-Z0-9]");
    private static final Pattern SILENCE_START_PATTERN = Pattern.compile("silence_start:\\s*([0-9.]+)");
    private static final Pattern SILENCE_END_PATTERN = Pattern.compile("silence_end:\\s*([0-9.]+)");
    private static final Pattern OWNER_ACTION_PATTERN = Pattern.compile("(由|请|安排|交给)([^，。；;、\\s]{1,10})(负责|完成|提交|跟进|准备|整理|确认)");
    private static final Pattern DEADLINE_PATTERN = Pattern.compile("(今天|明天|后天|本周[一二三四五六日天]?|下周[一二三四五六日天]?|\\d{1,2}月\\d{1,2}日|\\d{1,2}号|\\d{1,2}点|截止[^，。；;、]{0,10}|[^，。；;、]{0,10}(之前|前))");
    private static final List<String> TODO_MARKERS = List.of("需要", "负责", "完成", "提交", "安排", "确认", "准备", "截止", "之前", "尽快", "下周", "本周");
    private static final List<String> KEYWORD_STOP_WORDS = List.of("我们", "你们", "他们", "这个", "那个", "然后", "可以", "已经", "因为", "所以", "进行", "一个", "今天", "老师", "同学", "大家", "一下");
    private static final List<String> MEETING_DECISION_MARKERS = List.of("决定", "确定", "达成", "明确", "统一", "安排", "计划", "最终", "负责", "提交");
    private static final List<String> PENDING_DECISION_MARKERS = List.of("待确认", "再讨论", "暂定", "还没定", "后续确认", "再看", "之后再定", "待定", "后续再议");
    private static final List<String> CLASSROOM_INTERACTION_MARKERS = List.of("问题", "提问", "请问", "为什么", "怎么", "老师", "同学", "回答", "举例");
    private static final List<String> CLASSROOM_REVIEW_MARKERS = List.of("作业", "练习", "复习", "预习", "提交", "课后", "下次", "重点");
    private static final List<String> ROLE_QUESTION_MARKERS = List.of("?", "？", "请问", "是不是", "能不能", "怎么", "为什么", "什么时候", "是否");
    private static final List<String> ROLE_RESPONSE_MARKERS = List.of("可以", "是的", "需要", "这个是", "我的建议", "建议", "后面会", "统一按", "回答");
    private static final List<String> ROLE_HOST_MARKERS = List.of("今天我们", "接下来", "本次", "重点是", "需要大家", "安排", "统一", "先", "然后");
    private static final double MIN_SEGMENT_SECONDS = 1.2d;
    private static final double MAX_SEGMENT_SECONDS = 12.0d;
    private static final long MERGE_MAX_GAP_MS = 1500L;
    private static final BigDecimal ANONYMOUS_SPEAKER_THRESHOLD = new BigDecimal("0.72");

    @Resource
    private UserMeetingNoteMapper userMeetingNoteMapper;
    @Resource
    private UserMeetingSegmentMapper userMeetingSegmentMapper;
    @Resource
    private UserMeetingRevisionMapper userMeetingRevisionMapper;
    @Resource
    private UserSpeakerProfileMapper userSpeakerProfileMapper;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinioInfo minioInfo;
    @Resource
    private FunasrService funasrService;
    @Resource
    private VoiceprintService voiceprintService;
    @Resource
    private SpeakerDiarizationAdapter speakerDiarizationAdapter;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private UserAudioHistoryService userAudioHistoryService;
    @Resource(name = "meetingTaskExecutor")
    private Executor meetingTaskExecutor;

    private static final class MeetingProcessingMeta {
        private final String stage;
        private final String label;
        private final String description;
        private final Integer percent;

        private MeetingProcessingMeta(String stage, String label, String description, Integer percent) {
            this.stage = stage;
            this.label = label;
            this.description = description;
            this.percent = percent;
        }
    }

    private static final class MeetingAnalysisBundle {
        private final List<UserMeetingRoleInsightVO> roleInsights;
        private final List<UserMeetingTodoChainVO> todoChains;
        private final List<UserMeetingDecisionInsightVO> decisionInsights;

        private MeetingAnalysisBundle(
                List<UserMeetingRoleInsightVO> roleInsights,
                List<UserMeetingTodoChainVO> todoChains,
                List<UserMeetingDecisionInsightVO> decisionInsights
        ) {
            this.roleInsights = roleInsights;
            this.todoChains = todoChains;
            this.decisionInsights = decisionInsights;
        }
    }

    private static final class SpeakerRoleStats {
        private final String speakerName;
        private int segmentCount;
        private int transcriptLength;
        private int questionCount;
        private int responseCount;
        private int hostCount;
        private String hostEvidence;
        private String questionEvidence;
        private String responseEvidence;

        private SpeakerRoleStats(String speakerName) {
            this.speakerName = speakerName;
        }
    }

    @Override
    public Map<String, Object> createMeetingNote(
            Integer userId,
            String title,
            String sceneType,
            String selectedSpeakerIds,
            MultipartFile file,
            Integer batchSizeS,
            String hotword,
            Boolean autoDiarization
    ) throws Exception {
        UserInfo userInfo = requireUser(userId);
        validateAudio(file);

        String normalizedSceneType = normalizeSceneType(sceneType);
        String normalizedTitle = normalizeTitle(title, normalizedSceneType);
        String normalizedSpeakerIds = normalizeSelectedSpeakerIds(selectedSpeakerIds);

        UserMeetingNote note = createPendingNote(
                userInfo.getId(),
                normalizedTitle,
                normalizedSceneType,
                normalizedSpeakerIds,
                safeFilename(file.getOriginalFilename()),
                normalizeContentType(file.getContentType()),
                file.getSize()
        );

        clearSegments(note.getId());

        try {
            byte[] bytes = file.getBytes();
            uploadRawAudio(note, userId, note.getRawFilename(), bytes, note.getRawContentType());
            markProcessing(note);
            enqueueMeetingProcessing(
                    note.getId(),
                    userId,
                    normalizedSpeakerIds,
                    batchSizeS,
                    hotword,
                    Boolean.TRUE.equals(autoDiarization),
                    null,
                    null
            );
            return buildQueuedResult(note, userId);
        } catch (Exception ex) {
            markFailed(note, ex);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> createMeetingNoteFromHistory(
            Integer userId,
            Integer historyId,
            String title,
            String sceneType,
            String selectedSpeakerIds,
            Integer batchSizeS,
            String hotword,
            Boolean autoDiarization
    ) throws Exception {
        UserInfo userInfo = requireUser(userId);
        UserAudioHistory history = userAudioHistoryService.getHistoryById(historyId, userId);
        ensureHistoryAudioAvailable(history);

        String normalizedSceneType = normalizeSceneType(sceneType);
        String normalizedTitle = normalizeTitle(title, normalizedSceneType);
        String normalizedSpeakerIds = normalizeSelectedSpeakerIds(selectedSpeakerIds);

        UserMeetingNote note = createPendingNote(
                userInfo.getId(),
                normalizedTitle,
                normalizedSceneType,
                normalizedSpeakerIds,
                safeFilename(history.getOriginalFilename()),
                normalizeContentType(history.getContentType()),
                history.getFileSize() == null ? 0L : history.getFileSize()
        );

        clearSegments(note.getId());

        try {
            bindRawAudioFromHistory(note, history);
            String transcript = StringUtils.hasText(history.getTranscription()) ? history.getTranscription().trim() : null;
            String rawResult = StringUtils.hasText(history.getRawResult()) ? history.getRawResult() : null;
            markProcessing(note);
            enqueueMeetingProcessing(
                    note.getId(),
                    userId,
                    normalizedSpeakerIds,
                    batchSizeS,
                    hotword,
                    Boolean.TRUE.equals(autoDiarization),
                    transcript,
                    rawResult
            );
            return buildQueuedResult(note, userId);
        } catch (Exception ex) {
            markFailed(note, ex);
            throw ex;
        }
    }

    @Override
    public List<UserMeetingNoteVO> listHistoryViewByUserId(Integer userId) {
        return listHistoryViewByUserId(userId, null, null, null, null, null, null);
    }

    @Override
    public List<UserMeetingNoteVO> listHistoryViewByUserId(Integer userId, String keyword, String sceneType) {
        return listHistoryViewByUserId(userId, keyword, sceneType, null, null, null, null);
    }

    @Override
    public List<UserMeetingNoteVO> listHistoryViewByUserId(
            Integer userId,
            String keyword,
            String sceneType,
            String status,
            Boolean hasTodos,
            String dateFrom,
            String dateTo
    ) {
        requireUser(userId);
        LambdaQueryWrapper<UserMeetingNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMeetingNote::getUid, userId)
                .orderByDesc(UserMeetingNote::getCreateTime)
                .orderByDesc(UserMeetingNote::getId);
        String normalizedSceneType = normalizeSceneTypeForQuery(sceneType);
        String normalizedStatus = normalizeStatusForQuery(status);
        LocalDate normalizedDateFrom = parseLocalDate(dateFrom, "dateFrom");
        LocalDate normalizedDateTo = parseLocalDate(dateTo, "dateTo");
        validateDateRange(normalizedDateFrom, normalizedDateTo);
        if (StringUtils.hasText(normalizedSceneType)) {
            wrapper.eq(UserMeetingNote::getSceneType, normalizedSceneType);
        }
        if (StringUtils.hasText(normalizedStatus)) {
            wrapper.eq(UserMeetingNote::getStatus, normalizedStatus);
        }
        List<UserMeetingNote> notes = userMeetingNoteMapper.selectList(wrapper);
        List<UserMeetingNoteVO> result = new ArrayList<>(notes.size());
        for (UserMeetingNote note : notes) {
            if (matchesKeyword(note, keyword)
                    && matchesTodoFilter(note, hasTodos)
                    && matchesDateRange(note, normalizedDateFrom, normalizedDateTo)) {
                result.add(toView(note, false));
            }
        }
        return result;
    }

    @Override
    public UserMeetingStatsVO getMeetingStats(Integer userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserMeetingNote> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMeetingNote::getUid, userId)
                .orderByDesc(UserMeetingNote::getCreateTime)
                .orderByDesc(UserMeetingNote::getId);
        List<UserMeetingNote> notes = userMeetingNoteMapper.selectList(wrapper);

        UserMeetingStatsVO stats = new UserMeetingStatsVO();
        stats.setTotalNotes(notes.size());
        stats.setMeetingNotes(0);
        stats.setClassroomNotes(0);
        stats.setSuccessNotes(0);
        stats.setFailedNotes(0);
        stats.setTotalTodos(0);
        stats.setRecentSevenDaysNotes(0);
        stats.setLatestCreateTime(notes.isEmpty() ? null : notes.get(0).getCreateTime());

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Integer> meetingIds = new ArrayList<>();
        for (UserMeetingNote note : notes) {
            meetingIds.add(note.getId());
            if ("classroom".equals(note.getSceneType())) {
                stats.setClassroomNotes(stats.getClassroomNotes() + 1);
            } else {
                stats.setMeetingNotes(stats.getMeetingNotes() + 1);
            }
            if ("SUCCESS".equals(note.getStatus())) {
                stats.setSuccessNotes(stats.getSuccessNotes() + 1);
            } else if ("FAILED".equals(note.getStatus())) {
                stats.setFailedNotes(stats.getFailedNotes() + 1);
            }
            stats.setTotalTodos(stats.getTotalTodos() + parseStringList(note.getTodoJson()).size());
            if (note.getCreateTime() != null) {
                LocalDateTime createTime = note.getCreateTime().toInstant()
                        .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                        .toLocalDateTime();
                if (!createTime.isBefore(sevenDaysAgo)) {
                    stats.setRecentSevenDaysNotes(stats.getRecentSevenDaysNotes() + 1);
                }
            }
        }

        LambdaQueryWrapper<UserSpeakerProfile> speakerWrapper = new LambdaQueryWrapper<>();
        speakerWrapper.eq(UserSpeakerProfile::getUid, userId);
        Long speakerCount = userSpeakerProfileMapper.selectCount(speakerWrapper);
        stats.setSpeakerProfiles(speakerCount == null ? 0 : speakerCount.intValue());

        if (meetingIds.isEmpty()) {
            stats.setTotalSegments(0);
        } else {
            LambdaQueryWrapper<UserMeetingSegment> segmentWrapper = new LambdaQueryWrapper<>();
            segmentWrapper.in(UserMeetingSegment::getMeetingId, meetingIds);
            Long segmentCount = userMeetingSegmentMapper.selectCount(segmentWrapper);
            stats.setTotalSegments(segmentCount == null ? 0 : segmentCount.intValue());
        }
        return stats;
    }

    @Override
    public UserMeetingNoteVO getHistoryViewById(Integer meetingId, Integer userId) {
        UserMeetingNote note = getMeetingById(meetingId, userId);
        return toView(note, true);
    }

    @Override
    public List<UserMeetingRevisionVO> listRevisions(Integer meetingId, Integer userId) {
        getMeetingById(meetingId, userId);
        LambdaQueryWrapper<UserMeetingRevision> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMeetingRevision::getMeetingId, meetingId)
                .orderByAsc(UserMeetingRevision::getVersionNo)
                .orderByAsc(UserMeetingRevision::getId);
        List<UserMeetingRevision> revisions = userMeetingRevisionMapper.selectList(wrapper);
        List<UserMeetingRevisionVO> result = new ArrayList<>(revisions.size());
        for (UserMeetingRevision revision : revisions) {
            result.add(toRevisionView(revision));
        }
        return result;
    }

    @Override
    public UserMeetingNoteVO applyCorrection(Integer meetingId, Integer userId, MeetingCorrectionRequest request) {
        UserMeetingNote note = getMeetingById(meetingId, userId);
        if (request == null) {
            throw new IllegalArgumentException("校正内容不能为空");
        }

        List<UserMeetingSegment> segments = listSegmentEntitiesByMeetingId(note.getId());
        applySegmentCorrections(segments, request.getSpeakerSegments());

        if (request.getTitle() != null) {
            note.setTitle(normalizeCorrectionTitle(request.getTitle(), note.getSceneType()));
        }
        if (request.getSummaryText() != null) {
            note.setSummaryText(normalizeLongText(request.getSummaryText()));
        }
        if (request.getKeywords() != null) {
            note.setKeywordsJson(toJson(normalizeStringList(request.getKeywords(), 16, 32)));
        }
        if (request.getTodos() != null) {
            note.setTodoJson(toJson(normalizeStringList(request.getTodos(), 24, 300)));
        }
        if (request.getFullTranscript() != null) {
            note.setFullTranscript(normalizeLongText(request.getFullTranscript()));
        } else if (request.getSpeakerSegments() != null && !request.getSpeakerSegments().isEmpty()) {
            note.setFullTranscript(buildFullTranscriptFromSegments(segments));
        }

        note.setStatus("SUCCESS");
        note.setErrorMessage(null);
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
        UserMeetingNoteVO view = toView(note, true);
        saveRevisionSnapshot(note, view.getSpeakerBlocks(), view.getSpeakerSegments(), "MANUAL");
        return view;
    }

    @Override
    public Map<String, Object> getDiarizationReport(Integer meetingId, Integer userId) {
        UserMeetingNote note = getMeetingById(meetingId, userId);
        List<UserMeetingSegment> segments = listSegmentEntitiesByMeetingId(note.getId());
        Map<String, Integer> clusterSizes = new LinkedHashMap<>();
        int profileMatched = 0;
        for (UserMeetingSegment segment : segments) {
            String label = normalizeSpeakerName(segment.getSpeakerName());
            if (!StringUtils.hasText(label)) label = "未知发言人";
            clusterSizes.put(label, clusterSizes.getOrDefault(label, 0) + 1);
            if (segment.getSpeakerProfileId() != null) profileMatched++;
        }
        long manualRevisions = userMeetingRevisionMapper.selectCount(new LambdaQueryWrapper<UserMeetingRevision>()
                .eq(UserMeetingRevision::getMeetingId, note.getId())
                .eq(UserMeetingRevision::getRevisionType, "MANUAL"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("adapter", "cam++-voiceprint-anonymous-clustering");
        report.put("anonymousOnly", true);
        report.put("totalSegments", segments.size());
        report.put("clusterCount", clusterSizes.size());
        report.put("clusters", clusterSizes);
        report.put("profileMatchedSegments", profileMatched);
        report.put("manualRevisionCount", manualRevisions);
        report.put("derStatus", "NOT_EVALUATED");
        report.put("derMessage", "DER 需要带人工真实说话人标注的参照集；当前会议只提供匿名分组，不虚构评估结果。");
        report.put("manualEvidence", "每次人工校正都会生成 MANUAL 版本快照，可在版本对比中核对修改前后结果。");
        return report;
    }

    @Override
    public void downloadRawAudio(Integer meetingId, Integer userId, HttpServletResponse response) throws Exception {
        UserMeetingNote note = getMeetingById(meetingId, userId);
        if (!StringUtils.hasText(note.getRawBucket()) || !StringUtils.hasText(note.getRawObject())) {
            throw new IllegalArgumentException("当前纪要记录还没有可下载的原始音频");
        }
        response.setContentType(normalizeContentType(note.getRawContentType()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(safeFilename(note.getRawFilename()), StandardCharsets.UTF_8)
        );
        try (GetObjectResponse object = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(note.getRawBucket())
                        .object(note.getRawObject())
                        .build()
        )) {
            object.transferTo(response.getOutputStream());
        }
    }

    @Override
    public void downloadSegmentAudio(Integer meetingId, Integer segmentId, Integer userId, HttpServletResponse response) throws Exception {
        UserMeetingNote note = getMeetingById(meetingId, userId);
        UserMeetingSegment segment = getSegmentById(segmentId);
        if (!note.getId().equals(segment.getMeetingId())) {
            throw new IllegalArgumentException("当前片段不属于指定的纪要记录");
        }
        if (!StringUtils.hasText(segment.getSegmentBucket()) || !StringUtils.hasText(segment.getSegmentObject())) {
            throw new IllegalArgumentException("当前片段还没有可下载的音频");
        }
        response.setContentType(normalizeContentType(segment.getSegmentContentType()));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(safeFilename(segment.getSegmentFilename()), StandardCharsets.UTF_8)
        );
        try (GetObjectResponse object = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(segment.getSegmentBucket())
                        .object(segment.getSegmentObject())
                        .build()
        )) {
            object.transferTo(response.getOutputStream());
        }
    }

    @Override
    public void exportMeetingNote(Integer meetingId, Integer userId, String format, String template, HttpServletResponse response) throws Exception {
        UserMeetingNoteVO detail = getHistoryViewById(meetingId, userId);
        String normalizedFormat = normalizeExportFormat(format);
        MeetingExportTemplate exportTemplate = parseExportTemplate(template);
        String exportFilename = buildExportFilename(detail.getTitle(), meetingId, normalizedFormat);
        response.setHeader(
                "Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(exportFilename, StandardCharsets.UTF_8)
        );
        if ("docx".equals(normalizedFormat)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            try (XWPFDocument document = buildDocxExport(detail, exportTemplate)) {
                document.write(response.getOutputStream());
                response.getOutputStream().flush();
            }
            return;
        }

        String content = "md".equals(normalizedFormat)
                ? buildMarkdownExport(detail, exportTemplate)
                : buildTextExport(detail, exportTemplate);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("md".equals(normalizedFormat) ? "text/markdown;charset=UTF-8" : "text/plain;charset=UTF-8");
        response.getWriter().write(content);
        response.getWriter().flush();
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

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传会议或课堂音频");
        }
    }

    private String normalizeSceneType(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return "meeting";
        }
        String normalized = sceneType.trim().toLowerCase(Locale.ROOT);
        if (!"meeting".equals(normalized) && !"classroom".equals(normalized)) {
            throw new IllegalArgumentException("sceneType 仅支持 meeting 或 classroom");
        }
        return normalized;
    }

    private String normalizeSceneTypeForQuery(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return null;
        }
        String normalized = sceneType.trim().toLowerCase(Locale.ROOT);
        if (!"meeting".equals(normalized) && !"classroom".equals(normalized)) {
            throw new IllegalArgumentException("sceneType 仅支持 meeting 或 classroom");
        }
        return normalized;
    }

    private String normalizeStatusForQuery(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        Set<String> supportedStatuses = Set.of("PENDING", "UPLOADED", "PROCESSING", "SUCCESS", "FAILED");
        if (!supportedStatuses.contains(normalized)) {
            throw new IllegalArgumentException("status 仅支持 PENDING、UPLOADED、PROCESSING、SUCCESS 或 FAILED");
        }
        return normalized;
    }

    private LocalDate parseLocalDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " 格式错误，应为 yyyy-MM-dd");
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom 不能晚于 dateTo");
        }
    }

    private String normalizeTitle(String title, String sceneType) {
        if (StringUtils.hasText(title)) {
            String normalized = title.trim();
            return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
        }
        String prefix = "classroom".equals(sceneType) ? "课堂纪要" : "会议纪要";
        return prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String normalizeCorrectionTitle(String title, String sceneType) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("纪要标题不能为空");
        }
        return normalizeTitle(title, sceneType);
    }

    private String normalizeLongText(String text) {
        if (text == null) {
            return null;
        }
        return text.trim();
    }

    private List<String> normalizeStringList(List<String> values, int maxItems, int maxItemLength) {
        if (values == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String cleaned = value.trim();
            if (cleaned.length() > maxItemLength) {
                cleaned = cleaned.substring(0, maxItemLength);
            }
            normalized.add(cleaned);
            if (normalized.size() >= maxItems) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeSelectedSpeakerIds(String selectedSpeakerIds) {
        if (!StringUtils.hasText(selectedSpeakerIds)) {
            return null;
        }
        return selectedSpeakerIds.trim();
    }

    private boolean matchesKeyword(UserMeetingNote note, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsKeyword(note.getTitle(), normalizedKeyword)
                || containsKeyword(note.getSummaryText(), normalizedKeyword)
                || containsKeyword(note.getFullTranscript(), normalizedKeyword)
                || containsKeyword(note.getKeywordsJson(), normalizedKeyword)
                || containsKeyword(note.getTodoJson(), normalizedKeyword)
                || containsKeyword(note.getErrorMessage(), normalizedKeyword);
    }

    private boolean matchesTodoFilter(UserMeetingNote note, Boolean hasTodos) {
        if (hasTodos == null) {
            return true;
        }
        boolean noteHasTodos = !parseStringList(note.getTodoJson()).isEmpty();
        return hasTodos.equals(noteHasTodos);
    }

    private boolean matchesDateRange(UserMeetingNote note, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return true;
        }
        if (note.getCreateTime() == null) {
            return false;
        }
        LocalDate createDate = note.getCreateTime().toInstant()
                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                .toLocalDate();
        if (dateFrom != null && createDate.isBefore(dateFrom)) {
            return false;
        }
        return dateTo == null || !createDate.isAfter(dateTo);
    }

    private boolean containsKeyword(String source, String normalizedKeyword) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(normalizedKeyword)) {
            return false;
        }
        return source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private String normalizeExportFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return "txt";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!"txt".equals(normalized) && !"md".equals(normalized) && !"markdown".equals(normalized) && !"docx".equals(normalized)) {
            throw new IllegalArgumentException("format 仅支持 txt、md 或 docx");
        }
        return "markdown".equals(normalized) ? "md" : normalized;
    }

    private String buildExportFilename(String title, Integer meetingId, String format) {
        String baseName = sanitizeFilename(StringUtils.hasText(title) ? title : "meeting-note-" + meetingId);
        return baseName + "." + format;
    }

    private String buildTextExport(UserMeetingNoteVO detail, MeetingExportTemplate template) {
        List<String> lines = new ArrayList<>();
        lines.add("智能纪要导出");
        lines.add("标题：" + safeExportText(detail.getTitle()));
        if (shouldInclude(template.getIncludeMeta())) {
            lines.add("场景：" + ("classroom".equals(detail.getSceneType()) ? "课堂模式" : "会议模式"));
            lines.add("创建时间：" + safeExportText(formatDate(detail.getCreateTime())));
            lines.add("状态：" + safeExportText(detail.getStatus()));
        }
        if (shouldInclude(template.getIncludeSummary())) {
            lines.add("");
            lines.add("【纪要摘要】");
            lines.add(safeExportText(detail.getSummaryText()));
        }
        if (shouldInclude(template.getIncludeKeywords())) {
            lines.add("");
            lines.add("【关键词】");
            lines.add(detail.getKeywords() == null || detail.getKeywords().isEmpty() ? "暂无关键词" : String.join("、", detail.getKeywords()));
        }
        if (shouldInclude(template.getIncludeStructuredSections())) {
            lines.add("");
            lines.add("【结构化纪要】");
            appendStructuredSectionsText(lines, detail.getStructuredSections());
        }
        if (shouldInclude(template.getIncludeRoleInsights())) {
            lines.add("");
            lines.add("【发言角色分析】");
            appendRoleInsightsText(lines, detail.getRoleInsights());
        }
        if (shouldInclude(template.getIncludeTodoChains())) {
            lines.add("");
            lines.add("【待办责任链分析】");
            appendTodoChainsText(lines, detail.getTodoChains());
        }
        if (shouldInclude(template.getIncludeDecisionInsights())) {
            lines.add("");
            lines.add("【结论与待确认事项】");
            appendDecisionInsightsText(lines, detail.getDecisionInsights());
        }
        if (shouldInclude(template.getIncludeTodos())) {
            lines.add("");
            lines.add("【待办事项】");
            if (detail.getTodos() == null || detail.getTodos().isEmpty()) {
                lines.add("暂无待办事项");
            } else {
                for (String todo : detail.getTodos()) {
                    lines.add("- " + todo);
                }
            }
        }
        if (shouldInclude(template.getIncludeSpeakerTranscript())) {
            lines.add("");
            lines.add("【发言人纪要】");
            lines.add(safeExportText(detail.getSpeakerTranscript()));
        }
        if (shouldInclude(template.getIncludeSpeakerBlocks())) {
            lines.add("");
            lines.add("【整理后发言块】");
            appendSpeakerBlocksText(lines, detail.getSpeakerBlocks());
        }
        if (shouldInclude(template.getIncludeFullTranscript())) {
            lines.add("");
            lines.add("【全文转写】");
            lines.add(safeExportText(detail.getFullTranscript()));
        }
        return String.join("\n", lines);
    }

    private String buildMarkdownExport(UserMeetingNoteVO detail, MeetingExportTemplate template) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + safeExportText(detail.getTitle()));
        if (shouldInclude(template.getIncludeMeta())) {
            lines.add("");
            lines.add("- 场景：" + ("classroom".equals(detail.getSceneType()) ? "课堂模式" : "会议模式"));
            lines.add("- 创建时间：" + safeExportText(formatDate(detail.getCreateTime())));
            lines.add("- 状态：" + safeExportText(detail.getStatus()));
        }
        if (shouldInclude(template.getIncludeSummary())) {
            lines.add("");
            lines.add("## 纪要摘要");
            lines.add("");
            lines.add(safeExportText(detail.getSummaryText()));
        }
        if (shouldInclude(template.getIncludeKeywords())) {
            lines.add("");
            lines.add("## 关键词");
            lines.add("");
            lines.add(detail.getKeywords() == null || detail.getKeywords().isEmpty()
                    ? "暂无关键词"
                    : String.join("、", detail.getKeywords()));
        }
        if (shouldInclude(template.getIncludeStructuredSections())) {
            lines.add("");
            lines.add("## 结构化纪要");
            lines.add("");
            appendStructuredSectionsMarkdown(lines, detail.getStructuredSections());
        }
        if (shouldInclude(template.getIncludeRoleInsights())) {
            lines.add("");
            lines.add("## 发言角色分析");
            lines.add("");
            appendRoleInsightsMarkdown(lines, detail.getRoleInsights());
        }
        if (shouldInclude(template.getIncludeTodoChains())) {
            lines.add("");
            lines.add("## 待办责任链分析");
            lines.add("");
            appendTodoChainsMarkdown(lines, detail.getTodoChains());
        }
        if (shouldInclude(template.getIncludeDecisionInsights())) {
            lines.add("");
            lines.add("## 结论与待确认事项");
            lines.add("");
            appendDecisionInsightsMarkdown(lines, detail.getDecisionInsights());
        }
        if (shouldInclude(template.getIncludeTodos())) {
            lines.add("");
            lines.add("## 待办事项");
            lines.add("");
            if (detail.getTodos() == null || detail.getTodos().isEmpty()) {
                lines.add("- 暂无待办事项");
            } else {
                for (String todo : detail.getTodos()) {
                    lines.add("- " + todo);
                }
            }
        }
        if (shouldInclude(template.getIncludeSpeakerTranscript())) {
            lines.add("");
            lines.add("## 发言人纪要");
            lines.add("");
            lines.add(safeExportText(detail.getSpeakerTranscript()));
        }
        if (shouldInclude(template.getIncludeSpeakerBlocks())) {
            lines.add("");
            lines.add("## 整理后发言块");
            lines.add("");
            appendSpeakerBlocksMarkdown(lines, detail.getSpeakerBlocks());
        }
        if (shouldInclude(template.getIncludeFullTranscript())) {
            lines.add("");
            lines.add("## 全文转写");
            lines.add("");
            lines.add(safeExportText(detail.getFullTranscript()));
        }
        return String.join("\n", lines);
    }

    private void appendSpeakerBlocksText(List<String> lines, List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        if (speakerBlocks == null || speakerBlocks.isEmpty()) {
            lines.add("暂无整理后发言块");
            return;
        }
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            lines.add("[" + formatRange(block.getStartMs(), block.getEndMs()) + "] "
                    + safeExportText(block.getSpeakerName())
                    + "（合并片段 " + (block.getSegmentCount() == null ? 1 : block.getSegmentCount()) + " 段）");
            lines.add(safeExportText(block.getTranscript()));
            lines.add("");
        }
    }

    private void appendStructuredSectionsText(List<String> lines, List<UserMeetingInsightSectionVO> sections) {
        if (sections == null || sections.isEmpty()) {
            lines.add("暂无结构化纪要");
            return;
        }
        for (UserMeetingInsightSectionVO section : sections) {
            lines.add("[" + safeExportText(section.getTitle()) + "]");
            if (StringUtils.hasText(section.getSubtitle())) {
                lines.add(safeExportText(section.getSubtitle()));
            }
            List<String> items = section.getItems();
            if (items == null || items.isEmpty()) {
                lines.add("- 暂无内容");
            } else {
                for (String item : items) {
                    lines.add("- " + safeExportText(item));
                }
            }
            lines.add("");
        }
    }

    private void appendStructuredSectionsMarkdown(List<String> lines, List<UserMeetingInsightSectionVO> sections) {
        if (sections == null || sections.isEmpty()) {
            lines.add("- 暂无结构化纪要");
            return;
        }
        for (UserMeetingInsightSectionVO section : sections) {
            lines.add("### " + safeExportText(section.getTitle()));
            lines.add("");
            if (StringUtils.hasText(section.getSubtitle())) {
                lines.add("> " + safeExportText(section.getSubtitle()));
                lines.add("");
            }
            List<String> items = section.getItems();
            if (items == null || items.isEmpty()) {
                lines.add("- 暂无内容");
            } else {
                for (String item : items) {
                    lines.add("- " + safeExportText(item));
                }
            }
            lines.add("");
        }
    }

    private void appendRoleInsightsText(List<String> lines, List<UserMeetingRoleInsightVO> roleInsights) {
        if (roleInsights == null || roleInsights.isEmpty()) {
            lines.add("暂无发言角色分析");
            return;
        }
        for (UserMeetingRoleInsightVO insight : roleInsights) {
            lines.add("[" + safeExportText(insight.getRoleLabel()) + "] "
                    + safeExportText(insight.getSpeakerName()));
            lines.add("作用说明：" + safeExportText(insight.getContribution()));
            if (StringUtils.hasText(insight.getEvidence())) {
                lines.add("证据片段：" + safeExportText(insight.getEvidence()));
            }
            lines.add("");
        }
    }

    private void appendTodoChainsText(List<String> lines, List<UserMeetingTodoChainVO> todoChains) {
        if (todoChains == null || todoChains.isEmpty()) {
            lines.add("暂无待办责任链分析");
            return;
        }
        for (UserMeetingTodoChainVO chain : todoChains) {
            lines.add("[" + safeExportText(chain.getStatusLabel()) + "] "
                    + safeExportText(chain.getTaskText()));
            lines.add("负责人：" + safeExportText(chain.getOwner())
                    + " | 动作：" + safeExportText(chain.getAction())
                    + " | 时间：" + safeExportText(chain.getDeadline()));
            lines.add("");
        }
    }

    private void appendDecisionInsightsText(List<String> lines, List<UserMeetingDecisionInsightVO> decisionInsights) {
        if (decisionInsights == null || decisionInsights.isEmpty()) {
            lines.add("暂无结论分析");
            return;
        }
        for (UserMeetingDecisionInsightVO insight : decisionInsights) {
            lines.add("[" + safeExportText(insight.getTypeLabel()) + "] "
                    + safeExportText(insight.getContent()));
            if (StringUtils.hasText(insight.getSourceSpeaker())) {
                lines.add("来源发言人：" + safeExportText(insight.getSourceSpeaker()));
            }
            lines.add("");
        }
    }

    private void appendRoleInsightsMarkdown(List<String> lines, List<UserMeetingRoleInsightVO> roleInsights) {
        if (roleInsights == null || roleInsights.isEmpty()) {
            lines.add("- 暂无发言角色分析");
            return;
        }
        for (UserMeetingRoleInsightVO insight : roleInsights) {
            lines.add("- **" + safeExportText(insight.getRoleLabel()) + "**："
                    + safeExportText(insight.getSpeakerName()));
            lines.add("  - 作用说明：" + safeExportText(insight.getContribution()));
            if (StringUtils.hasText(insight.getEvidence())) {
                lines.add("  - 证据片段：" + safeExportText(insight.getEvidence()));
            }
        }
    }

    private void appendTodoChainsMarkdown(List<String> lines, List<UserMeetingTodoChainVO> todoChains) {
        if (todoChains == null || todoChains.isEmpty()) {
            lines.add("- 暂无待办责任链分析");
            return;
        }
        for (UserMeetingTodoChainVO chain : todoChains) {
            lines.add("- **" + safeExportText(chain.getStatusLabel()) + "**："
                    + safeExportText(chain.getTaskText()));
            lines.add("  - 负责人：" + safeExportText(chain.getOwner()));
            lines.add("  - 动作：" + safeExportText(chain.getAction()));
            lines.add("  - 时间：" + safeExportText(chain.getDeadline()));
        }
    }

    private void appendDecisionInsightsMarkdown(List<String> lines, List<UserMeetingDecisionInsightVO> decisionInsights) {
        if (decisionInsights == null || decisionInsights.isEmpty()) {
            lines.add("- 暂无结论分析");
            return;
        }
        for (UserMeetingDecisionInsightVO insight : decisionInsights) {
            lines.add("- **" + safeExportText(insight.getTypeLabel()) + "**："
                    + safeExportText(insight.getContent()));
            if (StringUtils.hasText(insight.getSourceSpeaker())) {
                lines.add("  - 来源发言人：" + safeExportText(insight.getSourceSpeaker()));
            }
        }
    }

    private void appendSpeakerBlocksMarkdown(List<String> lines, List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        if (speakerBlocks == null || speakerBlocks.isEmpty()) {
            lines.add("- 暂无整理后发言块");
            return;
        }
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            lines.add("- **" + safeExportText(block.getSpeakerName()) + "** "
                    + "(" + formatRange(block.getStartMs(), block.getEndMs()) + "，合并片段 "
                    + (block.getSegmentCount() == null ? 1 : block.getSegmentCount()) + " 段)");
            lines.add("");
            lines.add("  " + safeExportText(block.getTranscript()));
            lines.add("");
        }
    }

    private XWPFDocument buildDocxExport(UserMeetingNoteVO detail, MeetingExportTemplate template) {
        XWPFDocument document = new XWPFDocument();
        addDocxTitle(document, safeExportText(detail.getTitle()));
        if (shouldInclude(template.getIncludeMeta())) {
            addDocxMetaTable(document, detail);
        }
        if (shouldInclude(template.getIncludeSummary())) {
            addDocxHeading(document, "纪要摘要");
            addDocxParagraph(document, safeExportText(detail.getSummaryText()));
        }
        if (shouldInclude(template.getIncludeKeywords())) {
            addDocxHeading(document, "关键词");
            addDocxParagraph(document, detail.getKeywords() == null || detail.getKeywords().isEmpty()
                    ? "暂无关键词"
                    : String.join("、", detail.getKeywords()));
        }
        if (shouldInclude(template.getIncludeStructuredSections())) {
            addDocxHeading(document, "结构化纪要");
            addDocxStructuredSections(document, detail.getStructuredSections());
        }
        if (shouldInclude(template.getIncludeRoleInsights())) {
            addDocxHeading(document, "发言角色分析");
            if (detail.getRoleInsights() == null || detail.getRoleInsights().isEmpty()) {
                addDocxParagraph(document, "暂无发言角色分析");
            } else {
                addDocxRoleInsightTable(document, detail.getRoleInsights());
            }
        }
        if (shouldInclude(template.getIncludeTodoChains())) {
            addDocxHeading(document, "待办责任链分析");
            if (detail.getTodoChains() == null || detail.getTodoChains().isEmpty()) {
                addDocxParagraph(document, "暂无待办责任链分析");
            } else {
                addDocxTodoChainTable(document, detail.getTodoChains());
            }
        }
        if (shouldInclude(template.getIncludeDecisionInsights())) {
            addDocxHeading(document, "结论与待确认事项");
            if (detail.getDecisionInsights() == null || detail.getDecisionInsights().isEmpty()) {
                addDocxParagraph(document, "暂无结论分析");
            } else {
                addDocxDecisionInsightTable(document, detail.getDecisionInsights());
            }
        }
        if (shouldInclude(template.getIncludeTodos())) {
            addDocxHeading(document, "待办事项");
            if (detail.getTodos() == null || detail.getTodos().isEmpty()) {
                addDocxParagraph(document, "暂无待办事项");
            } else {
                addDocxTodoTable(document, detail.getTodos());
            }
        }
        if (shouldInclude(template.getIncludeSpeakerTranscript())) {
            addDocxHeading(document, "发言人纪要");
            addDocxParagraph(document, safeExportText(detail.getSpeakerTranscript()));
        }
        if (shouldInclude(template.getIncludeSpeakerBlocks())) {
            addDocxHeading(document, "整理后发言块");
            if (detail.getSpeakerBlocks() == null || detail.getSpeakerBlocks().isEmpty()) {
                addDocxParagraph(document, "暂无整理后发言块");
            } else {
                addDocxSpeakerBlockTable(document, detail.getSpeakerBlocks());
            }
        }
        if (shouldInclude(template.getIncludeFullTranscript())) {
            addDocxHeading(document, "全文转写");
            addDocxParagraph(document, safeExportText(detail.getFullTranscript()));
        }
        return document;
    }

    private void addDocxTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(220);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(text);
    }

    private void addDocxHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(200);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(14);
        run.setText(text);
    }

    private void addDocxParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(90);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setText(text);
    }

    private void addDocxBullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setText("• " + text);
    }

    private void addDocxMetaTable(XWPFDocument document, UserMeetingNoteVO detail) {
        XWPFTable table = document.createTable(2, 2);
        fillTableCell(table.getRow(0).getCell(0), "场景类型");
        fillTableCell(table.getRow(0).getCell(1), "classroom".equals(detail.getSceneType()) ? "课堂模式" : "会议模式");
        fillTableCell(table.getRow(1).getCell(0), "创建时间");
        fillTableCell(table.getRow(1).getCell(1), safeExportText(formatDate(detail.getCreateTime())));

        XWPFTableRow statusRow = table.createRow();
        fillTableCell(statusRow.getCell(0), "处理状态");
        fillTableCell(statusRow.getCell(1), safeExportText(detail.getStatus()));
    }

    private void addDocxTodoTable(XWPFDocument document, List<String> todos) {
        XWPFTable table = document.createTable(1, 2);
        fillTableCell(table.getRow(0).getCell(0), "序号");
        fillTableCell(table.getRow(0).getCell(1), "待办事项");
        int index = 1;
        for (String todo : todos) {
            XWPFTableRow row = table.createRow();
            fillTableCell(row.getCell(0), String.valueOf(index++));
            fillTableCell(row.getCell(1), safeExportText(todo));
        }
    }

    private void addDocxRoleInsightTable(XWPFDocument document, List<UserMeetingRoleInsightVO> roleInsights) {
        XWPFTable table = document.createTable(1, 3);
        fillTableCell(table.getRow(0).getCell(0), "角色");
        fillTableCell(table.getRow(0).getCell(1), "发言人");
        fillTableCell(table.getRow(0).getCell(2), "作用说明 / 证据");
        for (UserMeetingRoleInsightVO insight : roleInsights) {
            XWPFTableRow row = table.createRow();
            fillTableCell(row.getCell(0), safeExportText(insight.getRoleLabel()));
            fillTableCell(row.getCell(1), safeExportText(insight.getSpeakerName()));
            fillTableCell(row.getCell(2), safeExportText(insight.getContribution())
                    + (StringUtils.hasText(insight.getEvidence())
                    ? "\n证据片段：" + safeExportText(insight.getEvidence())
                    : ""));
        }
    }

    private void addDocxTodoChainTable(XWPFDocument document, List<UserMeetingTodoChainVO> todoChains) {
        XWPFTable table = document.createTable(1, 5);
        fillTableCell(table.getRow(0).getCell(0), "状态");
        fillTableCell(table.getRow(0).getCell(1), "任务");
        fillTableCell(table.getRow(0).getCell(2), "负责人");
        fillTableCell(table.getRow(0).getCell(3), "动作");
        fillTableCell(table.getRow(0).getCell(4), "时间");
        for (UserMeetingTodoChainVO chain : todoChains) {
            XWPFTableRow row = table.createRow();
            fillTableCell(row.getCell(0), safeExportText(chain.getStatusLabel()));
            fillTableCell(row.getCell(1), safeExportText(chain.getTaskText()));
            fillTableCell(row.getCell(2), safeExportText(chain.getOwner()));
            fillTableCell(row.getCell(3), safeExportText(chain.getAction()));
            fillTableCell(row.getCell(4), safeExportText(chain.getDeadline()));
        }
    }

    private void addDocxDecisionInsightTable(XWPFDocument document, List<UserMeetingDecisionInsightVO> decisionInsights) {
        XWPFTable table = document.createTable(1, 3);
        fillTableCell(table.getRow(0).getCell(0), "类型");
        fillTableCell(table.getRow(0).getCell(1), "内容");
        fillTableCell(table.getRow(0).getCell(2), "来源发言人");
        for (UserMeetingDecisionInsightVO insight : decisionInsights) {
            XWPFTableRow row = table.createRow();
            fillTableCell(row.getCell(0), safeExportText(insight.getTypeLabel()));
            fillTableCell(row.getCell(1), safeExportText(insight.getContent()));
            fillTableCell(row.getCell(2), safeExportText(insight.getSourceSpeaker()));
        }
    }

    private void addDocxSpeakerBlockTable(XWPFDocument document, List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        XWPFTable table = document.createTable(1, 4);
        fillTableCell(table.getRow(0).getCell(0), "发言人");
        fillTableCell(table.getRow(0).getCell(1), "时间范围");
        fillTableCell(table.getRow(0).getCell(2), "合并片段数");
        fillTableCell(table.getRow(0).getCell(3), "发言内容");
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            XWPFTableRow row = table.createRow();
            fillTableCell(row.getCell(0), safeExportText(block.getSpeakerName()));
            fillTableCell(row.getCell(1), formatRange(block.getStartMs(), block.getEndMs()));
            fillTableCell(row.getCell(2), String.valueOf(block.getSegmentCount() == null ? 1 : block.getSegmentCount()));
            fillTableCell(row.getCell(3), safeExportText(block.getTranscript()));
        }
    }

    private void addDocxStructuredSections(XWPFDocument document, List<UserMeetingInsightSectionVO> sections) {
        if (sections == null || sections.isEmpty()) {
            addDocxParagraph(document, "暂无结构化纪要");
            return;
        }
        for (UserMeetingInsightSectionVO section : sections) {
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setSpacingAfter(40);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(12);
            titleRun.setText(safeExportText(section.getTitle()));
            if (StringUtils.hasText(section.getSubtitle())) {
                XWPFParagraph subtitleParagraph = document.createParagraph();
                subtitleParagraph.setSpacingAfter(40);
                XWPFRun subtitleRun = subtitleParagraph.createRun();
                subtitleRun.setFontSize(10);
                subtitleRun.setItalic(true);
                subtitleRun.setText(safeExportText(section.getSubtitle()));
            }
            List<String> items = section.getItems();
            if (items == null || items.isEmpty()) {
                addDocxBullet(document, "暂无内容");
                continue;
            }
            for (String item : items) {
                addDocxBullet(document, safeExportText(item));
            }
        }
    }

    private void fillTableCell(XWPFTableCell cell, String text) {
        if (cell == null) {
            return;
        }
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingAfter(40);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(10);
        run.setText(text);
    }

    private MeetingExportTemplate parseExportTemplate(String template) {
        if (!StringUtils.hasText(template)) {
            return new MeetingExportTemplate();
        }
        try {
            MeetingExportTemplate parsed = objectMapper.readValue(template, MeetingExportTemplate.class);
            return parsed == null ? new MeetingExportTemplate() : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("导出模板参数不合法");
        }
    }

    private boolean shouldInclude(Boolean flag) {
        return flag == null || flag;
    }

    private String safeExportText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "暂无";
    }

    private String formatRange(Long startMs, Long endMs) {
        return formatMinuteSecond(startMs) + " - " + formatMinuteSecond(endMs);
    }

    private String formatMinuteSecond(Long value) {
        if (value == null || value < 0) {
            return "--:--";
        }
        long totalSeconds = value / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(date.toInstant().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime());
    }

    private List<Integer> parseSelectedSpeakerIds(String selectedSpeakerIds) {
        if (!StringUtils.hasText(selectedSpeakerIds)) {
            return Collections.emptyList();
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (String token : selectedSpeakerIds.split(",")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String value = token.trim();
            if (!value.matches("\\d+")) {
                throw new IllegalArgumentException("selectedSpeakerIds 格式不合法");
            }
            result.add(Integer.parseInt(value));
        }
        return new ArrayList<>(result);
    }

    private UserMeetingNote createPendingNote(
            Integer userId,
            String title,
            String sceneType,
            String selectedSpeakerIdsJson,
            String rawFilename,
            String rawContentType,
            long rawFileSize
    ) {
        UserMeetingNote note = new UserMeetingNote();
        note.setUid(userId);
        note.setTitle(title);
        note.setSceneType(sceneType);
        note.setSelectedSpeakerIdsJson(selectedSpeakerIdsJson);
        note.setRawFilename(rawFilename);
        note.setRawContentType(rawContentType);
        note.setRawFileSize(rawFileSize);
        note.setStatus("PENDING");
        note.setCreateTime(new Date());
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.insert(note);
        return note;
    }

    private void clearSegments(Integer meetingId) {
        LambdaQueryWrapper<UserMeetingSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMeetingSegment::getMeetingId, meetingId);
        userMeetingSegmentMapper.delete(wrapper);
    }

    private void uploadRawAudio(UserMeetingNote note, Integer userId, String filename, byte[] bytes, String contentType) throws Exception {
        String object = buildRawObjectPath(userId, filename);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType(normalizeContentType(contentType))
                        .build()
        );
        note.setRawBucket(minioInfo.getBucket());
        note.setRawObject(object);
        note.setRawFileSize((long) bytes.length);
        note.setStatus("UPLOADED");
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
    }

    private void bindRawAudioFromHistory(UserMeetingNote note, UserAudioHistory history) {
        note.setRawBucket(history.getBucket());
        note.setRawObject(history.getObject());
        note.setRawFilename(safeFilename(history.getOriginalFilename()));
        note.setRawContentType(normalizeContentType(history.getContentType()));
        note.setRawFileSize(history.getFileSize());
        note.setStatus("UPLOADED");
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
    }

    private void markProcessing(UserMeetingNote note) {
        note.setStatus("PROCESSING");
        note.setErrorMessage(null);
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
    }

    private void ensureHistoryAudioAvailable(UserAudioHistory history) {
        if (history == null) {
            throw new IllegalArgumentException("录音历史不存在");
        }
        if (!StringUtils.hasText(history.getBucket()) || !StringUtils.hasText(history.getObject())) {
            throw new IllegalArgumentException("当前录音历史没有可导入的原始音频");
        }
    }

    private Map<String, Object> processMeetingNote(
            UserMeetingNote note,
            Integer userId,
            byte[] bytes,
            String originalFilename,
            String originalContentType,
            String normalizedSpeakerIds,
            Integer batchSizeS,
            String hotword,
            boolean autoDiarization,
            String existingTranscript,
            String existingRawResult
    ) throws Exception {
        MultipartFile sourceFile = new InMemoryMultipartFile(
                "file",
                safeFilename(originalFilename),
                normalizeContentType(originalContentType),
                bytes
        );

        String transcript = existingTranscript;
        String rawResult = existingRawResult;
        if (!StringUtils.hasText(transcript)) {
            JsonNode response = funasrService.transcribeAudio(sourceFile, batchSizeS, hotword);
            transcript = extractAsrText(response);
            rawResult = toJson(response);
        }

        List<String> sentences = splitSentences(transcript);
        List<String> keywords = extractKeywords(sentences);
        List<String> todos = extractTodos(sentences);
        String summary = buildSummary(note.getSceneType(), note.getTitle(), sentences, todos);

        List<UserMeetingSegmentVO> speakerSegments = Collections.emptyList();
        List<UserMeetingSpeakerBlockVO> speakerBlocks = Collections.emptyList();
        String speakerTranscript = "";
        List<Integer> selectedProfileIds = parseSelectedSpeakerIds(normalizedSpeakerIds);
        boolean shouldBuildSpeakerSegments = autoDiarization || !selectedProfileIds.isEmpty();
        if (shouldBuildSpeakerSegments) {
            persistIntermediateResult(note, transcript, summary, keywords, todos, rawResult);
        }
        if (shouldBuildSpeakerSegments) {
            speakerSegments = buildSpeakerSegments(
                    userId,
                    note,
                    bytes,
                    originalFilename,
                    originalContentType,
                    selectedProfileIds,
                    autoDiarization,
                    batchSizeS,
                    hotword
            );
            speakerBlocks = mergeSpeakerBlocks(speakerSegments);
            speakerTranscript = buildSpeakerTranscript(speakerBlocks);
        }

        MeetingAnalysisBundle analysis = buildMeetingAnalysis(sentences, todos, speakerBlocks);

        note.setFullTranscript(transcript);
        note.setSummaryText(summary);
        note.setKeywordsJson(toJson(keywords));
        note.setTodoJson(toJson(todos));
        note.setRawResult(rawResult);
        note.setStatus("SUCCESS");
        note.setErrorMessage(null);
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
        saveRevisionSnapshot(note, speakerBlocks, speakerSegments, "AUTO");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meetingId", note.getId());
        result.put("userId", userId);
        result.put("title", note.getTitle());
        result.put("sceneType", note.getSceneType());
        result.put("rawObject", note.getRawObject());
        result.put("summaryText", note.getSummaryText());
        result.put("keywords", keywords);
        result.put("todos", todos);
        result.put("structuredSections", buildStructuredSections(note.getSceneType(), note.getTitle(), sentences, keywords, todos, speakerBlocks));
        result.put("roleInsights", analysis.roleInsights);
        result.put("todoChains", analysis.todoChains);
        result.put("decisionInsights", analysis.decisionInsights);
        result.put("fullTranscript", note.getFullTranscript());
        result.put("speakerTranscript", speakerTranscript);
        result.put("speakerBlocks", speakerBlocks);
        result.put("speakerSegments", speakerSegments);
        result.put("status", note.getStatus());
        MeetingProcessingMeta processingMeta = buildProcessingMeta(note, speakerSegments.size());
        result.put("processingStage", processingMeta.stage);
        result.put("processingLabel", processingMeta.label);
        result.put("processingDescription", processingMeta.description);
        result.put("processingPercent", processingMeta.percent);
        result.put("errorMessage", note.getErrorMessage());
        return result;
    }

    private void enqueueMeetingProcessing(
            Integer meetingId,
            Integer userId,
            String normalizedSpeakerIds,
            Integer batchSizeS,
            String hotword,
            boolean autoDiarization,
            String existingTranscript,
            String existingRawResult
    ) {
        meetingTaskExecutor.execute(() -> {
            UserMeetingNote current = userMeetingNoteMapper.selectById(meetingId);
            if (current == null) {
                return;
            }

            try {
                byte[] bytes = readObjectBytes(current.getRawBucket(), current.getRawObject());
                processMeetingNote(
                        current,
                        userId,
                        bytes,
                        current.getRawFilename(),
                        current.getRawContentType(),
                        normalizedSpeakerIds,
                        batchSizeS,
                        hotword,
                        autoDiarization,
                        existingTranscript,
                        existingRawResult
                );
            } catch (Exception ex) {
                UserMeetingNote latest = userMeetingNoteMapper.selectById(meetingId);
                if (latest != null) {
                    markFailed(latest, ex);
                }
            }
        });
    }

    private Map<String, Object> buildQueuedResult(UserMeetingNote note, Integer userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meetingId", note.getId());
        result.put("userId", userId);
        result.put("title", note.getTitle());
        result.put("sceneType", note.getSceneType());
        result.put("rawObject", note.getRawObject());
        result.put("summaryText", note.getSummaryText());
        result.put("keywords", parseStringList(note.getKeywordsJson()));
        result.put("todos", parseStringList(note.getTodoJson()));
        MeetingAnalysisBundle analysis = buildMeetingAnalysis(
                splitSentences(note.getFullTranscript()),
                parseStringList(note.getTodoJson()),
                Collections.emptyList()
        );
        result.put(
                "structuredSections",
                buildStructuredSections(
                        note.getSceneType(),
                        note.getTitle(),
                        splitSentences(note.getFullTranscript()),
                        parseStringList(note.getKeywordsJson()),
                        parseStringList(note.getTodoJson()),
                        Collections.emptyList()
                )
        );
        result.put("roleInsights", analysis.roleInsights);
        result.put("todoChains", analysis.todoChains);
        result.put("decisionInsights", analysis.decisionInsights);
        result.put("fullTranscript", note.getFullTranscript());
        result.put("speakerTranscript", "");
        result.put("speakerBlocks", Collections.emptyList());
        result.put("speakerSegments", Collections.emptyList());
        result.put("status", note.getStatus());
        MeetingProcessingMeta processingMeta = buildProcessingMeta(note, 0);
        result.put("processingStage", processingMeta.stage);
        result.put("processingLabel", processingMeta.label);
        result.put("processingDescription", processingMeta.description);
        result.put("processingPercent", processingMeta.percent);
        result.put("errorMessage", note.getErrorMessage());
        return result;
    }

    private void persistIntermediateResult(
            UserMeetingNote note,
            String transcript,
            String summary,
            List<String> keywords,
            List<String> todos,
            String rawResult
    ) {
        note.setFullTranscript(transcript);
        note.setSummaryText(summary);
        note.setKeywordsJson(toJson(keywords));
        note.setTodoJson(toJson(todos));
        note.setRawResult(rawResult);
        note.setStatus("PROCESSING");
        note.setErrorMessage(null);
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
    }

    private List<UserMeetingSegmentVO> buildSpeakerSegments(
            Integer userId,
            UserMeetingNote note,
            byte[] rawBytes,
            String originalFilename,
            String originalContentType,
            List<Integer> selectedProfileIds,
            boolean autoDiarization,
            Integer batchSizeS,
            String hotword
    ) throws Exception {
        List<UserSpeakerProfile> profiles = loadSpeakerProfiles(userId, selectedProfileIds);
        Map<Integer, byte[]> sampleAudioBytes = new LinkedHashMap<>();
        for (UserSpeakerProfile profile : profiles) {
            sampleAudioBytes.put(profile.getId(), readObjectBytes(profile.getSampleBucket(), profile.getSampleObject()));
        }

        Path tempDir = Files.createTempDirectory("meeting-note-");
        try {
            String extension = resolveExtension(originalFilename, originalContentType);
            Path inputPath = tempDir.resolve("source" + extension);
            Files.write(inputPath, rawBytes);

            List<TimeRange> ranges = detectSpeechRanges(inputPath);
            if (ranges.isEmpty()) {
                ranges = List.of(new TimeRange(0d, Math.max(1d, probeDurationSeconds(inputPath))));
            }

            List<UserMeetingSegmentVO> segments = new ArrayList<>();
            SpeakerDiarizationAdapter.Session anonymousSession = speakerDiarizationAdapter.openSession();
            int segmentIndex = 1;
            for (TimeRange range : ranges) {
                Path segmentPath = tempDir.resolve("segment_" + segmentIndex + ".wav");
                cutSegment(inputPath, range, segmentPath);
                byte[] segmentBytes = Files.readAllBytes(segmentPath);
                if (segmentBytes.length == 0) {
                    segmentIndex++;
                    continue;
                }

                MultipartFile segmentFile = new InMemoryMultipartFile(
                        "file",
                        "segment_" + segmentIndex + ".wav",
                        "audio/wav",
                        segmentBytes
                );

                JsonNode segmentResponse = funasrService.transcribeAudio(segmentFile, batchSizeS, hotword);
                String segmentTranscript = extractAsrText(segmentResponse);
                if (!StringUtils.hasText(segmentTranscript)) {
                    segmentIndex++;
                    continue;
                }

                SpeakerMatch bestMatch = matchSpeaker(segmentBytes, profiles, sampleAudioBytes);
                if (autoDiarization && bestMatch.profileId == null) {
                    SpeakerDiarizationAdapter.Assignment assignment = anonymousSession.assign(segmentBytes);
                    bestMatch = new SpeakerMatch(null, assignment.getClusterLabel(), assignment.getConfidence());
                }
                UserMeetingSegment segment = new UserMeetingSegment();
                segment.setMeetingId(note.getId());
                segment.setSegmentIndex(segmentIndex);
                segment.setStartMs(Math.round(range.startSeconds * 1000));
                segment.setEndMs(Math.round(range.endSeconds * 1000));
                segment.setSpeakerProfileId(bestMatch.profileId);
                segment.setSpeakerName(bestMatch.speakerName);
                segment.setMatchScore(bestMatch.score);
                segment.setTranscript(segmentTranscript);
                segment.setSegmentFilename("segment_" + segmentIndex + ".wav");
                segment.setSegmentContentType("audio/wav");
                segment.setSegmentFileSize((long) segmentBytes.length);
                segment.setCreateTime(new Date());
                segment.setUpdateTime(new Date());
                userMeetingSegmentMapper.insert(segment);
                uploadSegmentAudio(segment, userId, segmentBytes);
                segments.add(toSegmentView(segment));
                segmentIndex++;
            }

            segments.sort(Comparator.comparing(UserMeetingSegmentVO::getSegmentIndex));
            return segments;
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private List<UserSpeakerProfile> loadSpeakerProfiles(Integer userId, List<Integer> profileIds) {
        if (profileIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserSpeakerProfile> profiles = userSpeakerProfileMapper.selectBatchIds(profileIds);
        List<UserSpeakerProfile> result = new ArrayList<>();
        for (UserSpeakerProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            if (!userId.equals(profile.getUid())) {
                continue;
            }
            if (!"ACTIVE".equals(profile.getStatus())) {
                continue;
            }
            if (!StringUtils.hasText(profile.getSampleBucket()) || !StringUtils.hasText(profile.getSampleObject())) {
                continue;
            }
            result.add(profile);
        }
        return result;
    }

    private SpeakerMatch matchSpeaker(byte[] segmentBytes, List<UserSpeakerProfile> profiles, Map<Integer, byte[]> sampleAudioBytes) throws IOException {
        SpeakerMatch bestMatch = SpeakerMatch.unknown();
        MultipartFile segmentFile = new InMemoryMultipartFile("file2", "segment.wav", "audio/wav", segmentBytes);
        for (UserSpeakerProfile profile : profiles) {
            byte[] sampleBytes = sampleAudioBytes.get(profile.getId());
            if (sampleBytes == null || sampleBytes.length == 0) {
                continue;
            }
            MultipartFile sampleFile = new InMemoryMultipartFile("file1", safeFilename(profile.getSampleFilename()), profile.getSampleContentType(), sampleBytes);
            VoiceprintCompareResult compareResult = voiceprintService.compare(sampleFile, segmentFile);
            if (compareResult.getScore() == null) {
                continue;
            }
            if (bestMatch.score == null || compareResult.getScore().compareTo(bestMatch.score) > 0) {
                boolean accepted = Boolean.TRUE.equals(compareResult.getSamePerson());
                String speakerName = accepted ? profile.getSpeakerName() : "未知发言人";
                Integer profileId = accepted ? profile.getId() : null;
                bestMatch = new SpeakerMatch(profileId, speakerName, compareResult.getScore());
            }
        }
        return bestMatch;
    }

    private SpeakerMatch assignAnonymousSpeaker(byte[] segmentBytes, List<SpeakerCluster> clusters) throws IOException {
        if (clusters.isEmpty()) {
            SpeakerCluster cluster = new SpeakerCluster(1, "说话人 1", segmentBytes);
            clusters.add(cluster);
            return new SpeakerMatch(null, cluster.label, null);
        }

        SpeakerCluster bestAcceptedCluster = null;
        BigDecimal bestAcceptedScore = null;
        BigDecimal bestObservedScore = null;
        MultipartFile segmentFile = new InMemoryMultipartFile("file2", "segment.wav", "audio/wav", segmentBytes);
        for (SpeakerCluster cluster : clusters) {
            MultipartFile sampleFile = new InMemoryMultipartFile(
                    "file1",
                    "anonymous_" + cluster.index + ".wav",
                    "audio/wav",
                    cluster.representativeBytes
            );
            VoiceprintCompareResult compareResult = voiceprintService.compare(sampleFile, segmentFile);
            if (compareResult.getScore() == null) {
                continue;
            }
            if (bestObservedScore == null || compareResult.getScore().compareTo(bestObservedScore) > 0) {
                bestObservedScore = compareResult.getScore();
            }
            if (isAnonymousSpeakerAccepted(compareResult)
                    && (bestAcceptedScore == null || compareResult.getScore().compareTo(bestAcceptedScore) > 0)) {
                bestAcceptedCluster = cluster;
                bestAcceptedScore = compareResult.getScore();
            }
        }

        if (bestAcceptedCluster != null) {
            return new SpeakerMatch(null, bestAcceptedCluster.label, bestAcceptedScore);
        }

        SpeakerCluster cluster = new SpeakerCluster(
                clusters.size() + 1,
                "说话人 " + (clusters.size() + 1),
                segmentBytes
        );
        clusters.add(cluster);
        return new SpeakerMatch(null, cluster.label, bestObservedScore);
    }

    private boolean isAnonymousSpeakerAccepted(VoiceprintCompareResult compareResult) {
        if (Boolean.TRUE.equals(compareResult.getSamePerson())) {
            return true;
        }
        return compareResult.getScore() != null
                && compareResult.getScore().compareTo(ANONYMOUS_SPEAKER_THRESHOLD) >= 0;
    }

    private List<TimeRange> detectSpeechRanges(Path inputPath) throws Exception {
        double duration = probeDurationSeconds(inputPath);
        if (duration <= 0d) {
            return Collections.emptyList();
        }

        List<TimeRange> silences = detectSilenceRanges(inputPath);
        if (silences.isEmpty()) {
            return splitLongRange(new TimeRange(0d, duration));
        }

        List<TimeRange> result = new ArrayList<>();
        double currentStart = 0d;
        for (TimeRange silence : silences) {
            double speechEnd = Math.max(currentStart, silence.startSeconds);
            if (speechEnd - currentStart >= MIN_SEGMENT_SECONDS) {
                result.addAll(splitLongRange(new TimeRange(currentStart, speechEnd)));
            }
            currentStart = Math.max(currentStart, silence.endSeconds);
        }
        if (duration - currentStart >= MIN_SEGMENT_SECONDS) {
            result.addAll(splitLongRange(new TimeRange(currentStart, duration)));
        }
        if (result.isEmpty()) {
            result.addAll(splitLongRange(new TimeRange(0d, duration)));
        }
        return result;
    }

    private List<TimeRange> detectSilenceRanges(Path inputPath) throws Exception {
        ProcessResult processResult = runCommand(List.of(
                "ffmpeg",
                "-i", inputPath.toString(),
                "-af", "silencedetect=noise=-30dB:d=0.4",
                "-f", "null",
                "-"
        ));
        String output = processResult.output;
        List<Double> silenceStarts = new ArrayList<>();
        List<Double> silenceEnds = new ArrayList<>();

        Matcher startMatcher = SILENCE_START_PATTERN.matcher(output);
        while (startMatcher.find()) {
            silenceStarts.add(Double.parseDouble(startMatcher.group(1)));
        }

        Matcher endMatcher = SILENCE_END_PATTERN.matcher(output);
        while (endMatcher.find()) {
            silenceEnds.add(Double.parseDouble(endMatcher.group(1)));
        }

        List<TimeRange> silences = new ArrayList<>();
        int count = Math.min(silenceStarts.size(), silenceEnds.size());
        for (int index = 0; index < count; index++) {
            double start = silenceStarts.get(index);
            double end = silenceEnds.get(index);
            if (end > start) {
                silences.add(new TimeRange(start, end));
            }
        }
        silences.sort(Comparator.comparing(range -> range.startSeconds));
        return silences;
    }

    private List<TimeRange> splitLongRange(TimeRange range) {
        if (range.duration() <= MAX_SEGMENT_SECONDS) {
            return List.of(range);
        }
        List<TimeRange> result = new ArrayList<>();
        double cursor = range.startSeconds;
        while (cursor < range.endSeconds) {
            double end = Math.min(cursor + MAX_SEGMENT_SECONDS, range.endSeconds);
            if (end - cursor >= MIN_SEGMENT_SECONDS) {
                result.add(new TimeRange(cursor, end));
            }
            cursor = end;
        }
        return result;
    }

    private double probeDurationSeconds(Path inputPath) throws Exception {
        ProcessResult processResult = runCommand(List.of(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=nw=1:nk=1",
                inputPath.toString()
        ));
        String value = processResult.output.trim();
        if (!StringUtils.hasText(value)) {
            return 0d;
        }
        return Double.parseDouble(value);
    }

    private void cutSegment(Path inputPath, TimeRange range, Path outputPath) throws Exception {
        runCommand(List.of(
                "ffmpeg",
                "-y",
                "-i", inputPath.toString(),
                "-ss", String.valueOf(range.startSeconds),
                "-to", String.valueOf(range.endSeconds),
                "-ac", "1",
                "-ar", "16000",
                outputPath.toString()
        ));
    }

    private ProcessResult runCommand(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output;
        try (InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("命令执行失败: " + String.join(" ", command) + "\n" + output);
        }
        return new ProcessResult(exitCode, output);
    }

    private void uploadSegmentAudio(UserMeetingSegment segment, Integer userId, byte[] bytes) throws Exception {
        String object = buildSegmentObjectPath(userId, segment.getMeetingId(), segment.getSegmentIndex(), segment.getSegmentFilename());
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioInfo.getBucket())
                        .object(object)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .contentType("audio/wav")
                        .build()
        );
        segment.setSegmentBucket(minioInfo.getBucket());
        segment.setSegmentObject(object);
        segment.setSegmentFileSize((long) bytes.length);
        segment.setUpdateTime(new Date());
        userMeetingSegmentMapper.updateById(segment);
    }

    private byte[] readObjectBytes(String bucket, String object) throws Exception {
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(object)
                        .build()
        )) {
            return response.readAllBytes();
        }
    }

    private void markFailed(UserMeetingNote note, Exception ex) {
        note.setStatus("FAILED");
        note.setErrorMessage(truncate(ex.getMessage(), 1000));
        note.setUpdateTime(new Date());
        userMeetingNoteMapper.updateById(note);
    }

    private UserMeetingNote getMeetingById(Integer meetingId, Integer userId) {
        if (meetingId == null) {
            throw new IllegalArgumentException("meetingId 不能为空");
        }
        UserMeetingNote note = userMeetingNoteMapper.selectById(meetingId);
        if (note == null) {
            throw new IllegalArgumentException("纪要记录不存在");
        }
        requireUser(userId);
        if (!userId.equals(note.getUid())) {
            throw new IllegalArgumentException("无权查看其他用户的纪要记录");
        }
        return note;
    }

    private UserMeetingSegment getSegmentById(Integer segmentId) {
        if (segmentId == null) {
            throw new IllegalArgumentException("segmentId 不能为空");
        }
        UserMeetingSegment segment = userMeetingSegmentMapper.selectById(segmentId);
        if (segment == null) {
            throw new IllegalArgumentException("纪要片段不存在");
        }
        return segment;
    }

    private String buildRawObjectPath(Integer userId, String filename) {
        return "user-audio/" + userId
                + "/meeting/raw/"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + "_"
                + sanitizeFilename(filename);
    }

    private String buildSegmentObjectPath(Integer userId, Integer meetingId, Integer segmentIndex, String filename) {
        return "user-audio/" + userId
                + "/meeting/segment/"
                + meetingId
                + "/"
                + String.format(Locale.ROOT, "%03d", segmentIndex)
                + "_"
                + sanitizeFilename(filename);
    }

    private String sanitizeFilename(String filename) {
        return safeFilename(filename).replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String safeFilename(String filename) {
        return StringUtils.hasText(filename) ? filename : "meeting_audio.wav";
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }

    private String resolveExtension(String filename, String contentType) {
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        if (StringUtils.hasText(contentType) && contentType.contains("mpeg")) {
            return ".mp3";
        }
        return ".wav";
    }

    private String extractAsrText(JsonNode response) {
        JsonNode transcriptionNode = response.path("transcription");
        if (transcriptionNode.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode item : transcriptionNode) {
                String text = item.path("text").asText("");
                if (StringUtils.hasText(text)) {
                    texts.add(text.trim());
                }
            }
            return String.join("\n", texts);
        }
        String text = response.path("text").asText("");
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private List<String> splitSentences(String transcript) {
        if (!StringUtils.hasText(transcript)) {
            return Collections.emptyList();
        }
        String[] parts = SENTENCE_SPLIT_PATTERN.split(transcript);
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String normalized = part.trim();
            if (normalized.length() >= 4) {
                sentences.add(normalized);
            }
        }
        return sentences;
    }

    private String buildSummary(String sceneType, String title, List<String> sentences, List<String> todos) {
        if (sentences.isEmpty()) {
            return "当前音频已完成转写，但没有提取到足够的有效句子，建议检查录音质量后重试。";
        }
        List<String> summaryLines = new ArrayList<>();
        String scenePrefix = "classroom".equals(sceneType) ? "课堂内容聚焦于：" : "本次讨论主要围绕：";
        summaryLines.add(scenePrefix + joinTopSentences(sentences, 2));
        if (sentences.size() > 2) {
            summaryLines.add("补充要点：" + joinTopSentences(sentences.subList(Math.min(2, sentences.size()), sentences.size()), 2));
        }
        if (!todos.isEmpty()) {
            summaryLines.add("系统提取到 " + todos.size() + " 条待办线索，建议在纪要详情中进一步确认。");
        }
        if (StringUtils.hasText(title)) {
            summaryLines.add("纪要标题：" + title);
        }
        return String.join("\n", summaryLines);
    }

    private String joinTopSentences(List<String> sentences, int maxCount) {
        List<String> selected = new ArrayList<>();
        for (String sentence : sentences) {
            if (!StringUtils.hasText(sentence)) {
                continue;
            }
            selected.add(sentence);
            if (selected.size() >= maxCount) {
                break;
            }
        }
        return selected.isEmpty() ? "暂无" : String.join("；", selected);
    }

    private List<String> extractTodos(List<String> sentences) {
        LinkedHashSet<String> todos = new LinkedHashSet<>();
        for (String sentence : sentences) {
            for (String marker : TODO_MARKERS) {
                if (sentence.contains(marker)) {
                    todos.add(sentence);
                    break;
                }
            }
            if (todos.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(todos);
    }

    private List<String> extractKeywords(List<String> sentences) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String sentence : sentences) {
            String cleaned = NON_CHINESE_PATTERN.matcher(sentence).replaceAll("");
            if (cleaned.length() < 2) {
                continue;
            }
            int maxLength = Math.min(4, cleaned.length());
            for (int size = 2; size <= maxLength; size++) {
                for (int i = 0; i + size <= cleaned.length(); i++) {
                    String token = cleaned.substring(i, i + size);
                    if (isInvalidKeyword(token)) {
                        continue;
                    }
                    counter.merge(token, 1, Integer::sum);
                }
            }
        }
        return counter.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted((left, right) -> {
                    int compareCount = Integer.compare(right.getValue(), left.getValue());
                    if (compareCount != 0) {
                        return compareCount;
                    }
                    return Integer.compare(right.getKey().length(), left.getKey().length());
                })
                .map(Map.Entry::getKey)
                .distinct()
                .limit(8)
                .toList();
    }

    private boolean isInvalidKeyword(String token) {
        if (!StringUtils.hasText(token) || token.length() < 2) {
            return true;
        }
        for (String stopWord : KEYWORD_STOP_WORDS) {
            if (stopWord.contains(token) || token.contains(stopWord)) {
                return true;
            }
        }
        return false;
    }

    private List<UserMeetingInsightSectionVO> buildStructuredSections(
            String sceneType,
            String title,
            List<String> sentences,
            List<String> keywords,
            List<String> todos,
            List<UserMeetingSpeakerBlockVO> speakerBlocks
    ) {
        List<String> normalizedSentences = takeTopDistinct(sentences, 8);
        List<UserMeetingInsightSectionVO> sections = new ArrayList<>();
        boolean classroom = "classroom".equals(sceneType);

        if (classroom) {
            sections.add(createInsightSection(
                    "lesson_highlights",
                    "课堂重点",
                    "帮你快速抓住这节课最值得保留的内容。",
                    buildClassroomHighlightItems(title, normalizedSentences, keywords)
            ));
            sections.add(createInsightSection(
                    "interaction",
                    "互动问答",
                    "优先抽出课堂里出现提问、解释和回应的片段。",
                    buildInteractionItems(normalizedSentences)
            ));
            sections.add(createInsightSection(
                    "after_class",
                    "课后提醒",
                    "把练习、作业、复习等线索单独整理，方便后续跟进。",
                    buildAfterClassItems(normalizedSentences, todos)
            ));
        } else {
            sections.add(createInsightSection(
                    "discussion_focus",
                    "核心议题",
                    "把本次会议最集中的讨论主题单独拎出来。",
                    buildMeetingFocusItems(title, normalizedSentences, keywords)
            ));
            sections.add(createInsightSection(
                    "decision",
                    "决议与结论",
                    "优先提取确定、安排、统一等有结果导向的内容。",
                    buildDecisionItems(normalizedSentences)
            ));
            sections.add(createInsightSection(
                    "follow_up",
                    "后续动作",
                    "把需要继续推进的动作项与责任线索沉淀下来。",
                    buildFollowUpItems(normalizedSentences, todos)
            ));
        }

        if (speakerBlocks != null && !speakerBlocks.isEmpty()) {
            sections.add(createInsightSection(
                    "speaker_overview",
                    "发言分布",
                    "帮助快速判断是谁在主导发言、谁给出补充信息。",
                    buildSpeakerOverviewItems(speakerBlocks)
            ));
        }

        return sections;
    }

    private UserMeetingInsightSectionVO createInsightSection(String key, String title, String subtitle, List<String> items) {
        UserMeetingInsightSectionVO section = new UserMeetingInsightSectionVO();
        section.setKey(key);
        section.setTitle(title);
        section.setSubtitle(subtitle);
        section.setItems(items == null ? Collections.emptyList() : items);
        return section;
    }

    private List<String> buildMeetingFocusItems(String title, List<String> sentences, List<String> keywords) {
        List<String> items = new ArrayList<>();
        if (StringUtils.hasText(title)) {
            items.add("本次纪要围绕「" + title.trim() + "」展开。");
        }
        if (keywords != null && !keywords.isEmpty()) {
            items.add("关键词聚焦：" + String.join(" / ", keywords.subList(0, Math.min(4, keywords.size()))));
        }
        items.addAll(takeTopDistinct(sentences, 2));
        return limitInsightItems(items, 3, "当前录音已完成转写，建议结合全文确认会议议题。");
    }

    private List<String> buildDecisionItems(List<String> sentences) {
        List<String> items = pickSentencesByMarkers(sentences, MEETING_DECISION_MARKERS, 3);
        return limitInsightItems(items, 3, "暂未抽取到明显的决议语句，建议在详情页结合全文人工确认。");
    }

    private List<String> buildFollowUpItems(List<String> sentences, List<String> todos) {
        List<String> items = new ArrayList<>();
        if (todos != null && !todos.isEmpty()) {
            items.addAll(todos.subList(0, Math.min(3, todos.size())));
        }
        if (items.isEmpty()) {
            items.addAll(pickSentencesByMarkers(sentences, TODO_MARKERS, 3));
        }
        return limitInsightItems(items, 3, "当前未抽取到明确待办，可在人工校正时补充后续动作。");
    }

    private List<String> buildClassroomHighlightItems(String title, List<String> sentences, List<String> keywords) {
        List<String> items = new ArrayList<>();
        if (StringUtils.hasText(title)) {
            items.add("本节课程主题为「" + title.trim() + "」。");
        }
        if (keywords != null && !keywords.isEmpty()) {
            items.add("知识点聚焦：" + String.join(" / ", keywords.subList(0, Math.min(4, keywords.size()))));
        }
        items.addAll(takeTopDistinct(sentences, 2));
        return limitInsightItems(items, 3, "当前录音已完成转写，建议结合全文进一步整理课堂重点。");
    }

    private List<String> buildInteractionItems(List<String> sentences) {
        List<String> items = pickSentencesByMarkers(sentences, CLASSROOM_INTERACTION_MARKERS, 3);
        return limitInsightItems(items, 3, "本次录音以连续讲解为主，暂未识别到明显的课堂互动片段。");
    }

    private List<String> buildAfterClassItems(List<String> sentences, List<String> todos) {
        List<String> items = new ArrayList<>();
        if (todos != null && !todos.isEmpty()) {
            items.addAll(todos.subList(0, Math.min(3, todos.size())));
        }
        if (items.isEmpty()) {
            items.addAll(pickSentencesByMarkers(sentences, CLASSROOM_REVIEW_MARKERS, 3));
        }
        return limitInsightItems(items, 3, "暂未抽取到明确作业或复习提醒，可在校正后补充课后安排。");
    }

    private List<String> buildSpeakerOverviewItems(List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        List<String> items = new ArrayList<>();
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            if (block == null || !StringUtils.hasText(block.getTranscript())) {
                continue;
            }
            String speakerName = StringUtils.hasText(block.getSpeakerName()) ? block.getSpeakerName().trim() : "未知发言人";
            String transcript = block.getTranscript().trim();
            if (transcript.length() > 36) {
                transcript = transcript.substring(0, 36) + "...";
            }
            items.add(speakerName + "（" + (block.getSegmentCount() == null ? 1 : block.getSegmentCount()) + "段）：" + transcript);
            if (items.size() >= 3) {
                break;
            }
        }
        return limitInsightItems(items, 3, "发言片段将在识别完成后展示在这里。");
    }

    private List<String> pickSentencesByMarkers(List<String> sentences, List<String> markers, int maxItems) {
        if (sentences == null || sentences.isEmpty() || markers == null || markers.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        for (String sentence : sentences) {
            if (!StringUtils.hasText(sentence)) {
                continue;
            }
            for (String marker : markers) {
                if (sentence.contains(marker)) {
                    items.add(sentence.trim());
                    break;
                }
            }
            if (items.size() >= maxItems) {
                break;
            }
        }
        return takeTopDistinct(items, maxItems);
    }

    private List<String> takeTopDistinct(List<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            items.add(value.trim());
            if (items.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(items);
    }

    private List<String> limitInsightItems(List<String> items, int maxItems, String fallback) {
        List<String> normalized = takeTopDistinct(items, maxItems);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return Collections.singletonList(fallback);
    }

    private MeetingAnalysisBundle buildMeetingAnalysis(
            List<String> sentences,
            List<String> todos,
            List<UserMeetingSpeakerBlockVO> speakerBlocks
    ) {
        List<String> normalizedSentences = takeTopDistinct(sentences, 12);
        List<String> normalizedTodos = takeTopDistinct(todos, 8);
        List<UserMeetingRoleInsightVO> roleInsights = buildRoleInsights(speakerBlocks, normalizedTodos);
        List<UserMeetingTodoChainVO> todoChains = buildTodoChains(normalizedTodos, normalizedSentences, speakerBlocks);
        List<UserMeetingDecisionInsightVO> decisionInsights = buildDecisionInsights(normalizedSentences, speakerBlocks);
        return new MeetingAnalysisBundle(roleInsights, todoChains, decisionInsights);
    }

    private List<UserMeetingRoleInsightVO> buildRoleInsights(
            List<UserMeetingSpeakerBlockVO> speakerBlocks,
            List<String> todos
    ) {
        if (speakerBlocks == null || speakerBlocks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, SpeakerRoleStats> statsMap = new LinkedHashMap<>();
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            if (block == null || !StringUtils.hasText(block.getTranscript())) {
                continue;
            }
            String speakerName = normalizeSpeakerName(block.getSpeakerName());
            if (!StringUtils.hasText(speakerName) || "未知发言人".equals(speakerName)) {
                continue;
            }
            SpeakerRoleStats stats = statsMap.computeIfAbsent(speakerName, SpeakerRoleStats::new);
            String transcript = block.getTranscript().trim();
            stats.segmentCount += Math.max(1, block.getSegmentCount() == null ? 1 : block.getSegmentCount());
            stats.transcriptLength += transcript.length();
            stats.questionCount += countMarkerMatches(transcript, ROLE_QUESTION_MARKERS);
            stats.responseCount += countMarkerMatches(transcript, ROLE_RESPONSE_MARKERS);
            stats.hostCount += countMarkerMatches(transcript, ROLE_HOST_MARKERS);
            if (!StringUtils.hasText(stats.hostEvidence) && containsAnyMarker(transcript, ROLE_HOST_MARKERS)) {
                stats.hostEvidence = shortenText(transcript, 40);
            }
            if (!StringUtils.hasText(stats.questionEvidence) && containsAnyMarker(transcript, ROLE_QUESTION_MARKERS)) {
                stats.questionEvidence = shortenText(transcript, 40);
            }
            if (!StringUtils.hasText(stats.responseEvidence) && containsAnyMarker(transcript, ROLE_RESPONSE_MARKERS)) {
                stats.responseEvidence = shortenText(transcript, 40);
            }
        }

        if (statsMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserMeetingRoleInsightVO> result = new ArrayList<>();
        SpeakerRoleStats host = statsMap.values().stream()
                .max(Comparator.comparingInt((SpeakerRoleStats item) -> item.hostCount)
                        .thenComparingInt(item -> item.transcriptLength))
                .orElse(null);
        if (host != null) {
            result.add(createRoleInsight(
                    "host",
                    "主讲人",
                    host.speakerName,
                    StringUtils.hasText(host.hostEvidence) ? host.hostEvidence : "该发言人拥有最长的连续说明内容。",
                    "主导 " + host.segmentCount + " 段发言，整体讲解与推进最集中。"
            ));
        }

        SpeakerRoleStats questioner = statsMap.values().stream()
                .filter(item -> item.questionCount > 0)
                .max(Comparator.comparingInt((SpeakerRoleStats item) -> item.questionCount)
                        .thenComparingInt(item -> item.segmentCount))
                .orElse(null);
        if (questioner != null) {
            result.add(createRoleInsight(
                    "questioner",
                    "提问者",
                    questioner.speakerName,
                    StringUtils.hasText(questioner.questionEvidence) ? questioner.questionEvidence : "这位发言人提出了本次讨论中的关键问题。",
                    "识别到 " + questioner.questionCount + " 处提问线索，适合归入问题发起者。"
            ));
        }

        SpeakerRoleStats responder = statsMap.values().stream()
                .filter(item -> item.responseCount > 0)
                .max(Comparator.comparingInt((SpeakerRoleStats item) -> item.responseCount)
                        .thenComparingInt(item -> item.segmentCount))
                .orElse(null);
        if (responder != null) {
            result.add(createRoleInsight(
                    "responder",
                    "回应者",
                    responder.speakerName,
                    StringUtils.hasText(responder.responseEvidence) ? responder.responseEvidence : "这位发言人主要承担回答与解释的作用。",
                    "识别到 " + responder.responseCount + " 处回应线索，主要承担解释与确认。"
            ));
        }

        Set<String> knownSpeakers = statsMap.keySet();
        for (String todo : todos) {
            String owner = extractOwner(todo, knownSpeakers);
            if (StringUtils.hasText(owner)) {
                result.add(createRoleInsight(
                        "assignee",
                        "被分配任务者",
                        owner,
                        shortenText(todo, 42),
                        "该发言人被待办线索直接点名，适合纳入任务承接人。"
                ));
                break;
            }
        }

        return takeDistinctRoleInsights(result, 4);
    }

    private UserMeetingRoleInsightVO createRoleInsight(
            String roleKey,
            String roleLabel,
            String speakerName,
            String evidence,
            String contribution
    ) {
        UserMeetingRoleInsightVO item = new UserMeetingRoleInsightVO();
        item.setRoleKey(roleKey);
        item.setRoleLabel(roleLabel);
        item.setSpeakerName(speakerName);
        item.setEvidence(evidence);
        item.setContribution(contribution);
        return item;
    }

    private List<UserMeetingRoleInsightVO> takeDistinctRoleInsights(List<UserMeetingRoleInsightVO> items, int limit) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserMeetingRoleInsightVO> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (UserMeetingRoleInsightVO item : items) {
            if (item == null || !StringUtils.hasText(item.getRoleKey()) || !StringUtils.hasText(item.getSpeakerName())) {
                continue;
            }
            String uniqueKey = item.getRoleKey() + "@" + item.getSpeakerName();
            if (keys.add(uniqueKey)) {
                result.add(item);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<UserMeetingTodoChainVO> buildTodoChains(
            List<String> todos,
            List<String> sentences,
            List<UserMeetingSpeakerBlockVO> speakerBlocks
    ) {
        List<String> sources = !todos.isEmpty() ? todos : pickSentencesByMarkers(sentences, TODO_MARKERS, 6);
        if (sources.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> knownSpeakers = collectSpeakerNames(speakerBlocks);
        List<UserMeetingTodoChainVO> result = new ArrayList<>();
        for (String task : sources) {
            if (!StringUtils.hasText(task)) {
                continue;
            }
            String normalizedTask = task.trim();
            String owner = extractOwner(normalizedTask, knownSpeakers);
            String deadline = extractDeadline(normalizedTask);

            UserMeetingTodoChainVO item = new UserMeetingTodoChainVO();
            item.setTaskText(normalizedTask);
            item.setOwner(StringUtils.hasText(owner) ? owner : "待确认");
            item.setDeadline(StringUtils.hasText(deadline) ? deadline : "待补充");
            item.setAction(shortenText(normalizedTask, 52));
            if (StringUtils.hasText(owner) && StringUtils.hasText(deadline)) {
                item.setStatusKey("complete");
                item.setStatusLabel("完整待办");
            } else if (StringUtils.hasText(owner) || StringUtils.hasText(deadline)) {
                item.setStatusKey("partial");
                item.setStatusLabel("待补全");
            } else {
                item.setStatusKey("pending");
                item.setStatusLabel("待确认");
            }
            result.add(item);
            if (result.size() >= 6) {
                break;
            }
        }
        return result;
    }

    private List<UserMeetingDecisionInsightVO> buildDecisionInsights(
            List<String> sentences,
            List<UserMeetingSpeakerBlockVO> speakerBlocks
    ) {
        if (sentences == null || sentences.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserMeetingDecisionInsightVO> result = new ArrayList<>();
        for (String sentence : pickSentencesByMarkers(sentences, MEETING_DECISION_MARKERS, 3)) {
            result.add(createDecisionInsight("confirmed", "已确认结论", sentence, resolveSpeakerFromSentence(sentence, speakerBlocks)));
        }
        for (String sentence : pickSentencesByMarkers(sentences, PENDING_DECISION_MARKERS, 3)) {
            result.add(createDecisionInsight("pending", "待确认事项", sentence, resolveSpeakerFromSentence(sentence, speakerBlocks)));
        }
        return result;
    }

    private UserMeetingDecisionInsightVO createDecisionInsight(
            String typeKey,
            String typeLabel,
            String content,
            String sourceSpeaker
    ) {
        UserMeetingDecisionInsightVO item = new UserMeetingDecisionInsightVO();
        item.setTypeKey(typeKey);
        item.setTypeLabel(typeLabel);
        item.setContent(shortenText(content, 68));
        item.setSourceSpeaker(sourceSpeaker);
        return item;
    }

    private Set<String> collectSpeakerNames(List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        if (speakerBlocks == null || speakerBlocks.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            String speakerName = normalizeSpeakerName(block == null ? null : block.getSpeakerName());
            if (StringUtils.hasText(speakerName) && !"未知发言人".equals(speakerName)) {
                result.add(speakerName);
            }
        }
        return result;
    }

    private int countMarkerMatches(String text, List<String> markers) {
        int count = 0;
        if (!StringUtils.hasText(text) || markers == null) {
            return count;
        }
        for (String marker : markers) {
            if (StringUtils.hasText(marker) && text.contains(marker)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAnyMarker(String text, List<String> markers) {
        return countMarkerMatches(text, markers) > 0;
    }

    private String extractOwner(String task, Set<String> knownSpeakers) {
        if (!StringUtils.hasText(task)) {
            return null;
        }
        Matcher matcher = OWNER_ACTION_PATTERN.matcher(task);
        if (matcher.find()) {
            return normalizeManualSpeakerName(matcher.group(2));
        }
        if (knownSpeakers != null) {
            for (String speakerName : knownSpeakers) {
                if (StringUtils.hasText(speakerName) && task.contains(speakerName)) {
                    return speakerName;
                }
            }
        }
        return null;
    }

    private String extractDeadline(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = DEADLINE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String resolveSpeakerFromSentence(String sentence, List<UserMeetingSpeakerBlockVO> speakerBlocks) {
        if (!StringUtils.hasText(sentence) || speakerBlocks == null || speakerBlocks.isEmpty()) {
            return null;
        }
        for (UserMeetingSpeakerBlockVO block : speakerBlocks) {
            if (block == null || !StringUtils.hasText(block.getTranscript())) {
                continue;
            }
            if (block.getTranscript().contains(sentence) || sentence.contains(shortenText(block.getTranscript(), 18))) {
                return normalizeSpeakerName(block.getSpeakerName());
            }
        }
        return null;
    }

    private String shortenText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private MeetingProcessingMeta buildProcessingMeta(UserMeetingNote note, int segmentCount) {
        String status = StringUtils.hasText(note.getStatus()) ? note.getStatus().trim().toUpperCase(Locale.ROOT) : "PENDING";
        boolean hasTranscript = StringUtils.hasText(note.getFullTranscript());
        boolean hasSummary = StringUtils.hasText(note.getSummaryText());
        boolean hasSpeakerMatch = !parseSelectedSpeakerIds(note.getSelectedSpeakerIdsJson()).isEmpty();

        return switch (status) {
            case "FAILED" -> new MeetingProcessingMeta(
                    "FAILED",
                    "处理失败",
                    StringUtils.hasText(note.getErrorMessage()) ? note.getErrorMessage() : "后台处理未成功完成，建议检查音频或稍后重试。",
                    100
            );
            case "SUCCESS" -> new MeetingProcessingMeta(
                    "SUCCESS",
                    "已完成",
                    "纪要已生成完成，支持查看结构化结果、时间轴、校正与导出。",
                    100
            );
            case "UPLOADED" -> new MeetingProcessingMeta(
                    "UPLOADED",
                    "音频已接收",
                    "原始音频已经入库，系统正在准备启动转写任务。",
                    22
            );
            case "PROCESSING" -> {
                if (!hasTranscript) {
                    yield new MeetingProcessingMeta(
                            "TRANSCRIBING",
                            "正在转写",
                            "系统正在调用语音识别模型生成全文文本，请稍等片刻。",
                            48
                    );
                }
                if (hasSpeakerMatch && segmentCount <= 0) {
                    yield new MeetingProcessingMeta(
                            "SPEAKER_MATCHING",
                            "正在识别发言人",
                            "全文转写和摘要已准备完成，正在切分语音片段并匹配发言人。",
                            84
                    );
                }
                if (hasSpeakerMatch) {
                    yield new MeetingProcessingMeta(
                            "FINALIZING",
                            "正在整理纪要",
                            "片段识别已完成，系统正在合并发言块并整理最终纪要结构。",
                            92
                    );
                }
                if (hasSummary) {
                    yield new MeetingProcessingMeta(
                            "STRUCTURING",
                            "正在整理纪要",
                            "系统已完成转写，正在提炼摘要、关键词和待办事项。",
                            78
                    );
                }
                yield new MeetingProcessingMeta(
                        "PROCESSING",
                        "后台处理中",
                        "任务已进入处理流程，系统正在整理结果，请稍后刷新。",
                        64
                );
            }
            default -> new MeetingProcessingMeta(
                    "QUEUED",
                    "等待排队",
                    "任务已创建，正在等待后台接管处理。",
                    8
            );
        };
    }

    private String buildSpeakerTranscript(List<UserMeetingSpeakerBlockVO> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (UserMeetingSpeakerBlockVO block : blocks) {
            String speakerName = StringUtils.hasText(block.getSpeakerName()) ? block.getSpeakerName() : "未知发言人";
            String transcript = StringUtils.hasText(block.getTranscript()) ? block.getTranscript() : "";
            lines.add(speakerName + "：" + transcript);
        }
        return String.join("\n", lines);
    }

    private List<UserMeetingSpeakerBlockVO> mergeSpeakerBlocks(List<UserMeetingSegmentVO> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserMeetingSpeakerBlockVO> blocks = new ArrayList<>();
        UserMeetingSpeakerBlockVO current = null;
        for (UserMeetingSegmentVO segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.getTranscript())) {
                continue;
            }
            if (current != null && canMergeSpeakerBlock(current, segment)) {
                current.setEndMs(segment.getEndMs());
                current.setTranscript(mergeTranscript(current.getTranscript(), segment.getTranscript()));
                current.setSegmentCount((current.getSegmentCount() == null ? 1 : current.getSegmentCount()) + 1);
                current.setMatchScore(pickBetterScore(current.getMatchScore(), segment.getMatchScore()));
                continue;
            }

            current = new UserMeetingSpeakerBlockVO();
            current.setSpeakerProfileId(segment.getSpeakerProfileId());
            current.setSpeakerName(StringUtils.hasText(segment.getSpeakerName()) ? segment.getSpeakerName() : "未知发言人");
            current.setMatchScore(segment.getMatchScore());
            current.setStartMs(segment.getStartMs());
            current.setEndMs(segment.getEndMs());
            current.setTranscript(segment.getTranscript());
            current.setSegmentCount(1);
            blocks.add(current);
        }
        return blocks;
    }

    private boolean canMergeSpeakerBlock(UserMeetingSpeakerBlockVO block, UserMeetingSegmentVO segment) {
        if (block == null || segment == null) {
            return false;
        }
        Long blockEnd = block.getEndMs();
        Long nextStart = segment.getStartMs();
        if (blockEnd == null || nextStart == null || nextStart - blockEnd > MERGE_MAX_GAP_MS) {
            return false;
        }
        if (block.getSpeakerProfileId() != null && block.getSpeakerProfileId().equals(segment.getSpeakerProfileId())) {
            return true;
        }
        String currentSpeaker = normalizeSpeakerName(block.getSpeakerName());
        String nextSpeaker = normalizeSpeakerName(segment.getSpeakerName());
        return StringUtils.hasText(currentSpeaker)
                && StringUtils.hasText(nextSpeaker)
                && !"未知发言人".equals(currentSpeaker)
                && currentSpeaker.equals(nextSpeaker);
    }

    private String normalizeSpeakerName(String speakerName) {
        if (!StringUtils.hasText(speakerName)) {
            return "";
        }
        return speakerName.trim();
    }

    private String mergeTranscript(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        String normalizedLeft = left.trim();
        String normalizedRight = right.trim();
        if (normalizedLeft.endsWith("。") || normalizedLeft.endsWith("！") || normalizedLeft.endsWith("？")
                || normalizedLeft.endsWith("!") || normalizedLeft.endsWith("?") || normalizedLeft.endsWith("；")
                || normalizedLeft.endsWith(";")) {
            return normalizedLeft + normalizedRight;
        }
        return normalizedLeft + " " + normalizedRight;
    }

    private java.math.BigDecimal pickBetterScore(java.math.BigDecimal current, java.math.BigDecimal incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        return incoming.compareTo(current) > 0 ? incoming : current;
    }

    private UserMeetingNoteVO toView(UserMeetingNote note, boolean includeSegments) {
        UserMeetingNoteVO view = new UserMeetingNoteVO();
        view.setId(note.getId());
        view.setUserId(note.getUid());
        view.setTitle(note.getTitle());
        view.setSceneType(note.getSceneType());
        view.setRawBucket(note.getRawBucket());
        view.setRawObject(note.getRawObject());
        view.setRawFilename(note.getRawFilename());
        view.setRawContentType(note.getRawContentType());
        view.setRawFileSize(note.getRawFileSize());
        view.setFullTranscript(note.getFullTranscript());
        view.setSummaryText(note.getSummaryText());
        view.setKeywords(parseStringList(note.getKeywordsJson()));
        view.setTodos(parseStringList(note.getTodoJson()));
        view.setStatus(note.getStatus());
        view.setErrorMessage(note.getErrorMessage());
        view.setCreateTime(note.getCreateTime());
        view.setUpdateTime(note.getUpdateTime());
        boolean hasRawAudio = StringUtils.hasText(note.getRawBucket()) && StringUtils.hasText(note.getRawObject());
        view.setHasRawAudio(hasRawAudio);
        view.setRawAudioUrl(hasRawAudio ? "/api/meeting/history/" + note.getId() + "/audio" : null);
        List<UserMeetingSegmentVO> segments = Collections.emptyList();
        List<UserMeetingSpeakerBlockVO> blocks = Collections.emptyList();
        if (includeSegments) {
            segments = listSegmentsByMeetingId(note.getId());
            blocks = mergeSpeakerBlocks(segments);
            view.setSpeakerBlocks(blocks);
            view.setSpeakerSegments(segments);
            view.setSpeakerTranscript(buildSpeakerTranscript(blocks));
        }
        if (!includeSegments) {
            view.setSpeakerBlocks(Collections.emptyList());
            view.setSpeakerSegments(Collections.emptyList());
            view.setSpeakerTranscript("");
        }
        view.setStructuredSections(
                buildStructuredSections(
                        note.getSceneType(),
                        note.getTitle(),
                        splitSentences(note.getFullTranscript()),
                        view.getKeywords(),
                        view.getTodos(),
                        blocks
                )
        );
        MeetingAnalysisBundle analysis = buildMeetingAnalysis(
                splitSentences(note.getFullTranscript()),
                view.getTodos(),
                blocks
        );
        view.setRoleInsights(analysis.roleInsights);
        view.setTodoChains(analysis.todoChains);
        view.setDecisionInsights(analysis.decisionInsights);
        MeetingProcessingMeta processingMeta = buildProcessingMeta(note, segments.size());
        view.setProcessingStage(processingMeta.stage);
        view.setProcessingLabel(processingMeta.label);
        view.setProcessingDescription(processingMeta.description);
        view.setProcessingPercent(processingMeta.percent);
        return view;
    }

    private void saveRevisionSnapshot(
            UserMeetingNote note,
            List<UserMeetingSpeakerBlockVO> speakerBlocks,
            List<UserMeetingSegmentVO> speakerSegments,
            String revisionType
    ) {
        LambdaQueryWrapper<UserMeetingRevision> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(UserMeetingRevision::getMeetingId, note.getId());
        Long count = userMeetingRevisionMapper.selectCount(countWrapper);

        UserMeetingRevision revision = new UserMeetingRevision();
        revision.setMeetingId(note.getId());
        revision.setVersionNo((count == null ? 0 : count.intValue()) + 1);
        revision.setRevisionType(revisionType);
        revision.setTitle(note.getTitle());
        revision.setSummaryText(note.getSummaryText());
        revision.setKeywordsJson(note.getKeywordsJson());
        revision.setTodoJson(note.getTodoJson());
        revision.setFullTranscript(note.getFullTranscript());
        revision.setSpeakerTranscript(buildSpeakerTranscript(speakerBlocks));
        revision.setSpeakerBlocksJson(toJson(speakerBlocks));
        revision.setSpeakerSegmentsJson(toJson(speakerSegments));
        revision.setCreateTime(new Date());
        userMeetingRevisionMapper.insert(revision);
    }

    private UserMeetingRevisionVO toRevisionView(UserMeetingRevision revision) {
        UserMeetingRevisionVO view = new UserMeetingRevisionVO();
        view.setId(revision.getId());
        view.setMeetingId(revision.getMeetingId());
        view.setVersionNo(revision.getVersionNo());
        view.setRevisionType(revision.getRevisionType());
        view.setTitle(revision.getTitle());
        view.setSummaryText(revision.getSummaryText());
        view.setKeywords(parseStringList(revision.getKeywordsJson()));
        view.setTodos(parseStringList(revision.getTodoJson()));
        view.setFullTranscript(revision.getFullTranscript());
        MeetingAnalysisBundle analysis = buildMeetingAnalysis(
                splitSentences(revision.getFullTranscript()),
                view.getTodos(),
                parseSpeakerBlockList(revision.getSpeakerBlocksJson())
        );
        view.setRoleInsights(analysis.roleInsights);
        view.setTodoChains(analysis.todoChains);
        view.setDecisionInsights(analysis.decisionInsights);
        view.setSpeakerTranscript(revision.getSpeakerTranscript());
        view.setSpeakerBlocks(parseSpeakerBlockList(revision.getSpeakerBlocksJson()));
        view.setSpeakerSegments(parseSpeakerSegmentList(revision.getSpeakerSegmentsJson()));
        view.setCreateTime(revision.getCreateTime());
        return view;
    }

    private List<UserMeetingSegmentVO> listSegmentsByMeetingId(Integer meetingId) {
        List<UserMeetingSegment> segments = listSegmentEntitiesByMeetingId(meetingId);
        List<UserMeetingSegmentVO> result = new ArrayList<>(segments.size());
        for (UserMeetingSegment segment : segments) {
            result.add(toSegmentView(segment));
        }
        return result;
    }

    private List<UserMeetingSegment> listSegmentEntitiesByMeetingId(Integer meetingId) {
        LambdaQueryWrapper<UserMeetingSegment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMeetingSegment::getMeetingId, meetingId)
                .orderByAsc(UserMeetingSegment::getSegmentIndex)
                .orderByAsc(UserMeetingSegment::getId);
        return userMeetingSegmentMapper.selectList(wrapper);
    }

    private void applySegmentCorrections(List<UserMeetingSegment> segments, List<MeetingSegmentCorrectionItem> corrections) {
        if (segments == null || segments.isEmpty() || corrections == null || corrections.isEmpty()) {
            return;
        }
        Map<Integer, UserMeetingSegment> segmentMap = new LinkedHashMap<>();
        for (UserMeetingSegment segment : segments) {
            segmentMap.put(segment.getId(), segment);
        }
        for (MeetingSegmentCorrectionItem correction : corrections) {
            if (correction == null || correction.getId() == null) {
                continue;
            }
            UserMeetingSegment segment = segmentMap.get(correction.getId());
            if (segment == null) {
                continue;
            }
            boolean changed = false;
            if (correction.getSpeakerName() != null) {
                String normalizedSpeakerName = normalizeManualSpeakerName(correction.getSpeakerName());
                if (!normalizedSpeakerName.equals(normalizeSpeakerName(segment.getSpeakerName()))) {
                    segment.setSpeakerName(normalizedSpeakerName);
                    segment.setSpeakerProfileId(null);
                    segment.setMatchScore(null);
                    changed = true;
                }
            }
            if (correction.getTranscript() != null) {
                String normalizedTranscript = normalizeLongText(correction.getTranscript());
                if (!safeTextEquals(segment.getTranscript(), normalizedTranscript)) {
                    segment.setTranscript(normalizedTranscript);
                    changed = true;
                }
            }
            if (changed) {
                segment.setUpdateTime(new Date());
                userMeetingSegmentMapper.updateById(segment);
            }
        }
    }

    private String normalizeManualSpeakerName(String speakerName) {
        if (!StringUtils.hasText(speakerName)) {
            return "未知发言人";
        }
        String normalized = speakerName.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private boolean safeTextEquals(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private String buildFullTranscriptFromSegments(List<UserMeetingSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (UserMeetingSegment segment : segments) {
            if (!StringUtils.hasText(segment.getTranscript())) {
                continue;
            }
            lines.add(segment.getTranscript().trim());
        }
        return String.join("\n", lines);
    }

    private UserMeetingSegmentVO toSegmentView(UserMeetingSegment segment) {
        UserMeetingSegmentVO view = new UserMeetingSegmentVO();
        view.setId(segment.getId());
        view.setMeetingId(segment.getMeetingId());
        view.setSegmentIndex(segment.getSegmentIndex());
        view.setStartMs(segment.getStartMs());
        view.setEndMs(segment.getEndMs());
        view.setSpeakerProfileId(segment.getSpeakerProfileId());
        view.setSpeakerName(segment.getSpeakerName());
        view.setMatchScore(segment.getMatchScore());
        view.setTranscript(segment.getTranscript());
        view.setSegmentBucket(segment.getSegmentBucket());
        view.setSegmentObject(segment.getSegmentObject());
        view.setSegmentFilename(segment.getSegmentFilename());
        view.setSegmentFileSize(segment.getSegmentFileSize());
        boolean hasSegmentAudio = StringUtils.hasText(segment.getSegmentBucket()) && StringUtils.hasText(segment.getSegmentObject());
        view.setHasSegmentAudio(hasSegmentAudio);
        view.setSegmentAudioUrl(hasSegmentAudio
                ? "/api/meeting/history/" + segment.getMeetingId() + "/segments/" + segment.getId() + "/audio"
                : null);
        return view;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<UserMeetingSpeakerBlockVO> parseSpeakerBlockList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UserMeetingSpeakerBlockVO>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<UserMeetingSegmentVO> parseSpeakerSegmentList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UserMeetingSegmentVO>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
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

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(current -> {
                            try {
                                Files.deleteIfExists(current);
                            } catch (IOException ignored) {
                            }
                        });
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private static final class TimeRange {
        private final double startSeconds;
        private final double endSeconds;

        private TimeRange(double startSeconds, double endSeconds) {
            this.startSeconds = Math.max(0d, startSeconds);
            this.endSeconds = Math.max(this.startSeconds, endSeconds);
        }

        private double duration() {
            return endSeconds - startSeconds;
        }
    }

    private static final class ProcessResult {
        private final int exitCode;
        private final String output;

        private ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class SpeakerMatch {
        private final Integer profileId;
        private final String speakerName;
        private final BigDecimal score;

        private SpeakerMatch(Integer profileId, String speakerName, BigDecimal score) {
            this.profileId = profileId;
            this.speakerName = speakerName;
            this.score = score;
        }

        private static SpeakerMatch unknown() {
            return new SpeakerMatch(null, "未知发言人", null);
        }
    }

    private static final class SpeakerCluster {
        private final int index;
        private final String label;
        private final byte[] representativeBytes;

        private SpeakerCluster(int index, String label, byte[] representativeBytes) {
            this.index = index;
            this.label = label;
            this.representativeBytes = representativeBytes;
        }
    }
}
