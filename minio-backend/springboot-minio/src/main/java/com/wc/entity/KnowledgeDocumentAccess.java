package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/** Explicit recipients of a TEAM-visible document. */
@TableName("t_knowledge_document_access")
public class KnowledgeDocumentAccess {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    @TableField("document_id") private Long documentId;
    @TableField("user_id") private Integer userId;
    @TableField("create_time") private Date createTime;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { documentId = value; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { createTime = value; }
}
