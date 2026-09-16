CREATE TABLE IF NOT EXISTS t_user_realtime_chat_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id INT NOT NULL COMMENT '所属登录用户ID',
    role VARCHAR(16) NOT NULL COMMENT 'user 或 assistant',
    content TEXT NOT NULL COMMENT '对话文本',
    input_mode VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text 或 voice',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_realtime_chat_user_time (user_id, create_time, id)
) COMMENT='实时语音与文本对话历史';
