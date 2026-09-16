package com.wc.meeting;

import com.wc.meeting.diarization.CamPlusDiarizationAdapter;
import com.wc.voiceprint.service.VoiceprintService;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CamPlusDiarizationTests {
    private VoiceprintCompareResult result(String score, boolean samePerson) {
        var result = new VoiceprintCompareResult();
        result.setScore(score == null ? null : new BigDecimal(score));
        result.setSamePerson(samePerson);
        return result;
    }
    private CamPlusDiarizationAdapter adapter(VoiceprintService service) {
        return new CamPlusDiarizationAdapter(service, new BigDecimal("0.72"));
    }
    @Test void verificationFlagCannotBypassGroupingThreshold() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.30", true));
        try (var session = adapter(service).openSession()) {
            assertEquals("说话人 1", session.assign(new byte[]{1}).label());
            var next = session.assign(new byte[]{2});
            assertEquals("说话人 2", next.label());
            assertNull(next.similarity());
        }
    }
    @Test void meetingThresholdAcceptsBoundaryWithoutVerificationFlag() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.72", false));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1});
            var next = session.assign(new byte[]{2});
            assertEquals("说话人 1", next.label());
            assertEquals(new BigDecimal("0.72"), next.similarity());
        }
    }
    @Test void choosesHighestAcceptedScoreAcrossGroups() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.1", false), result("0.8", true), result("0.9", true));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1});
            session.assign(new byte[]{2});
            assertEquals("说话人 2", session.assign(new byte[]{3}).label());
        }
    }
    @Test void missingOrInvalidEvidenceDoesNotInventNewGroup() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result(null, true), result("1.2", true), result("0.1", false));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1});
            assertEquals("未知发言人", session.assign(new byte[]{2}).label());
            assertEquals("未知发言人", session.assign(new byte[]{3}).label());
            assertEquals("说话人 2", session.assign(new byte[]{4}).label());
        }
    }
    @Test void meetingsDoNotShareLabelsOrReferenceAudio() throws Exception {
        var service = mock(VoiceprintService.class);
        var adapter = adapter(service);
        try (var first = adapter.openSession(); var second = adapter.openSession()) {
            assertEquals("说话人 1", first.assign(new byte[]{1}).label());
            assertEquals("说话人 1", second.assign(new byte[]{2}).label());
            verifyNoInteractions(service);
        }
    }
    @Test void closedSessionRejectsReuseAndValidatesInput() {
        var session = adapter(mock(VoiceprintService.class)).openSession();
        assertThrows(IllegalArgumentException.class, () -> session.assign(new byte[0]));
        session.close();
        assertThrows(IllegalStateException.class, () -> session.assign(new byte[]{1}));
        assertDoesNotThrow(session::close);
    }
    @Test void serviceFailurePropagatesWithoutAddingCluster() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenThrow(new IOException("unavailable")).thenReturn(result("0.8", true));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1});
            assertThrows(IOException.class, () -> session.assign(new byte[]{2}));
            assertEquals("说话人 1", session.assign(new byte[]{2}).label());
        }
    }
    @Test void invalidThresholdFailsAtConfigurationTime() {
        assertThrows(IllegalArgumentException.class, () -> new CamPlusDiarizationAdapter(mock(VoiceprintService.class), new BigDecimal("1.1")));
    }

    @Test void ambiguousGroupsStayUnknownAndDoNotBecomeAReference() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.1", false),
                result("0.85", true), result("0.83", true), result("0.91", true), result("0.1", false));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1});
            session.assign(new byte[]{2});
            assertEquals("未知发言人", session.assign(new byte[]{3}).label());
            assertEquals("说话人 1", session.assign(new byte[]{4}).label());
        }
        verify(service, times(5)).compare(any(), any());
    }

    @Test void speakerLimitDoesNotForceAnUnmatchedSegmentIntoAnExistingGroup() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.1", false));
        var adapter = adapter(service);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "maxSpeakers", 1);
        try (var session = adapter.openSession()) {
            session.assign(new byte[]{1});
            assertEquals("未知发言人", session.assign(new byte[]{2}).label());
        }
    }

    @Test void nearThresholdRunnerUpStillMakesAttributionAmbiguous() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.1", false), result("0.73", true), result("0.71", false));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1}); session.assign(new byte[]{2});
            assertEquals("未知发言人", session.assign(new byte[]{3}).label());
        }
    }

    @Test void highConfidenceRefreshRetainsAnchorAndRejectsDisagreement() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.95", true), result("0.93", true), result("0.4", false));
        try (var session = adapter(service).openSession()) {
            session.assign(new byte[]{1}); session.assign(new byte[]{2});
            assertEquals("未知发言人", session.assign(new byte[]{3}).label());
        }
        var references = org.mockito.ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
        verify(service, times(3)).compare(references.capture(), any());
        assertArrayEquals(new byte[]{1}, references.getAllValues().get(1).getBytes());
        assertArrayEquals(new byte[]{2}, references.getAllValues().get(2).getBytes());
    }

    @Test void agreeingRefreshedSamplesRemainBoundedAndAcceptGroup() throws Exception {
        var service = mock(VoiceprintService.class);
        when(service.compare(any(), any())).thenReturn(result("0.95", true));
        try (var session = adapter(service).openSession()) {
            for (int i = 0; i < 8; i++) assertEquals("说话人 1", session.assign(new byte[]{(byte)i}).label());
        }
        verify(service, times(13)).compare(any(), any());
    }
}
