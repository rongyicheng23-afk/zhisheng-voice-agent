package com.wc.meeting.diarization;

import com.wc.voiceprint.service.VoiceprintService;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.wc.utils.InMemoryMultipartFile;

/** Reuses CAM++ comparisons; FFmpeg boundaries and ASR remain in the meeting pipeline. */
@Component
public class CamPlusDiarizationAdapter implements SpeakerDiarizationAdapter {
    private final VoiceprintService voiceprint;
    private final BigDecimal threshold;

    public CamPlusDiarizationAdapter(VoiceprintService voiceprint,
            @Value("${meeting.diarization.threshold:0.72}") BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0 || threshold.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("diarization threshold must be between 0 and 1");
        }
        this.voiceprint = voiceprint;
        this.threshold = threshold;
    }

    @Override public Session openSession() { return new GroupingSession(); }

    private final class GroupingSession implements Session {
        private final List<byte[]> representatives = new ArrayList<>();
        private boolean closed;

        @Override public synchronized Assignment assign(byte[] wav) throws IOException {
            if (closed) throw new IllegalStateException("diarization session closed");
            if (wav == null || wav.length == 0) throw new IllegalArgumentException("empty segment");
            int best = -1;
            BigDecimal bestScore = null;
            boolean incomplete = false;
            for (int i = 0; i < representatives.size(); i++) {
                VoiceprintCompareResult result = voiceprint.compare(
                        new InMemoryMultipartFile("file1", "anonymous.wav", "audio/wav", representatives.get(i)),
                        new InMemoryMultipartFile("file2", "segment.wav", "audio/wav", wav));
                BigDecimal score = result == null ? null : result.getScore();
                if (score == null || score.compareTo(BigDecimal.ONE.negate()) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
                    incomplete = true;
                    continue;
                }
                // Verification's samePerson flag uses another threshold. Do not
                // let that flag bypass the meeting-specific grouping threshold.
                if (score.compareTo(threshold) >= 0 && (bestScore == null || score.compareTo(bestScore) > 0)) {
                    best = i;
                    bestScore = score;
                }
            }
            if (best >= 0) return new Assignment("说话人 " + (best + 1), bestScore);
            // Missing comparison evidence does not prove a new speaker exists.
            if (incomplete) return new Assignment("未知发言人", null);
            representatives.add(wav.clone());
            // No match was accepted, so a new group must not inherit a rejected score.
            return new Assignment("说话人 " + representatives.size(), null);
        }

        @Override public synchronized void close() {
            representatives.clear();
            closed = true;
        }
    }
}
