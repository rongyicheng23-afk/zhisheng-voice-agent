package com.wc.vo;

import java.math.BigDecimal;
import java.util.Date;

public class UserVoiceprintHistoryVO {

    private Integer id;
    private Integer userId;
    private String leftBucket;
    private String leftObject;
    private String leftFilename;
    private String leftContentType;
    private Long leftFileSize;
    private String rightBucket;
    private String rightObject;
    private String rightFilename;
    private String rightContentType;
    private Long rightFileSize;
    private BigDecimal score;
    private BigDecimal thresholdValue;
    private Boolean samePerson;
    private String resultMessage;
    private String status;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
    private Boolean hasLeftAudio;
    private Boolean hasRightAudio;
    private String leftAudioUrl;
    private String rightAudioUrl;

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

    public String getLeftBucket() {
        return leftBucket;
    }

    public void setLeftBucket(String leftBucket) {
        this.leftBucket = leftBucket;
    }

    public String getLeftObject() {
        return leftObject;
    }

    public void setLeftObject(String leftObject) {
        this.leftObject = leftObject;
    }

    public String getLeftFilename() {
        return leftFilename;
    }

    public void setLeftFilename(String leftFilename) {
        this.leftFilename = leftFilename;
    }

    public String getLeftContentType() {
        return leftContentType;
    }

    public void setLeftContentType(String leftContentType) {
        this.leftContentType = leftContentType;
    }

    public Long getLeftFileSize() {
        return leftFileSize;
    }

    public void setLeftFileSize(Long leftFileSize) {
        this.leftFileSize = leftFileSize;
    }

    public String getRightBucket() {
        return rightBucket;
    }

    public void setRightBucket(String rightBucket) {
        this.rightBucket = rightBucket;
    }

    public String getRightObject() {
        return rightObject;
    }

    public void setRightObject(String rightObject) {
        this.rightObject = rightObject;
    }

    public String getRightFilename() {
        return rightFilename;
    }

    public void setRightFilename(String rightFilename) {
        this.rightFilename = rightFilename;
    }

    public String getRightContentType() {
        return rightContentType;
    }

    public void setRightContentType(String rightContentType) {
        this.rightContentType = rightContentType;
    }

    public Long getRightFileSize() {
        return rightFileSize;
    }

    public void setRightFileSize(Long rightFileSize) {
        this.rightFileSize = rightFileSize;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public Boolean getSamePerson() {
        return samePerson;
    }

    public void setSamePerson(Boolean samePerson) {
        this.samePerson = samePerson;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
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

    public Boolean getHasLeftAudio() {
        return hasLeftAudio;
    }

    public void setHasLeftAudio(Boolean hasLeftAudio) {
        this.hasLeftAudio = hasLeftAudio;
    }

    public Boolean getHasRightAudio() {
        return hasRightAudio;
    }

    public void setHasRightAudio(Boolean hasRightAudio) {
        this.hasRightAudio = hasRightAudio;
    }

    public String getLeftAudioUrl() {
        return leftAudioUrl;
    }

    public void setLeftAudioUrl(String leftAudioUrl) {
        this.leftAudioUrl = leftAudioUrl;
    }

    public String getRightAudioUrl() {
        return rightAudioUrl;
    }

    public void setRightAudioUrl(String rightAudioUrl) {
        this.rightAudioUrl = rightAudioUrl;
    }
}
