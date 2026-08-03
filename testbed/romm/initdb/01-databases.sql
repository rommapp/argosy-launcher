-- One database per RomM version. Schema migrations are one-way, so a database
-- a newer RomM has migrated is not readable by an older one; sharing a single
-- database across versions would measure migration damage rather than the
-- response shape each version actually ships.

CREATE DATABASE IF NOT EXISTS romm_v50 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS romm_v51 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON romm_v49.* TO 'romm-user'@'%';
GRANT ALL PRIVILEGES ON romm_v50.* TO 'romm-user'@'%';
GRANT ALL PRIVILEGES ON romm_v51.* TO 'romm-user'@'%';
FLUSH PRIVILEGES;
