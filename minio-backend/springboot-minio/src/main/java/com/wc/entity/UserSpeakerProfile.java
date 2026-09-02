package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "t_user_speaker_profile")
public class UserSpeakerProfile implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "uid")
    private Integer uid;

    @TableField(value = "speaker_name")
    private String speakerName;

    @TableField(value = "speaker_role")
    private String speakerRole;

    @TableField(value = "sample_bucket")
    private String sampleBucket;

    @TableField(value = "sample_object")
    private String sampleObject;

    @TableField(value = "sample_filename")
    private String sampleFilename;

    @TableField(value = "sample_content_type")
    private String sampleContentType;

    @TableField(value = "sample_file_size")
    private Long sampleFileSize;

    @TableField(value = "status")
    private String status;

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
}
