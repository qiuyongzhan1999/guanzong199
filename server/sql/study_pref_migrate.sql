-- 背单词：个人每日目标（不含奖励计划/成就徽章）
-- mysql -u root -p --default-character-set=utf8mb4 gz199 < server/sql/study_pref_migrate.sql

USE gz199;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS user_study_pref (
  user_id       VARCHAR(64)  NOT NULL PRIMARY KEY COMMENT '设备ID/用户ID',
  daily_target  INT NOT NULL DEFAULT 30 COMMENT '每日单词目标（30/50/100/自定义 5～500）',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习偏好（每日目标）';
