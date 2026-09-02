INSERT INTO t_user_info (username, nick, password, sex, phone, email, address, create_time, update_time)
SELECT 'admin',
       'admin',
       'e10adc3949ba59abbe56e057f20f883e',
       1,
       '',
       'admin@example.com',
       '',
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM t_user_info
    WHERE username = 'admin'
);
