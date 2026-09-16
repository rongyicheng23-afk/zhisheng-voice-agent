package com.wc.meeting;

import com.wc.vo.UserMeetingSegmentVO;
import java.util.*;

/**
 * Extractive, meeting-local organization. Quotes remain attached to their
 * source segment; a name, a role or a keyword never authenticates a person.
 */
public final class MeetingSpeakerOrganizer {
    private MeetingSpeakerOrganizer() {}

    public record Evidence(Integer segmentId, Long startMs, Long endMs, String text) {}
    public record SpeakerSummary(String groupKey, String speakerName, String identityNotice,
            int segmentCount, List<Evidence> statements, List<Evidence> decisionCandidates,
            List<Evidence> todoCandidates) {}

    public static List<SpeakerSummary> organize(List<UserMeetingSegmentVO> segments) {
        if (segments == null) return List.of();
        Map<String, List<UserMeetingSegmentVO>> groups = new LinkedHashMap<>();
        for (UserMeetingSegmentVO segment : segments) {
            if (segment == null) continue;
            String name = name(segment);
            // Unknown segments are not evidence of a shared speaker.
            String key = "未知发言人".equals(name) ? "unknown:" + segment.getId()
                    : segment.getSpeakerProfileId() != null ? "profile:" + segment.getSpeakerProfileId()
                    : "local:" + name;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(segment);
        }
        List<SpeakerSummary> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            var first = group.getValue().get(0);
            List<Evidence> statements = new ArrayList<>(), decisions = new ArrayList<>(), todos = new ArrayList<>();
            for (var segment : group.getValue()) {
                String text = segment.getTranscript();
                if (text == null || text.isBlank()) continue;
                for (String sentence : text.split("(?<=[。！？!?；;])|\\r?\\n")) {
                    if (sentence.isBlank()) continue;
                    var evidence = new Evidence(segment.getId(), segment.getStartMs(), segment.getEndMs(), sentence.trim());
                    statements.add(evidence);
                    if (contains(sentence, "决定", "确定", "同意", "结论", "通过", "暂定", "待确认"))
                        decisions.add(evidence);
                    if (contains(sentence, "负责", "待办", "完成", "提交", "跟进", "安排", "需要", "计划"))
                        todos.add(evidence);
                }
            }
            String notice = "未知发言人".equals(name(first)) ? "未确定归属，不与其他未知片段合并"
                    : first.getSpeakerProfileId() != null ? "档案声学匹配候选，身份未经核实"
                    : "仅本场会议的匿名分组或人工命名，身份未经核实";
            result.add(new SpeakerSummary(group.getKey(), name(first), notice, group.getValue().size(),
                    List.copyOf(statements), List.copyOf(decisions), List.copyOf(todos)));
        }
        return List.copyOf(result);
    }

    private static String name(UserMeetingSegmentVO segment) {
        return segment.getSpeakerName() == null || segment.getSpeakerName().isBlank()
                ? "未知发言人" : segment.getSpeakerName().trim();
    }

    private static boolean contains(String text, String... markers) {
        return Arrays.stream(markers).anyMatch(text::contains);
    }
}
