package com.wc.vo;

import java.math.BigDecimal;

public class UserMeetingSpeakerBlockVO {

    private Integer speakerProfileId;
    private String speakerName;
    private BigDecimal matchScore;
    private Long startMs;
    private Long endMs;
    private String transcript;
    private Integer segmentCount;

    public Integer getSpeakerProfileId() {
        return speakerProfileId;
    }

    public void setSpeakerProfileId(Integer speakerProfileId) {
        this.speakerProfileId = speakerProfileId;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public Long getStartMs() {
        return startMs;
    }

    public void setStartMs(Long startMs) {
        this.startMs = startMs;
    }

    public Long getEndMs() {
        return endMs;
    }

    public void setEndMs(Long endMs) {
        this.endMs = endMs;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public Integer getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }
}
