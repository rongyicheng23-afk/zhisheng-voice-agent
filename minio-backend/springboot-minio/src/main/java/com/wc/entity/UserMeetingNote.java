package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "t_user_meeting_note")
public class UserMeetingNote implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "uid")
    private Integer uid;

    @TableField(value = "title")
    private String title;

    @TableField(value = "scene_type")
    private String sceneType;

    @TableField(value = "selected_speaker_ids_json")
    private String selectedSpeakerIdsJson;

    @TableField(value = "raw_bucket")
    private String rawBucket;

    @TableField(value = "raw_object")
    private String rawObject;

    @TableField(value = "raw_filename")
    private String rawFilename;

    @TableField(value = "raw_content_type")
    private String rawContentType;

    @TableField(value = "raw_file_size")
    private Long rawFileSize;

    @TableField(value = "full_transcript")
    private String fullTranscript;

    @TableField(value = "summary_text")
    private String summaryText;

    @TableField(value = "keywords_json")
    private String keywordsJson;

    @TableField(value = "todo_json")
    private String todoJson;

    @TableField(value = "raw_result")
    private String rawResult;

    @TableField(value = "status")
    private String status;

    @TableField(value = "error_message")
    private String errorMessage;

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

    public Integer getUid() {
        return uid;
    }

    public void setUid(Integer uid) {
        this.uid = uid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSceneType() {
        return sceneType;
    }

    public void setSceneType(String sceneType) {
        this.sceneType = sceneType;
    }

    public String getSelectedSpeakerIdsJson() {
        return selectedSpeakerIdsJson;
    }

    public void setSelectedSpeakerIdsJson(String selectedSpeakerIdsJson) {
        this.selectedSpeakerIdsJson = selectedSpeakerIdsJson;
    }

    public String getRawBucket() {
        return rawBucket;
    }

    public void setRawBucket(String rawBucket) {
        this.rawBucket = rawBucket;
    }

    public String getRawObject() {
        return rawObject;
    }

    public void setRawObject(String rawObject) {
        this.rawObject = rawObject;
    }

    public String getRawFilename() {
        return rawFilename;
    }

    public void setRawFilename(String rawFilename) {
        this.rawFilename = rawFilename;
    }

    public String getRawContentType() {
        return rawContentType;
    }

    public void setRawContentType(String rawContentType) {
        this.rawContentType = rawContentType;
    }

    public Long getRawFileSize() {
        return rawFileSize;
    }

    public void setRawFileSize(Long rawFileSize) {
        this.rawFileSize = rawFileSize;
    }

    public String getFullTranscript() {
        return fullTranscript;
    }

    public void setFullTranscript(String fullTranscript) {
        this.fullTranscript = fullTranscript;
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

    public String getRawResult() {
        return rawResult;
    }

    public void setRawResult(String rawResult) {
        this.rawResult = rawResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
