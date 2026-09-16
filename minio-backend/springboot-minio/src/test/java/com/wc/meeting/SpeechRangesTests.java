package com.wc.meeting;

import com.wc.meeting.diarization.SpeechRanges;
import com.wc.meeting.diarization.SpeechRanges.Range;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechRangesTests {
    @Test void shortTurnsArePreservedBetweenSilences() {
        assertEquals(List.of(new Range(1, 1.5), new Range(3, 3.6)),
                SpeechRanges.speech(4, List.of(new Range(0, 1), new Range(1.5, 3), new Range(3.6, 4)), 12));
    }
    @Test void longTurnSplittingKeepsTailAndUsesBalancedChunks() {
        var parts = SpeechRanges.speech(24.1, List.of(), 12);
        assertEquals(3, parts.size());
        assertEquals(0, parts.get(0).start());
        assertEquals(24.1, parts.get(2).end());
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(parts.get(i).end() - parts.get(i).start() <= 12);
            assertTrue(parts.get(i).end() - parts.get(i).start() > 1.2);
            if (i > 0) assertEquals(parts.get(i - 1).end(), parts.get(i).start());
        }
    }
    @Test void silenceDoesNotBecomeAnInventedSpeechSegment() {
        assertTrue(SpeechRanges.speech(4, List.of(new Range(0, 4)), 12).isEmpty());
    }
    @Test void unorderedOverlappingSilencesAreClampedAndMerged() {
        assertEquals(List.of(new Range(3, 4)), SpeechRanges.speech(6,
                List.of(new Range(5, 8), new Range(-1, 2), new Range(1, 3), new Range(4, 5)), 12));
    }
    @Test void invalidDurationAndSilenceFailExplicitly() {
        for (double duration : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> SpeechRanges.speech(duration, List.of(), 12));
        assertThrows(IllegalArgumentException.class, () -> SpeechRanges.speech(10, List.of(new Range(5, 4)), 12));
    }
}
