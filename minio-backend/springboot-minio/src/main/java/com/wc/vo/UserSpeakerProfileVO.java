package com.wc.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

public class UserSpeakerProfileVO {

    private Integer id;
    private Integer userId;
    private String speakerName;
    private String speakerRole;
    private String sampleBucket;
    private String sampleObject;
    private String sampleFilename;
    private String sampleContentType;
    private Long sampleFileSize;
    private String status;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
    private Boolean hasSampleAudio;
    private String sampleAudioUrl;

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

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public String getSpeakerRole() {
        return speakerRole;
    }

    public void setSpeakerRole(String speakerRole) {
        this.speakerRole = speakerRole;
    }

    public String getSampleBucket() {
        return sampleBucket;
    }

    public void setSampleBucket(String sampleBucket) {
        this.sampleBucket = sampleBucket;
    }

    public String getSampleObject() {
        return sampleObject;
    }

    public void setSampleObject(String sampleObject) {
        this.sampleObject = sampleObject;
    }

    public String getSampleFilename() {
        return sampleFilename;
    }

    public void setSampleFilename(String sampleFilename) {
        this.sampleFilename = sampleFilename;
    }

    public String getSampleContentType() {
        return sampleContentType;
    }

    public void setSampleContentType(String sampleContentType) {
        this.sampleContentType = sampleContentType;
    }

    public Long getSampleFileSize() {
        return sampleFileSize;
    }

    public void setSampleFileSize(Long sampleFileSize) {
        this.sampleFileSize = sampleFileSize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Boolean getHasSampleAudio() {
        return hasSampleAudio;
    }

    public void setHasSampleAudio(Boolean hasSampleAudio) {
        this.hasSampleAudio = hasSampleAudio;
    }

    public String getSampleAudioUrl() {
        return sampleAudioUrl;
    }

    public void setSampleAudioUrl(String sampleAudioUrl) {
        this.sampleAudioUrl = sampleAudioUrl;
    }
}
