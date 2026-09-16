package com.wc.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.entity.*;
import com.wc.mapper.*;
import com.wc.meeting.model.*;
import com.wc.service.UserInfoService;
import com.wc.service.UserMeetingNoteService;
import com.wc.service.impl.UserMeetingNoteServiceImpl;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingCorrectionTests {
    UserMeetingNoteMapper notes;
    UserMeetingSegmentMapper segments;
    UserMeetingRevisionMapper revisions;
    UserMeetingNoteService service;
    UserMeetingNote note;
    UserMeetingSegment segment;
    RecordingTransactions tx;
    UserMeetingNoteServiceImpl target;

    @BeforeEach void setup() {
        notes = mock(UserMeetingNoteMapper.class);
        segments = mock(UserMeetingSegmentMapper.class);
        revisions = mock(UserMeetingRevisionMapper.class);
        var users = mock(UserInfoService.class);
        when(users.getUserById(1)).thenReturn(new UserInfo());
        target = new UserMeetingNoteServiceImpl();
        ReflectionTestUtils.setField(target, "userMeetingNoteMapper", notes);
        ReflectionTestUtils.setField(target, "userMeetingSegmentMapper", segments);
        ReflectionTestUtils.setField(target, "userMeetingRevisionMapper", revisions);
        ReflectionTestUtils.setField(target, "userInfoService", users);
        ReflectionTestUtils.setField(target, "objectMapper", new ObjectMapper());
        tx = new RecordingTransactions();
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
        service = (UserMeetingNoteService) proxy.getProxy();
        note = new UserMeetingNote();
        note.setId(7); note.setUid(1); note.setStatus("SUCCESS");
        note.setTitle("会议"); note.setSceneType("meeting"); note.setFullTranscript("旧全文");
        segment = new UserMeetingSegment();
        segment.setId(11); segment.setMeetingId(7); segment.setSpeakerName("原组");
        segment.setTranscript("原文"); segment.setStartMs(0L); segment.setEndMs(1000L);
        segment.setSpeakerProfileId(99); segment.setMatchScore(new BigDecimal("0.9"));
        when(notes.selectOwnedForCorrection(7, 1)).thenReturn(note);
        when(notes.updateById(any(UserMeetingNote.class))).thenReturn(1);
        when(segments.selectList(any())).thenReturn(List.of(segment));
        when(segments.updateCorrection(any(), anyBoolean())).thenReturn(1);
        when(revisions.selectCount(any())).thenReturn(1L);
        when(revisions.insert(any(UserMeetingRevision.class))).thenReturn(1);
    }

    MeetingSegmentCorrectionItem item(int id, String speaker, String text) {
        var item = new MeetingSegmentCorrectionItem();
        item.setId(id); item.setSpeakerName(speaker); item.setTranscript(text);
        return item;
    }
    MeetingCorrectionRequest request(MeetingSegmentCorrectionItem... items) {
        var request = new MeetingCorrectionRequest();
        com.wc.vo.UserMeetingSegmentVO view = ReflectionTestUtils.invokeMethod(target, "toSegmentView", segment);
        request.setCorrectionToken(com.wc.meeting.MeetingCorrectionToken.of(note, List.of(view)));
        request.setSpeakerSegments(Arrays.asList(items));
        return request;
    }

    @Test void renameClearsIdentityAndRebuildsFullTextInOneTransaction() {
        var result = service.applyCorrection(7, 1, request(item(11, "人工组", "校正文本")));
        assertNull(segment.getSpeakerProfileId()); assertNull(segment.getMatchScore());
        assertEquals("校正文本", result.getFullTranscript());
        assertEquals("人工组", result.getSpeakerSummaries().get(0).speakerName());
        assertEquals("校正文本", result.getSpeakerSummaries().get(0).statements().get(0).text());
        verify(segments).updateCorrection(segment, true);
        verify(revisions).insert(any(UserMeetingRevision.class));
        assertEquals(1, tx.commits); assertEquals(0, tx.rollbacks);
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, tx.isolation);
    }
    @Test void textOnlyCorrectionKeepsAcousticAssociation() {
        service.applyCorrection(7, 1, request(item(11, "原组", "新文本")));
        assertEquals(99, segment.getSpeakerProfileId());
        verify(segments).updateCorrection(segment, false);
    }

    @Test void staleDraftCannotOverwriteNewerTranscript() {
        var stale = request(item(11, "旧草稿", null));
        segment.setTranscript("别人刚保存的新内容");
        assertThrows(MeetingCorrectionConflict.class, () -> service.applyCorrection(7, 1, stale));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
        verify(notes, never()).updateById(any(UserMeetingNote.class));
        verifyNoInteractions(revisions);
    }

    @Test void staleDraftCannotOverwriteNewerSummaryOrIdentity() {
        var stale = request(item(11, "旧草稿", null));
        note.setSummaryText("新的摘要");
        assertThrows(MeetingCorrectionConflict.class, () -> service.applyCorrection(7, 1, stale));
        var another = request(item(11, "旧草稿", null));
        segment.setSpeakerProfileId(100);
        assertThrows(MeetingCorrectionConflict.class, () -> service.applyCorrection(7, 1, another));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
    }

    @Test void missingTokenFailsClosed() {
        assertThrows(MeetingCorrectionConflict.class,
                () -> service.applyCorrection(7, 1, new MeetingCorrectionRequest()));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
    }
    @Test void explicitFullTextTakesPrecedence() {
        var request = request(item(11, "原组", "新文本"));
        request.setFullTranscript("手工全文");
        assertEquals("手工全文", service.applyCorrection(7, 1, request).getFullTranscript());
    }
    @Test void speakerOnlyRenamePreservesPreviouslyEditedFullTranscript() {
        assertEquals("旧全文", service.applyCorrection(7, 1, request(item(11, "新组", null))).getFullTranscript());
    }
    @Test void foreignSegmentRejectsEntireBatchBeforeWrites() {
        assertThrows(IllegalArgumentException.class, () -> service.applyCorrection(7, 1,
                request(item(11, "新组", null), item(999, "别的会议", null))));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
        verify(notes, never()).updateById(any(UserMeetingNote.class));
    }
    @Test void duplicateSegmentRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.applyCorrection(7, 1,
                request(item(11, "A", null), item(11, "B", null))));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
    }
    @Test void longSpeakerNameRejectedRatherThanSilentlyTruncated() {
        assertThrows(IllegalArgumentException.class, () -> service.applyCorrection(7, 1,
                request(item(11, "名".repeat(65), null))));
        verify(segments, never()).updateCorrection(any(), anyBoolean());
    }
    @Test void processingMeetingCannotBeOverwritten() {
        note.setStatus("TRANSCRIBING");
        assertThrows(IllegalArgumentException.class, () -> service.applyCorrection(7, 1, request()));
        verifyNoInteractions(segments, revisions);
    }
    @Test void nonOwnedMeetingNeverLoadsSegments() {
        when(notes.selectOwnedForCorrection(7, 1)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.applyCorrection(7, 1, request()));
        verifyNoInteractions(segments, revisions);
    }
    @Test void revisionFailureTriggersTransactionRollback() {
        when(revisions.insert(any(UserMeetingRevision.class))).thenThrow(new IllegalStateException("fixture"));
        assertThrows(IllegalStateException.class, () -> service.applyCorrection(7, 1, request(item(11, "新组", null))));
        assertEquals(0, tx.commits); assertEquals(1, tx.rollbacks);
    }
    @Test void disappearedSegmentDoesNotProduceSuccessfulRevision() {
        when(segments.updateCorrection(any(), anyBoolean())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.applyCorrection(7, 1, request(item(11, "新组", null))));
        verifyNoInteractions(revisions);
        assertEquals(1, tx.rollbacks);
    }
    @Test void mapperSqlExplicitlyClearsNullOnlyForRenamesAndScopesMeeting() throws Exception {
        String script = String.join(" ", UserMeetingSegmentMapper.class
                .getMethod("updateCorrection", UserMeetingSegment.class, boolean.class).getAnnotation(Update.class).value());
        var sql = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        String renamed = sql.getBoundSql(Map.of("segment", segment, "renamed", true)).getSql();
        String unchanged = sql.getBoundSql(Map.of("segment", segment, "renamed", false)).getSql();
        assertTrue(renamed.contains("speaker_profile_id = NULL, match_score = NULL"));
        assertFalse(unchanged.contains("speaker_profile_id"));
        assertTrue(renamed.contains("WHERE id = ? AND meeting_id = ?"));
    }

    // Verifies Spring transaction advice, not a real database rollback.
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks, isolation;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { isolation = definition.getIsolationLevel(); }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
