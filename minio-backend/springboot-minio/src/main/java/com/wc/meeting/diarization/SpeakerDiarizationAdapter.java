package com.wc.meeting.diarization;

import com.wc.utils.InMemoryMultipartFile;
import com.wc.voiceprint.model.VoiceprintCompareResult;
import com.wc.voiceprint.service.VoiceprintService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter boundary for anonymous in-meeting speaker grouping.
 *
 * Labels such as “说话人 1” only mean that audio segments are similar within
 * this meeting. They deliberately do not identify a real person. Known
 * voiceprint profiles are still resolved by the caller and require human
 * confirmation before they become an identity claim.
 */
@Component
public class SpeakerDiarizationAdapter {
    private static final BigDecimal DEFAULT_CLUSTER_THRESHOLD = new BigDecimal("0.72");
    private final VoiceprintService voiceprintService;

    public SpeakerDiarizationAdapter(VoiceprintService voiceprintService) {
        this.voiceprintService = voiceprintService;
    }

    public Session openSession() {
        return new Session(voiceprintService, DEFAULT_CLUSTER_THRESHOLD);
    }

    public static final class Assignment {
        private final String clusterLabel;
        private final BigDecimal confidence;
        private final boolean newCluster;

        public Assignment(String clusterLabel, BigDecimal confidence, boolean newCluster) {
            this.clusterLabel = clusterLabel;
            this.confidence = confidence;
            this.newCluster = newCluster;
        }

        public String getClusterLabel() { return clusterLabel; }
        public BigDecimal getConfidence() { return confidence; }
        public boolean isNewCluster() { return newCluster; }
    }

    public static final class Session {
        private final VoiceprintService voiceprintService;
        private final BigDecimal threshold;
        private final List<Cluster> clusters = new ArrayList<>();

        private Session(VoiceprintService voiceprintService, BigDecimal threshold) {
            this.voiceprintService = voiceprintService;
            this.threshold = threshold;
        }

        public Assignment assign(byte[] segmentBytes) throws IOException {
            if (clusters.isEmpty()) {
                Cluster first = new Cluster(1, segmentBytes);
                clusters.add(first);
                return new Assignment(first.label(), null, true);
            }
            Cluster best = null;
            BigDecimal bestScore = null;
            MultipartFile candidate = new InMemoryMultipartFile("file2", "segment.wav", "audio/wav", segmentBytes);
            for (Cluster cluster : clusters) {
                MultipartFile representative = new InMemoryMultipartFile(
                        "file1", "anonymous_" + cluster.index + ".wav", "audio/wav", cluster.representativeBytes
                );
                VoiceprintCompareResult comparison = voiceprintService.compare(representative, candidate);
                BigDecimal score = comparison.getScore();
                if (score != null && (bestScore == null || score.compareTo(bestScore) > 0)) {
                    best = cluster;
                    bestScore = score;
                }
            }
            if (best != null && bestScore != null && bestScore.compareTo(threshold) >= 0) {
                // Keep a longer representative clip when available. It makes
                // the next comparison less sensitive to a short noisy phrase.
                if (segmentBytes.length > best.representativeBytes.length) best.representativeBytes = segmentBytes;
                return new Assignment(best.label(), bestScore, false);
            }
            Cluster created = new Cluster(clusters.size() + 1, segmentBytes);
            clusters.add(created);
            return new Assignment(created.label(), bestScore, true);
        }

        public int clusterCount() { return clusters.size(); }
    }

    private static final class Cluster {
        private final int index;
        private byte[] representativeBytes;
        private Cluster(int index, byte[] representativeBytes) { this.index = index; this.representativeBytes = representativeBytes; }
        private String label() { return "说话人 " + index; }
    }
}
