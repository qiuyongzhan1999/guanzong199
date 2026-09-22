-- 刷题模块（docs/16）——与当前库字段对齐
-- mysql -u root -p gz199 < server/sql/practice_tables.sql

USE gz199;

CREATE TABLE IF NOT EXISTS questions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  subject VARCHAR(20) NOT NULL COMMENT 'guanzong / english',
  type VARCHAR(30) NOT NULL COMMENT '数学/逻辑/阅读/完形',
  knowledge_point VARCHAR(100) NULL,
  content TEXT NOT NULL,
  options JSON NULL,
  answer VARCHAR(10) NOT NULL,
  analysis TEXT NULL,
  analysis_idea TEXT NULL,
  analysis_kp TEXT NULL,
  difficulty TINYINT DEFAULT 3,
  year INT NULL,
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_subject_type (subject, type),
  INDEX idx_knowledge (knowledge_point)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷题题库';

CREATE TABLE IF NOT EXISTS user_answers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  user_answer VARCHAR(10) NOT NULL,
  is_correct TINYINT NOT NULL,
  time_spent_ms INT NULL,
  mode VARCHAR(20) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录';

CREATE TABLE IF NOT EXISTS user_favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_question (user_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

CREATE TABLE IF NOT EXISTS user_wrong_questions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  wrong_count INT DEFAULT 1,
  last_wrong_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  status TINYINT DEFAULT 0 COMMENT '0未掌握 1已掌握',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_question (user_id, question_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题本';

CREATE TABLE IF NOT EXISTS user_knowledge_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  subject VARCHAR(20) NOT NULL,
  knowledge_point VARCHAR(100) NOT NULL,
  total INT DEFAULT 0,
  correct INT DEFAULT 0,
  mastery_rate DECIMAL(5,1) DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_subject_kp (user_id, subject, knowledge_point)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点掌握度';
