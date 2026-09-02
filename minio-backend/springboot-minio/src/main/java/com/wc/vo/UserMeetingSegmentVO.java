package com.wc.vo;

import java.math.BigDecimal;

public class UserMeetingSegmentVO {

    private Integer id;
    private Integer meetingId;
    private Integer segmentIndex;
    private Long startMs;
    private Long endMs;
    private Integer speakerProfileId;
    private String speakerName;
    private BigDecimal matchScore;
    private String transcript;
    private String segmentBucket;
    private String segmentObject;
    private String segmentFilename;
    private Long segmentFileSize;
    private Boolean hasSegmentAudio;
    private String segmentAudioUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(Integer meetingId) {
        this.meetingId = meetingId;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
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

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getSegmentBucket() {
        return segmentBucket;
    }

    public void setSegmentBucket(String segmentBucket) {
        this.segmentBucket = segmentBucket;
    }

    public String getSegmentObject() {
        return segmentObject;
    }

    public void setSegmentObject(String segmentObject) {
        this.segmentObject = segmentObject;
    }

    public String getSegmentFilename() {
        return segmentFilename;
    }

    public void setSegmentFilename(String segmentFilename) {
        this.segmentFilename = segmentFilename;
    }

    public Long getSegmentFileSize() {
        return segmentFileSize;
    }

    public void setSegmentFileSize(Long segmentFileSize) {
        this.segmentFileSize = segmentFileSize;
    }

    public Boolean getHasSegmentAudio() {
        return hasSegmentAudio;
    }

    public void setHasSegmentAudio(Boolean hasSegmentAudio) {
        this.hasSegmentAudio = hasSegmentAudio;
    }

    public String getSegmentAudioUrl() {
        return segmentAudioUrl;
    }

    public void setSegmentAudioUrl(String segmentAudioUrl) {
        this.segmentAudioUrl = segmentAudioUrl;
    }
}
