package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@TableName(value = "t_user_meeting_segment")
public class UserMeetingSegment implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "meeting_id")
    private Integer meetingId;

    @TableField(value = "segment_index")
    private Integer segmentIndex;

    @TableField(value = "start_ms")
    private Long startMs;

    @TableField(value = "end_ms")
    private Long endMs;

    @TableField(value = "speaker_profile_id")
    private Integer speakerProfileId;

    @TableField(value = "speaker_name")
    private String speakerName;

    @TableField(value = "match_score")
    private BigDecimal matchScore;

    @TableField(value = "transcript")
    private String transcript;

    @TableField(value = "segment_bucket")
    private String segmentBucket;

    @TableField(value = "segment_object")
    private String segmentObject;

    @TableField(value = "segment_filename")
    private String segmentFilename;

    @TableField(value = "segment_content_type")
    private String segmentContentType;

    @TableField(value = "segment_file_size")
    private Long segmentFileSize;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time")
    private Date createTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time")
    private Date updateTime;

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

    public String getSegmentContentType() {
        return segmentContentType;
    }

    public void setSegmentContentType(String segmentContentType) {
        this.segmentContentType = segmentContentType;
    }

    public Long getSegmentFileSize() {
        return segmentFileSize;
    }

    public void setSegmentFileSize(Long segmentFileSize) {
        this.segmentFileSize = segmentFileSize;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
