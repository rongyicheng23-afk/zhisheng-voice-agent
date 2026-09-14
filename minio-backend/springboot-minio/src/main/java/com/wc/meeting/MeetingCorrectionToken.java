package com.wc.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wc.entity.UserMeetingNote;
import com.wc.vo.UserMeetingSegmentVO;
import java.security.MessageDigest;
import java.util.*;

/** A content fingerprint, checked under the owned meeting row lock. */
public final class MeetingCorrectionToken {
    private MeetingCorrectionToken() {}

    public static String of(UserMeetingNote note, List<UserMeetingSegmentVO> segments) {
        List<Object> values = new ArrayList<>(Arrays.asList(note.getId(), note.getUid(), note.getTitle(),
                note.getStatus(), note.getSummaryText(), note.getKeywordsJson(), note.getTodoJson(),
                note.getFullTranscript()));
        segments.stream().sorted(Comparator.comparing(UserMeetingSegmentVO::getId)).forEach(s ->
                values.add(Arrays.asList(s.getId(), s.getSpeakerName(), s.getSpeakerProfileId(),
                        s.getMatchScore() == null ? null : s.getMatchScore().stripTrailingZeros().toPlainString(),
                        s.getTranscript(), s.getStartMs(), s.getEndMs())));
        try {
            byte[] bytes = new ObjectMapper().writeValueAsBytes(values);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("无法生成纪要校正版本", error);
        }
    }
}
