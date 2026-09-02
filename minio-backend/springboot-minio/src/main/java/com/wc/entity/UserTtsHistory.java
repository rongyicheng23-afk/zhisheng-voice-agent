package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "t_user_tts_history")
public class UserTtsHistory implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "uid")
    private Integer uid;

    @TableField(value = "input_text")
    private String inputText;

    @TableField(value = "emotion")
    private String emotion;

    @TableField(value = "language")
    private String language;

    @TableField(value = "requested_format")
    private String requestedFormat;

    @TableField(value = "source_bucket")
    private String sourceBucket;

    @TableField(value = "source_object")
    private String sourceObject;

    @TableField(value = "source_filename")
    private String sourceFilename;

    @TableField(value = "source_content_type")
    private String sourceContentType;

    @TableField(value = "source_file_size")
    private Long sourceFileSize;

    @TableField(value = "result_bucket")
    private String resultBucket;

    @TableField(value = "result_object")
    private String resultObject;

    @TableField(value = "result_filename")
    private String resultFilename;

    @TableField(value = "result_content_type")
    private String resultContentType;

    @TableField(value = "result_file_size")
    private Long resultFileSize;

    @TableField(value = "status")
    private String status;

    @TableField(value = "raw_result")
    private String rawResult;

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

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getEmotion() {
        return emotion;
    }

    public void setEmotion(String emotion) {
        this.emotion = emotion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getRequestedFormat() {
        return requestedFormat;
    }

    public void setRequestedFormat(String requestedFormat) {
        this.requestedFormat = requestedFormat;
    }

    public String getSourceBucket() {
        return sourceBucket;
    }

    public void setSourceBucket(String sourceBucket) {
        this.sourceBucket = sourceBucket;
    }

    public String getSourceObject() {
        return sourceObject;
    }

    public void setSourceObject(String sourceObject) {
        this.sourceObject = sourceObject;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getSourceContentType() {
        return sourceContentType;
    }

    public void setSourceContentType(String sourceContentType) {
        this.sourceContentType = sourceContentType;
    }

    public Long getSourceFileSize() {
        return sourceFileSize;
    }

    public void setSourceFileSize(Long sourceFileSize) {
        this.sourceFileSize = sourceFileSize;
    }

    public String getResultBucket() {
        return resultBucket;
    }

    public void setResultBucket(String resultBucket) {
        this.resultBucket = resultBucket;
    }

    public String getResultObject() {
        return resultObject;
    }

    public void setResultObject(String resultObject) {
        this.resultObject = resultObject;
    }

    public String getResultFilename() {
        return resultFilename;
    }

    public void setResultFilename(String resultFilename) {
        this.resultFilename = resultFilename;
    }

    public String getResultContentType() {
        return resultContentType;
    }

    public void setResultContentType(String resultContentType) {
        this.resultContentType = resultContentType;
    }

    public Long getResultFileSize() {
        return resultFileSize;
    }

    public void setResultFileSize(Long resultFileSize) {
        this.resultFileSize = resultFileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRawResult() {
        return rawResult;
    }

    public void setRawResult(String rawResult) {
        this.rawResult = rawResult;
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
