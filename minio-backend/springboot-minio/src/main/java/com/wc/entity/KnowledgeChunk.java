package com.wc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

@TableName("t_knowledge_chunk")
public class KnowledgeChunk {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("document_id") private Long documentId;
    @TableField("chunk_no") private Integer chunkNo;
    @TableField("page_no") private Integer pageNo;
    @TableField("content") private String content;
    @TableField("content_hash") private String contentHash;
    @TableField("create_time") private Date createTime;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long value) { this.documentId = value; }
    public Integer getChunkNo() { return chunkNo; }
    public void setChunkNo(Integer value) { this.chunkNo = value; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer value) { this.pageNo = value; }
    public String getContent() { return content; }
    public void setContent(String value) { this.content = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { this.contentHash = value; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date value) { this.createTime = value; }
}
