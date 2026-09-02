CREATE TABLE IF NOT EXISTS t_user_speaker_profile (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    uid INT NOT NULL COMMENT '用户ID',
    speaker_name VARCHAR(64) NOT NULL COMMENT '发言人名称',
    speaker_role VARCHAR(64) DEFAULT NULL COMMENT '发言人角色',
    sample_bucket VARCHAR(128) DEFAULT NULL COMMENT '样本音频 bucket',
    sample_object VARCHAR(255) DEFAULT NULL COMMENT '样本音频对象路径',
    sample_filename VARCHAR(255) DEFAULT NULL COMMENT '样本音频文件名',
    sample_content_type VARCHAR(128) DEFAULT NULL COMMENT '样本音频 content-type',
    sample_file_size BIGINT DEFAULT 0 COMMENT '样本音频大小',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DELETED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_t_user_speaker_profile_uid (uid),
    KEY idx_t_user_speaker_profile_create_time (create_time)
) COMMENT='用户发言人声纹档案表';
