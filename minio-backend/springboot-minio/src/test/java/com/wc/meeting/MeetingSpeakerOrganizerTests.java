package com.wc.meeting;

import com.wc.vo.*;
import com.wc.meeting.model.MeetingExportTemplate;
import com.wc.service.impl.UserMeetingNoteServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeetingSpeakerOrganizerTests {
    UserMeetingSegmentVO segment(int id, String name, String text) {
        var value = new UserMeetingSegmentVO();
        value.setId(id); value.setSpeakerName(name); value.setTranscript(text);
        value.setStartMs(id * 1000L); value.setEndMs(id * 1000L + 900);
        return value;
    }

    @Test void quotesRetainSourceAndDoNotInventOwnersOrConfirmedDecisions() {
        var groups = MeetingSpeakerOrganizer.organize(List.of(
                segment(1, "说话人 1", "我建议改版。尚未决定上线。请李四提交报告。"),
                segment(2, "说话人 2", "我不同意。"),
                segment(3, "说话人 1", "继续讨论。")));
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).segmentCount());
        var decision = groups.get(0).decisionCandidates().get(0);
        assertEquals("尚未决定上线。", decision.text());
        assertEquals(1, decision.segmentId());
        assertEquals(1000L, decision.startMs());
        assertEquals("请李四提交报告。", groups.get(0).todoCandidates().get(0).text());
        assertTrue(groups.get(0).identityNotice().contains("身份未经核实"));
    }

    @Test void unknownSegmentsAndSameNamedDifferentProfilesStaySeparate() {
        var a = segment(3, "王", "第一人。"); a.setSpeakerProfileId(10);
        var b = segment(4, "王", "第二人。"); b.setSpeakerProfileId(11);
        var groups = MeetingSpeakerOrganizer.organize(List.of(
                segment(1, "未知发言人", "第一段。"), segment(2, "未知发言人", "第二段。"), a, b));
        assertEquals(4, groups.size());
        assertTrue(groups.stream().allMatch(g -> g.segmentCount() == 1));
    }

    @Test void renameAndTranscriptCorrectionRecomputeOrganizationWithoutStaleQuotes() {
        var a = segment(1, "说话人 1", "旧句。");
        var b = segment(2, "说话人 2", "另一句。");
        a.setSpeakerName("会议内人工名称"); b.setSpeakerName("会议内人工名称"); a.setTranscript("新句。");
        var groups = MeetingSpeakerOrganizer.organize(List.of(a, b));
        assertEquals(1, groups.size());
        assertEquals("新句。", groups.get(0).statements().get(0).text());
        assertTrue(groups.get(0).identityNotice().contains("人工命名"));
    }

    @Test void emptyInputDoesNotFabricateContent() {
        assertTrue(MeetingSpeakerOrganizer.organize(null).isEmpty());
        var group = MeetingSpeakerOrganizer.organize(List.of(segment(1, "说话人 1", ""))).get(0);
        assertTrue(group.statements().isEmpty());
        assertTrue(group.todoCandidates().isEmpty());
    }

    @Test void reviewQueueKeepsUnknownShortAndCandidateIdentityExplicit() {
        var unknown = segment(1, "未知发言人", "是否决定提交？尚未决定上线。暂定周五提交。");
        var profile = segment(2, "王", "继续。"); profile.setSpeakerProfileId(10);
        var groups = MeetingSpeakerOrganizer.organize(List.of(unknown, profile));
        assertEquals(1, groups.get(0).reviewItems().size());
        assertEquals(1, groups.get(0).reviewItems().get(0).segmentId());
        assertTrue(groups.get(0).reviewItems().get(0).reasons().stream().anyMatch(s -> s.contains("短发言")));
        assertTrue(groups.get(1).reviewItems().get(0).reasons().stream().anyMatch(s -> s.contains("身份")));
        assertTrue(groups.get(0).statements().get(0).caution().contains("疑问"));
        assertTrue(groups.get(0).statements().get(1).caution().contains("否定"));
        assertTrue(groups.get(0).statements().get(2).caution().contains("待定"));
    }

    @Test void unknownSegmentsWithoutIdsNeverMerge() {
        var a = segment(1, null, "甲。"); a.setId(null);
        var b = segment(2, null, "乙。"); b.setId(null);
        assertEquals(2, MeetingSpeakerOrganizer.organize(List.of(a, b)).size());
    }

    @Test void adjacentBlocksDoNotMergeDifferentProfilesWithTheSameName() {
        var service = new UserMeetingNoteServiceImpl();
        var a = segment(1, "王", "第一人。"); a.setSpeakerProfileId(10);
        var b = segment(2, "王", "第二人。"); b.setSpeakerProfileId(11);
        List<UserMeetingSpeakerBlockVO> blocks = ReflectionTestUtils.invokeMethod(service, "mergeSpeakerBlocks", List.of(a, b));
        assertEquals(2, blocks.size());
    }

    @Test void repeatedSentenceDoesNotGuessItsSpeakerAndMentionIsNotOwnership() {
        var service = new UserMeetingNoteServiceImpl();
        var a = new UserMeetingSpeakerBlockVO(); a.setSpeakerName("说话人 1"); a.setTranscript("决定继续讨论。");
        var b = new UserMeetingSpeakerBlockVO(); b.setSpeakerName("说话人 2"); b.setTranscript("决定继续讨论。");
        String speaker = ReflectionTestUtils.invokeMethod(service, "resolveSpeakerFromSentence", "决定继续讨论。", List.of(a, b));
        assertNull(speaker);
        String owner = ReflectionTestUtils.invokeMethod(service, "extractOwner", "需要与李四讨论报告", Set.of("李四"));
        assertNull(owner);
        owner = ReflectionTestUtils.invokeMethod(service, "extractOwner", "请李四提交报告", Set.of("李四"));
        assertEquals("李四", owner);
        owner = ReflectionTestUtils.invokeMethod(service, "extractOwner", "请我们提交报告", Set.of());
        assertNull(owner);
    }

    @Test void legacyRoleAndDecisionViewsDoNotCollapseSameNamedProfiles() {
        var service = new UserMeetingNoteServiceImpl();
        var a = new UserMeetingSpeakerBlockVO(); a.setSpeakerName("王"); a.setSpeakerProfileId(10); a.setTranscript("接下来决定继续讨论。");
        var b = new UserMeetingSpeakerBlockVO(); b.setSpeakerName("王"); b.setSpeakerProfileId(11); b.setTranscript("接下来决定继续讨论。");
        String speaker = ReflectionTestUtils.invokeMethod(service, "resolveSpeakerFromSentence", "决定继续讨论。", List.of(a, b));
        assertNull(speaker);
        List<UserMeetingRoleInsightVO> roles = ReflectionTestUtils.invokeMethod(service, "buildRoleInsights", List.of(a, b), List.of());
        assertTrue(roles.get(0).getSpeakerName().contains("档案 #"));
        assertTrue(roles.get(0).getContribution().contains("1 段"));
        a.setSpeakerName("未知发言人"); a.setSpeakerProfileId(null);
        assertNull(ReflectionTestUtils.invokeMethod(service, "resolveSpeakerFromSentence", "决定继续讨论。", List.of(a)));
    }

    @Test void exportsIncludeTraceableQuotesAndRespectSpeakerSectionToggle() throws Exception {
        var service = new UserMeetingNoteServiceImpl();
        var view = new UserMeetingNoteVO();
        view.setTitle("测试会议");
        view.setSpeakerSegments(List.of(segment(9, "说话人 1", "计划提交报告。")));
        var template = new MeetingExportTemplate();
        for (String method : List.of("buildTextExport", "buildMarkdownExport")) {
            String output = ReflectionTestUtils.invokeMethod(service, method, view, template);
            assertTrue(output.contains("片段 #9"));
            assertTrue(output.contains("计划提交报告。"));
            assertTrue(output.contains("身份未经核实"));
        }
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                ReflectionTestUtils.invokeMethod(service, "buildDocxExport", view, template)) {
            assertTrue(doc.getParagraphs().stream().anyMatch(p -> p.getText().contains("片段 #9")));
        }
        template.setIncludeSpeakerBlocks(false);
        String output = ReflectionTestUtils.invokeMethod(service, "buildTextExport", view, template);
        assertFalse(output.contains("片段 #9"));
    }
}
