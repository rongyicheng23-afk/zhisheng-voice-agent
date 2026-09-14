package com.wc.meeting;

import com.wc.meeting.controller.MeetingNoteController;
import com.wc.meeting.model.MeetingCorrectionRequest;
import com.wc.service.UserMeetingNoteService;
import com.wc.utils.ThreadLocalUtil;
import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeetingCorrectionErrorTests {
    @AfterEach void cleanup() { ThreadLocalUtil.remove(); }

    @Test void internalFailureDoesNotExposeDatabaseDetails() {
        ThreadLocalUtil.set(Map.of("id", 1));
        var service = mock(UserMeetingNoteService.class);
        var request = new MeetingCorrectionRequest();
        when(service.applyCorrection(7, 1, request)).thenThrow(
                new IllegalStateException("SQL jdbc:mysql://internal/password=fixture"));
        var result = new MeetingNoteController(service).applyCorrection(7, request);
        assertEquals(500, result.getCode());
        assertFalse(result.getMsg().contains("SQL"));
        assertFalse(result.getMsg().contains("fixture"));
        assertTrue(result.getMsg().contains("核对"));
    }

    @Test void validationMessageRemainsActionable() {
        ThreadLocalUtil.set(Map.of("id", 1));
        var service = mock(UserMeetingNoteService.class);
        var request = new MeetingCorrectionRequest();
        when(service.applyCorrection(7, 1, request)).thenThrow(new IllegalArgumentException("校正片段无效"));
        var result = new MeetingNoteController(service).applyCorrection(7, request);
        assertEquals(400, result.getCode());
        assertEquals("校正片段无效", result.getMsg());
    }

    @Test void staleDraftReturnsConflictCode() {
        ThreadLocalUtil.set(Map.of("id", 1));
        var service = mock(UserMeetingNoteService.class);
        var request = new MeetingCorrectionRequest();
        when(service.applyCorrection(7, 1, request)).thenThrow(new MeetingCorrectionConflict());
        var result = new MeetingNoteController(service).applyCorrection(7, request);
        assertEquals(409, result.getCode());
        assertTrue(result.getMsg().contains("保留草稿"));
    }
}
