INSERT INTO sys_user (id, username, password_hash, display_name, role, status)
VALUES (
    1,
    'admin',
    '${adminPasswordHash}',
    '系统管理员',
    'ADMIN',
    'ENABLED'
);
