CREATE TABLE IF NOT EXISTS t_user_role_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_code),
    KEY idx_user_role_user_id (user_id)
) COMMENT='用户角色分配；权限由服务端校验';
