package com.wc.meeting.diarization;

import java.io.IOException;
import java.math.BigDecimal;

/** Meeting-local anonymous grouping, never a real-person identity claim. */
public interface SpeakerDiarizationAdapter {
    Session openSession();
    record Assignment(String label, BigDecimal similarity) {}
    interface Session extends AutoCloseable {
        Assignment assign(byte[] wav) throws IOException;
        @Override void close();
    }
}
