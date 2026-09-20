CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识资料ID',
    owner_user_id INT NOT NULL COMMENT '资料所有者和首期权限范围',
    title VARCHAR(255) NOT NULL COMMENT '展示名称',
    source_name VARCHAR(255) NOT NULL COMMENT '资料来源或发布单位',
    version_label VARCHAR(64) NOT NULL DEFAULT 'v1' COMMENT '资料版本',
    valid_from DATE NULL COMMENT '生效日期',
    valid_until DATE NULL COMMENT '失效日期',
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT PUBLISHED ARCHIVED',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'MinIO私有桶',
    object_name VARCHAR(512) NOT NULL COMMENT 'MinIO对象路径',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(128) NULL COMMENT '文件类型',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '字节数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_knowledge_document_owner_status (owner_user_id, status),
    KEY idx_knowledge_document_valid_until (valid_until)
) COMMENT='RAG知识资料元数据';

CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识片段ID',
    document_id BIGINT NOT NULL COMMENT '所属资料ID',
    chunk_no INT NOT NULL COMMENT '资料内顺序',
    page_no INT NULL COMMENT '页码或段落页号',
    content MEDIUMTEXT NOT NULL COMMENT '已解析文本片段',
    content_hash CHAR(64) NOT NULL COMMENT '片段内容哈希',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_chunk_document_no (document_id, chunk_no),
    KEY idx_knowledge_chunk_document (document_id)
) COMMENT='RAG知识资料切片';

-- Kept in separate tables so databases created before visibility support keep
-- working without a destructive ALTER. Missing rows are treated as PRIVATE.
CREATE TABLE IF NOT EXISTS t_knowledge_document_visibility (
    document_id BIGINT PRIMARY KEY COMMENT '知识资料ID',
    visibility_scope VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE TEAM PUBLIC',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_knowledge_visibility_scope (visibility_scope)
) COMMENT='知识资料可见范围；由后端检索时强制校验';

CREATE TABLE IF NOT EXISTS t_knowledge_document_access (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_document_access (document_id, user_id),
    KEY idx_knowledge_access_user (user_id),
    KEY idx_knowledge_access_document (document_id)
) COMMENT='TEAM资料的显式授权成员';
