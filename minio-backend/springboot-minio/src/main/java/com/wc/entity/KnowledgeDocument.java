package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.util.Date;

@TableName("t_knowledge_document")
public class KnowledgeDocument {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("owner_user_id") private Integer ownerUserId;
    @TableField("title") private String title;
    @TableField("source_name") private String sourceName;
    @TableField("version_label") private String versionLabel;
    @TableField("valid_from") private LocalDate validFrom;
    @TableField("valid_until") private LocalDate validUntil;
    @TableField("status") private String status;
    @TableField("bucket_name") private String bucketName;
    @TableField("object_name") private String objectName;
    @TableField("original_filename") private String originalFilename;
    @TableField("content_type") private String contentType;
    @TableField("file_size") private Long fileSize;
    @TableField("create_time") private Date createTime;
    @TableField("update_time") private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Integer value) { this.ownerUserId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { this.sourceName = value; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String value) { this.versionLabel = value; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate value) { this.validFrom = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { this.validUntil = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String value) { this.bucketName = value; }
    public String getObjectName() { return objectName; }
    public void setObjectName(String value) { this.objectName = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { this.contentType = value; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long value) { this.fileSize = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { this.createTime = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { this.updateTime = value; }
}
