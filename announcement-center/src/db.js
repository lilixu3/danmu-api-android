const mysql = require('mysql2/promise');

function createPoolFromEnv() {
  return mysql.createPool({
    host: process.env.MYSQL_HOST || '127.0.0.1',
    port: Number(process.env.MYSQL_PORT || 3306),
    database: process.env.MYSQL_DATABASE,
    user: process.env.MYSQL_USER,
    password: process.env.MYSQL_PASSWORD,
    charset: 'utf8mb4',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
  });
}

async function ensureSchema(pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS admins (
      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      username VARCHAR(64) NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      last_login_at DATETIME NULL,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_admins_username (username)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS announcements (
      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
      announcement_key VARCHAR(64) NOT NULL,
      title VARCHAR(160) NOT NULL,
      summary VARCHAR(320) NOT NULL DEFAULT '',
      content_markdown MEDIUMTEXT NOT NULL,
      content_preview VARCHAR(400) NOT NULL DEFAULT '',
      cover_image_url VARCHAR(500) NULL,
      announcement_type VARCHAR(16) NOT NULL DEFAULT 'long',
      push_version INT UNSIGNED NOT NULL DEFAULT 1,
      severity VARCHAR(16) NOT NULL DEFAULT 'info',
      status VARCHAR(16) NOT NULL DEFAULT 'draft',
      popup_enabled TINYINT(1) NOT NULL DEFAULT 1,
      force_popup TINYINT(1) NOT NULL DEFAULT 0,
      allow_snooze_today TINYINT(1) NOT NULL DEFAULT 1,
      target_variants_json TEXT NOT NULL,
      min_app_version VARCHAR(32) NULL,
      max_app_version VARCHAR(32) NULL,
      start_at DATETIME NULL,
      end_at DATETIME NULL,
      primary_button_text VARCHAR(80) NULL,
      primary_button_url VARCHAR(500) NULL,
      primary_button_mode VARCHAR(16) NOT NULL DEFAULT 'none',
      secondary_button_text VARCHAR(80) NULL,
      secondary_button_url VARCHAR(500) NULL,
      secondary_button_mode VARCHAR(16) NOT NULL DEFAULT 'none',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      published_at DATETIME NULL,
      PRIMARY KEY (id),
      UNIQUE KEY uniq_announcements_key (announcement_key),
      KEY idx_announcements_status_time (status, start_at, end_at, published_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  `);

  const [typeColumns] = await pool.query(`
    SELECT COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'announcements'
      AND COLUMN_NAME = 'announcement_type'
    LIMIT 1
  `);

  if (typeColumns.length === 0) {
    await pool.query(`
      ALTER TABLE announcements
      ADD COLUMN announcement_type VARCHAR(16) NOT NULL DEFAULT 'long'
      AFTER cover_image_url
    `);
  }

  const [pushVersionColumns] = await pool.query(`
    SELECT COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'announcements'
      AND COLUMN_NAME = 'push_version'
    LIMIT 1
  `);

  if (pushVersionColumns.length === 0) {
    await pool.query(`
      ALTER TABLE announcements
      ADD COLUMN push_version INT UNSIGNED NOT NULL DEFAULT 1
      AFTER announcement_type
    `);
  }

  const [primaryModeColumns] = await pool.query(`
    SELECT COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'announcements'
      AND COLUMN_NAME = 'primary_button_mode'
    LIMIT 1
  `);

  if (primaryModeColumns.length === 0) {
    await pool.query(`
      ALTER TABLE announcements
      ADD COLUMN primary_button_mode VARCHAR(16) NOT NULL DEFAULT 'none'
      AFTER primary_button_url
    `);
  }

  const [secondaryModeColumns] = await pool.query(`
    SELECT COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'announcements'
      AND COLUMN_NAME = 'secondary_button_mode'
    LIMIT 1
  `);

  if (secondaryModeColumns.length === 0) {
    await pool.query(`
      ALTER TABLE announcements
      ADD COLUMN secondary_button_mode VARCHAR(16) NOT NULL DEFAULT 'none'
      AFTER secondary_button_url
    `);
  }

  await pool.query(`
    UPDATE announcements
    SET primary_button_mode = CASE
      WHEN primary_button_text IS NOT NULL AND primary_button_text <> ''
        AND primary_button_url IS NOT NULL AND primary_button_url <> ''
      THEN 'link'
      ELSE 'none'
    END
    WHERE primary_button_mode = 'none'
  `);

  await pool.query(`
    UPDATE announcements
    SET secondary_button_mode = CASE
      WHEN secondary_button_text IS NOT NULL AND secondary_button_text <> ''
        AND secondary_button_url IS NOT NULL AND secondary_button_url <> ''
      THEN 'link'
      ELSE 'none'
    END
    WHERE secondary_button_mode = 'none'
  `);

  await pool.query(`
    UPDATE announcements
    SET push_version = 1
    WHERE push_version IS NULL OR push_version < 1
  `);
}

module.exports = {
  createPoolFromEnv,
  ensureSchema,
};
