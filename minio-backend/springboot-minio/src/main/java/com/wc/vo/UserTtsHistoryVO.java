package com.wc.vo;

import java.util.Date;

public class UserTtsHistoryVO {

    private Integer id;
    private Integer userId;
    private String inputText;
    private String emotion;
    private String language;
    private String requestedFormat;
    private String sourceBucket;
    private String sourceObject;
    private String sourceFilename;
    private String sourceContentType;
    private Long sourceFileSize;
    private String resultBucket;
    private String resultObject;
    private String resultFilename;
    private String resultContentType;
    private Long resultFileSize;
    private String status;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
    private Boolean hasSourceAudio;
    private Boolean hasResultAudio;
    private String sourceAudioUrl;
    private String resultAudioUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
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

    public Boolean getHasSourceAudio() {
        return hasSourceAudio;
    }

    public void setHasSourceAudio(Boolean hasSourceAudio) {
        this.hasSourceAudio = hasSourceAudio;
    }

    public Boolean getHasResultAudio() {
        return hasResultAudio;
    }

    public void setHasResultAudio(Boolean hasResultAudio) {
        this.hasResultAudio = hasResultAudio;
    }

    public String getSourceAudioUrl() {
        return sourceAudioUrl;
    }

    public void setSourceAudioUrl(String sourceAudioUrl) {
        this.sourceAudioUrl = sourceAudioUrl;
    }

    public String getResultAudioUrl() {
        return resultAudioUrl;
    }

    public void setResultAudioUrl(String resultAudioUrl) {
        this.resultAudioUrl = resultAudioUrl;
    }
}
