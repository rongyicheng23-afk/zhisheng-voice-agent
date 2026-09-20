package com.wc.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/** Server-enforced visibility for a knowledge document. */
@TableName("t_knowledge_document_visibility")
public class KnowledgeDocumentVisibility {
    @TableId("document_id") private Long documentId;
    @TableField("visibility_scope") private String visibilityScope;
    @TableField("update_time") private Date updateTime;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { documentId = value; }
    public String getVisibilityScope() { return visibilityScope; }
    public void setVisibilityScope(String value) { visibilityScope = value; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date value) { updateTime = value; }
}
