CREATE TABLE IF NOT EXISTS t_user_info (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(64) NULL COMMENT '登录用户名',
    nick VARCHAR(64) NULL COMMENT '昵称',
    password VARCHAR(64) NULL COMMENT '密码',
    sex INT DEFAULT 1 COMMENT '性别',
    phone VARCHAR(32) DEFAULT '' COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    address VARCHAR(255) DEFAULT '' COMMENT '地址',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='用户信息表';

CREATE TABLE IF NOT EXISTS t_user_image (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    uid INT NOT NULL COMMENT '用户ID',
    bucket VARCHAR(128) DEFAULT NULL COMMENT 'MinIO bucket',
    object VARCHAR(255) DEFAULT NULL COMMENT '对象路径',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_t_user_image_uid (uid)
) COMMENT='用户头像表';

CREATE TABLE IF NOT EXISTS t_user_contract (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    uid INT NOT NULL COMMENT '用户ID',
    bucket VARCHAR(128) DEFAULT NULL COMMENT 'MinIO bucket',
    object VARCHAR(255) DEFAULT NULL COMMENT '对象路径',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_t_user_contract_uid (uid)
) COMMENT='用户合同表';

SET @username_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_user_info'
      AND COLUMN_NAME = 'username'
);

SET @auth_schema_sql := IF(
    @username_column_exists = 0,
    'ALTER TABLE t_user_info ADD COLUMN username VARCHAR(64) NULL COMMENT ''登录用户名'' AFTER id',
    'SELECT 1'
);

PREPARE auth_schema_stmt FROM @auth_schema_sql;
EXECUTE auth_schema_stmt;
DEALLOCATE PREPARE auth_schema_stmt;

UPDATE t_user_info
SET username = nick
WHERE (username IS NULL OR username = '')
  AND nick IS NOT NULL
  AND nick <> '';
