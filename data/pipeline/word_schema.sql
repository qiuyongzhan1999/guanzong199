-- 背单词功能建表（管综上岸通 gz199）
USE gz199;

-- 单词表（词库：有道考神考研词书，3 册合并）
CREATE TABLE IF NOT EXISTS word (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  word VARCHAR(64) NOT NULL,
  phonetic VARCHAR(128) DEFAULT '' COMMENT '英式音标',
  phonetic_us VARCHAR(128) DEFAULT '' COMMENT '美式音标',
  meaning_cn TEXT COMMENT '中文释义（多词性，/ 分隔）',
  pos VARCHAR(64) DEFAULT '' COMMENT '词性（v/n/adj...）',
  sentence_en TEXT COMMENT '例句英文',
  sentence_cn TEXT COMMENT '例句中文',
  book VARCHAR(16) DEFAULT 'ky' COMMENT '词书：ky=考研',
  word_rank INT DEFAULT 0 COMMENT '词书内序号',
  source VARCHAR(32) DEFAULT 'kaoyan' COMMENT '来源',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word (word),
  KEY idx_rank (word_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单词表';

-- 学习时长记录（按用户×日期累加）
CREATE TABLE IF NOT EXISTS study_record (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL COMMENT '设备ID/用户ID',
  study_date DATE NOT NULL,
  duration_sec INT NOT NULL DEFAULT 0 COMMENT '当日学习秒数',
  words_seen INT NOT NULL DEFAULT 0 COMMENT '当日浏览单词数',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, study_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习时长记录';

-- 队伍表
CREATE TABLE IF NOT EXISTS team (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL COMMENT '队伍名称',
  creator_id VARCHAR(64) NOT NULL,
  max_members INT NOT NULL DEFAULT 5,
  current_members INT NOT NULL DEFAULT 1,
  daily_target_words INT NOT NULL DEFAULT 20 COMMENT '每日共同目标单词数',
  start_date DATE NOT NULL,
  end_date DATE DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0已解散 1进行中 2已完成',
  invite_code VARCHAR(16) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invite (invite_code),
  KEY idx_creator (creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组队表';

-- 队伍成员表
CREATE TABLE IF NOT EXISTS team_member (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  team_id BIGINT UNSIGNED NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'member' COMMENT 'leader/member',
  team_streak INT NOT NULL DEFAULT 0 COMMENT '队伍内连续打卡天数',
  total_checkin_days INT NOT NULL DEFAULT 0 COMMENT '队伍内累计打卡天数',
  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  is_active TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_team_user (team_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍成员表';

-- 队伍打卡记录表
CREATE TABLE IF NOT EXISTS team_checkin (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  team_id BIGINT UNSIGNED NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  checkin_date DATE NOT NULL,
  words_learned INT NOT NULL DEFAULT 0,
  is_completed TINYINT NOT NULL DEFAULT 0 COMMENT '是否达标（>=队伍每日目标）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_team_user_date (team_id, user_id, checkin_date),
  KEY idx_team_date (team_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍打卡记录表';

-- 学习偏好（每日目标；不含奖励计划）
CREATE TABLE IF NOT EXISTS user_study_pref (
  user_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '设备ID/用户ID',
  daily_target INT NOT NULL DEFAULT 30 COMMENT '每日单词目标（30/50/100/自定义 5～500）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习偏好';