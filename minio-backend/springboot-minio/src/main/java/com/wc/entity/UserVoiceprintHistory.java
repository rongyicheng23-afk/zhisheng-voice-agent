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

@TableName(value = "t_user_voiceprint_history")
public class UserVoiceprintHistory implements Serializable {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "uid")
    private Integer uid;

    @TableField(value = "left_bucket")
    private String leftBucket;

    @TableField(value = "left_object")
    private String leftObject;

    @TableField(value = "left_filename")
    private String leftFilename;

    @TableField(value = "left_content_type")
    private String leftContentType;

    @TableField(value = "left_file_size")
    private Long leftFileSize;

    @TableField(value = "right_bucket")
    private String rightBucket;

    @TableField(value = "right_object")
    private String rightObject;

    @TableField(value = "right_filename")
    private String rightFilename;

    @TableField(value = "right_content_type")
    private String rightContentType;

    @TableField(value = "right_file_size")
    private Long rightFileSize;

    @TableField(value = "score")
    private BigDecimal score;

    @TableField(value = "threshold_value")
    private BigDecimal thresholdValue;

    @TableField(value = "same_person")
    private Boolean samePerson;

    @TableField(value = "result_message")
    private String resultMessage;

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
