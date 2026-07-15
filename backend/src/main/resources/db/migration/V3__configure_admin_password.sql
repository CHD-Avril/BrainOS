UPDATE sys_user
SET password_hash = '${adminPasswordHash}'
WHERE id = 1
  AND username = 'admin';
