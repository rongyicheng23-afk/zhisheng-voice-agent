package com.wc.meeting;

import com.wc.service.impl.UserMeetingNoteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real FFmpeg/ffprobe, synthetic tones only; no model or speech-accuracy claim. */
class MeetingFfmpegBoundaryTests {
    @TempDir Path directory;

    Path wav(boolean tones) throws Exception {
        for (String tool : List.of("ffmpeg", "ffprobe")) {
            try {
                var process = new ProcessBuilder(tool, "-version").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                assumeTrue(process.waitFor() == 0, tool + " required");
            } catch (java.io.IOException error) { assumeTrue(false, tool + " unavailable"); }
        }
        int samples = 16000 * 3;
        var data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes()).putInt(36 + samples * 2).put("WAVEfmt ".getBytes()).putInt(16);
        data.putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
        data.put("data".getBytes()).putInt(samples * 2);
        for (int i = 0; i < samples; i++) {
            double t = i / 16000.0;
            boolean voiced = tones && ((t >= .5 && t < 1.1) || (t >= 1.8 && t < 2.4));
            data.putShort(voiced ? (short)(12000 * Math.sin(2 * Math.PI * 440 * t)) : 0);
        }
        Path file = directory.resolve("fixture.wav");
        Files.write(file, data.array());
        return file;
    }

    @Test void actualSilenceDetectorRetainsBothSubSecondTurns() throws Exception {
        List<?> ranges = ReflectionTestUtils.invokeMethod(new UserMeetingNoteServiceImpl(), "detectSpeechRanges", wav(true));
        assertEquals(2, ranges.size());
        assertEquals(.5, (double)ReflectionTestUtils.getField(ranges.get(0), "startSeconds"), .01);
        assertEquals(1.1, (double)ReflectionTestUtils.getField(ranges.get(0), "endSeconds"), .01);
        assertEquals(1.8, (double)ReflectionTestUtils.getField(ranges.get(1), "startSeconds"), .01);
        assertEquals(2.4, (double)ReflectionTestUtils.getField(ranges.get(1), "endSeconds"), .01);
    }

    @Test void actualSilentFileProducesNoSpeechRanges() throws Exception {
        List<?> ranges = ReflectionTestUtils.invokeMethod(new UserMeetingNoteServiceImpl(), "detectSpeechRanges", wav(false));
        assertTrue(ranges.isEmpty());
    }
}
