package com.wc.meeting.diarization;

import java.util.*;

/** Deterministic silence complement: retain short turns and never drop a tail. */
public final class SpeechRanges {
    private SpeechRanges() {}
    public record Range(double start, double end) {}

    public static List<Range> speech(double duration, List<Range> silences, double maxSeconds) {
        if (!Double.isFinite(duration) || duration <= 0 || !Double.isFinite(maxSeconds) || maxSeconds <= 0)
            throw new IllegalArgumentException("invalid audio duration or segment limit");
        List<Range> ordered = new ArrayList<>();
        for (Range r : silences) {
            if (!Double.isFinite(r.start) || !Double.isFinite(r.end) || r.end < r.start)
                throw new IllegalArgumentException("invalid silence range");
            double start = Math.max(0, Math.min(duration, r.start));
            double end = Math.max(start, Math.min(duration, r.end));
            if (end > start) ordered.add(new Range(start, end));
        }
        ordered.sort(Comparator.comparingDouble(Range::start));
        List<Range> result = new ArrayList<>();
        double cursor = 0;
        for (Range silence : ordered) {
            if (silence.start > cursor) split(result, cursor, silence.start, maxSeconds);
            cursor = Math.max(cursor, silence.end);
        }
        if (cursor < duration) split(result, cursor, duration, maxSeconds);
        return List.copyOf(result);
    }

    private static void split(List<Range> result, double start, double end, double maxSeconds) {
        int count = (int) Math.ceil((end - start) / maxSeconds);
        double step = (end - start) / count;
        for (int i = 0; i < count; i++)
            result.add(new Range(start + i * step, i == count - 1 ? end : start + (i + 1) * step));
    }
}
