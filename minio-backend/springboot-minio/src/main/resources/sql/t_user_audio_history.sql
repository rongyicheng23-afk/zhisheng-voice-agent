CREATE TABLE IF NOT EXISTS t_user_audio_history (
    id INT PRIMARY KEY AUTO_INCREMENT,
    uid INT NOT NULL,
    bucket VARCHAR(255),
    object VARCHAR(500),
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT DEFAULT 0,
    request_mode VARCHAR(32) NOT NULL,
    funasr_mode VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    transcription LONGTEXT,
    raw_result LONGTEXT,
    error_message VARCHAR(1000),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_audio_uid (uid),
    INDEX idx_user_audio_create_time (create_time)
);
