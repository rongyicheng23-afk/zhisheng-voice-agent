package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "t_user_meeting_revision")
public class UserMeetingRevision implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "meeting_id")
    private Integer meetingId;

    @TableField(value = "version_no")
    private Integer versionNo;

    @TableField(value = "revision_type")
    private String revisionType;

    @TableField(value = "title")
    private String title;

    @TableField(value = "summary_text")
    private String summaryText;

    @TableField(value = "keywords_json")
    private String keywordsJson;

    @TableField(value = "todo_json")
    private String todoJson;

    @TableField(value = "full_transcript")
    private String fullTranscript;

    @TableField(value = "speaker_transcript")
    private String speakerTranscript;

    @TableField(value = "speaker_blocks_json")
    private String speakerBlocksJson;

    @TableField(value = "speaker_segments_json")
    private String speakerSegmentsJson;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time")
    private Date createTime;

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

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getRevisionType() {
        return revisionType;
    }

    public void setRevisionType(String revisionType) {
        this.revisionType = revisionType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getKeywordsJson() {
        return keywordsJson;
    }

    public void setKeywordsJson(String keywordsJson) {
        this.keywordsJson = keywordsJson;
    }

    public String getTodoJson() {
        return todoJson;
    }

    public void setTodoJson(String todoJson) {
        this.todoJson = todoJson;
    }

    public String getFullTranscript() {
        return fullTranscript;
    }

    public void setFullTranscript(String fullTranscript) {
        this.fullTranscript = fullTranscript;
    }

    public String getSpeakerTranscript() {
        return speakerTranscript;
    }

    public void setSpeakerTranscript(String speakerTranscript) {
        this.speakerTranscript = speakerTranscript;
    }

    public String getSpeakerBlocksJson() {
        return speakerBlocksJson;
    }

    public void setSpeakerBlocksJson(String speakerBlocksJson) {
        this.speakerBlocksJson = speakerBlocksJson;
    }

    public String getSpeakerSegmentsJson() {
        return speakerSegmentsJson;
    }

    public void setSpeakerSegmentsJson(String speakerSegmentsJson) {
        this.speakerSegmentsJson = speakerSegmentsJson;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
