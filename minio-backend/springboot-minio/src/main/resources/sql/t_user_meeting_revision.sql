CREATE TABLE IF NOT EXISTS t_user_meeting_revision
(
    id                   INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    meeting_id           INT          NOT NULL COMMENT '纪要ID',
    version_no           INT          NOT NULL COMMENT '版本号',
    revision_type        VARCHAR(32)  NOT NULL COMMENT '版本类型 AUTO/MANUAL',
    title                VARCHAR(128) NULL COMMENT '纪要标题',
    summary_text         LONGTEXT     NULL COMMENT '摘要文本',
    keywords_json        LONGTEXT     NULL COMMENT '关键词JSON',
    todo_json            LONGTEXT     NULL COMMENT '待办JSON',
    full_transcript      LONGTEXT     NULL COMMENT '全文转写',
    speaker_transcript   LONGTEXT     NULL COMMENT '发言人纪要',
    speaker_blocks_json  LONGTEXT     NULL COMMENT '整理后发言块JSON',
    speaker_segments_json LONGTEXT    NULL COMMENT '原始片段JSON',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_meeting_id (meeting_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户纪要版本快照表';
