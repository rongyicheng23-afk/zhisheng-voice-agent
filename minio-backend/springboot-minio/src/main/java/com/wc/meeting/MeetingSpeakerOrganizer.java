package com.wc.meeting;

import com.wc.vo.UserMeetingSegmentVO;
import java.util.*;

/**
 * Extractive, meeting-local organization. Quotes remain attached to their
 * source segment; a name, a role or a keyword never authenticates a person.
 */
public final class MeetingSpeakerOrganizer {
    private MeetingSpeakerOrganizer() {}

    public record Evidence(Integer segmentId, Long startMs, Long endMs, String text, String caution) {}
    public record ReviewItem(Integer segmentId, Long startMs, Long endMs, List<String> reasons) {}
    public record SpeakerSummary(String groupKey, String speakerName, String identityNotice,
            int segmentCount, List<Evidence> statements, List<Evidence> decisionCandidates,
            List<Evidence> todoCandidates, List<ReviewItem> reviewItems) {}

    public static List<SpeakerSummary> organize(List<UserMeetingSegmentVO> segments) {
        if (segments == null) return List.of();
        Map<String, List<UserMeetingSegmentVO>> groups = new LinkedHashMap<>();
        int unknownIndex = 0;
        for (UserMeetingSegmentVO segment : segments) {
            if (segment == null) continue;
            String name = name(segment);
            // Unknown segments are not evidence of a shared speaker.
            String key = "未知发言人".equals(name) ? "unknown:" + (segment.getId() != null ? segment.getId() : "missing-" + unknownIndex++)
                    : segment.getSpeakerProfileId() != null ? "profile:" + segment.getSpeakerProfileId()
                    : "local:" + name;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(segment);
        }
        List<SpeakerSummary> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            var first = group.getValue().get(0);
            List<Evidence> statements = new ArrayList<>(), decisions = new ArrayList<>(), todos = new ArrayList<>();
            List<ReviewItem> review = new ArrayList<>();
            for (var segment : group.getValue()) {
                List<String> reasons = new ArrayList<>();
                if ("未知发言人".equals(name(segment))) reasons.add("归属未知，请逐段试听确认，不能把未知片段视为同一人");
                if (segment.getSpeakerProfileId() != null) reasons.add("档案匹配仅为候选，不代表真实身份已核实");
                Long start = segment.getStartMs(), end = segment.getEndMs();
                if (start == null || end == null || start < 0 || end <= start) reasons.add("时间范围无效，不能可靠定位原音");
                else if (end - start < 1200) reasons.add("短发言：声学证据不足，需人工复核归属");
                if (segment.getMatchScore() != null && segment.getMatchScore().doubleValue() < 0.80)
                    reasons.add("相似度偏低，建议复核；相似度不是身份概率");
                if (!reasons.isEmpty()) review.add(new ReviewItem(segment.getId(), start, end, List.copyOf(reasons)));
                String text = segment.getTranscript();
                if (text == null || text.isBlank()) continue;
                for (String sentence : text.split("(?<=[。！？!?；;])|\\r?\\n")) {
                    if (sentence.isBlank()) continue;
                    var evidence = new Evidence(segment.getId(), segment.getStartMs(), segment.getEndMs(), sentence.trim(), caution(sentence));
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
                    List.copyOf(statements), List.copyOf(decisions), List.copyOf(todos), List.copyOf(review)));
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

    private static String caution(String text) {
        if (contains(text, "?", "？", "是否", "能否", "可否")) return "含疑问，不作为已确定结论或已分配任务";
        if (contains(text, "不", "未", "取消", "否决")) return "含否定或取消表述，请核对语境，不自动认定结论和待办";
        if (contains(text, "建议", "暂定", "待定", "待确认", "如果", "假如", "再讨论")) return "建议、条件或待定表述，尚需确认";
        return "原文摘录，结论、负责人及期限均需人工核对";
    }
}
