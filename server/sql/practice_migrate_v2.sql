-- 将旧刷题表迁到 V2（subjects/chapters/knowledge_points + questions.subject_id）
-- 保留 questions 原 id，答题/错题/收藏外键可继续用。
-- 用法（务必 utf8mb4）：
--   mysql -u root -p --default-character-set=utf8mb4 gz199 < server/sql/practice_migrate_v2.sql

USE gz199;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. 旧表改名备份 ----------
RENAME TABLE questions TO questions_legacy;
RENAME TABLE user_answers TO user_answers_legacy;
RENAME TABLE user_favorites TO user_favorites_legacy;
RENAME TABLE user_wrong_questions TO user_wrong_questions_legacy;
RENAME TABLE user_knowledge_stats TO user_knowledge_stats_legacy;

DROP TABLE IF EXISTS knowledge_points;
DROP TABLE IF EXISTS chapters;
DROP TABLE IF EXISTS subjects;

-- ---------- 2. V2 知识树 ----------
CREATE TABLE subjects (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  code          VARCHAR(32)  NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  short_name    VARCHAR(32)  NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_subjects_code (code),
  KEY idx_subjects_sort (sort_order, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷题科目';

CREATE TABLE chapters (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  subject_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_chapters_subject (subject_id, sort_order),
  CONSTRAINT fk_chapters_subject FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节/模块';

CREATE TABLE knowledge_points (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id    BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kp_chapter (chapter_id, sort_order),
  CONSTRAINT fk_kp_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点';

INSERT INTO subjects (id, code, name, short_name, sort_order) VALUES
(1, 'math',    '管综数学', '数学', 1),
(2, 'logic',   '管综逻辑', '逻辑', 2),
(3, 'english', '英语二',   '英语二', 3);

INSERT INTO chapters (id, subject_id, name, sort_order) VALUES
(1,  1, '第一章 算术', 1),
(2,  1, '第二章 整式与分式', 2),
(3,  1, '第三章 函数', 3),
(4,  1, '第四章 方程与不等式', 4),
(5,  1, '第五章 数列', 5),
(6,  1, '第六章 平面几何', 6),
(7,  1, '第七章 立体几何', 7),
(8,  1, '第八章 平面解析几何', 8),
(9,  1, '第九章 计数原理', 9),
(10, 1, '第十章 概率', 10),
(11, 1, '第十一章 数据描述', 11),
(12, 2, '形式逻辑', 1),
(13, 2, '综合推理', 2),
(14, 2, '论证逻辑', 3),
(15, 3, '完形填空', 1),
(16, 3, '阅读理解', 2),
(17, 3, '英译汉', 3),
(18, 3, '写作基础', 4);

INSERT INTO knowledge_points (chapter_id, name, sort_order) VALUES
(1, '整数', 1), (1, '分数小数百分数', 2), (1, '数轴与绝对值', 3), (1, '平均值', 4),
(2, '整式', 1), (2, '分式', 2),
(3, '一元二次函数', 1), (3, '指数函数', 2), (3, '对数函数', 3), (3, '幂函数', 4),
(4, '一元一次方程', 1), (4, '一元二次方程', 2), (4, '不等式', 3), (4, '应用', 4),
(5, '等差数列', 1), (5, '等比数列', 2), (5, '数列求和', 3), (5, '综合应用', 4),
(6, '三角形', 1), (6, '四边形', 2), (6, '圆与扇形', 3), (6, '综合', 4),
(7, '长方体', 1), (7, '圆柱体', 2), (7, '圆锥体', 3), (7, '综合', 4),
(8, '坐标系', 1), (8, '直线方程', 2), (8, '圆的方程', 3), (8, '综合', 4),
(9, '加法乘法原理', 1), (9, '排列', 2), (9, '组合', 3), (9, '综合应用', 4),
(10, '古典概型', 1), (10, '独立事件', 2), (10, '条件概率', 3), (10, '综合应用', 4),
(11, '平均值', 1), (11, '方差与标准差', 2), (11, '图表分析', 3),
(12, '概念', 1), (12, '简单命题', 2), (12, '复合命题', 3), (12, '模态命题', 4),
(13, '关系命题', 1), (13, '数学相关推理', 2), (13, '排列组合推理', 3),
(14, '论证方式分析', 1), (14, '论证评价', 2), (14, '谬误识别', 3),
(15, '词汇辨析', 1), (15, '逻辑衔接', 2), (15, '固定搭配', 3),
(16, '细节题', 1), (16, '主旨题', 2), (16, '态度题', 3), (16, '词义句意题', 4),
(16, '推断题', 5), (16, '例证题', 6), (16, '新题型', 7),
(17, '句子主干拆分', 1), (17, '语序调整', 2), (17, '热点话题翻译', 3),
(18, '小作文要点', 1), (18, '大作文框架', 2);

-- ---------- 3. V2 题目 / 用户表 ----------
CREATE TABLE questions (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  subject_id          BIGINT       NOT NULL,
  chapter_id          BIGINT       NOT NULL,
  knowledge_point_id  BIGINT       NOT NULL,
  question_type       VARCHAR(32)  NOT NULL DEFAULT 'single',
  stem                TEXT         NOT NULL,
  options_json        JSON         NULL,
  answer              VARCHAR(64)  NOT NULL,
  analysis            TEXT         NULL,
  analysis_idea       TEXT         NULL,
  difficulty          TINYINT      NOT NULL DEFAULT 3,
  source_tag          VARCHAR(128) NULL,
  year                INT          NULL,
  status              TINYINT      NOT NULL DEFAULT 1,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_q_subject (subject_id, status),
  KEY idx_q_chapter (chapter_id, status),
  KEY idx_q_kp (knowledge_point_id, status),
  KEY idx_q_diff (difficulty),
  KEY idx_q_type (question_type),
  CONSTRAINT fk_q_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
  CONSTRAINT fk_q_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id),
  CONSTRAINT fk_q_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库';

CREATE TABLE user_answers (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  question_id     BIGINT       NOT NULL,
  user_answer     VARCHAR(64)  NOT NULL,
  is_correct      TINYINT      NOT NULL,
  time_spent_ms   INT          NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ua_user_time (user_id, created_at),
  KEY idx_ua_question (question_id),
  CONSTRAINT fk_ua_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录';

CREATE TABLE user_favorites (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  question_id     BIGINT       NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fav_user_q (user_id, question_id),
  KEY idx_fav_user_time (user_id, created_at),
  CONSTRAINT fk_fav_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

CREATE TABLE user_wrong_questions (
  id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id                   BIGINT       NOT NULL,
  question_id               BIGINT       NOT NULL,
  wrong_count               INT          NOT NULL DEFAULT 1,
  consecutive_correct_count INT          NOT NULL DEFAULT 0,
  last_wrong_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  auto_removed              TINYINT      NOT NULL DEFAULT 0,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wrong_user_q (user_id, question_id),
  KEY idx_wrong_active (user_id, auto_removed, last_wrong_at),
  KEY idx_wrong_count (user_id, wrong_count),
  CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题册';

CREATE TABLE user_knowledge_stats (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT         NOT NULL,
  knowledge_point_id  BIGINT         NOT NULL,
  total_count         INT            NOT NULL DEFAULT 0,
  correct_count       INT            NOT NULL DEFAULT 0,
  mastery_rate        DECIMAL(5,1)   NOT NULL DEFAULT 0.0,
  updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uks_user_kp (user_id, knowledge_point_id),
  KEY idx_uks_mastery (user_id, mastery_rate),
  CONSTRAINT fk_uks_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点掌握度';

-- ---------- 4. 迁题目（按旧 type / knowledge_point 映射到章节知识点） ----------
INSERT INTO questions (
  id, subject_id, chapter_id, knowledge_point_id, question_type,
  stem, options_json, answer, analysis, analysis_idea,
  difficulty, source_tag, year, status, created_at, updated_at
)
SELECT
  q.id,
  CASE
    WHEN q.type = '数学' THEN 1
    WHEN q.type = '逻辑' THEN 2
    ELSE 3
  END AS subject_id,
  CASE q.knowledge_point
    WHEN '方程' THEN 4
    WHEN '工程' THEN 4
    WHEN '应用题' THEN 4
    WHEN '数列' THEN 5
    WHEN '概率' THEN 10
    WHEN '平均值' THEN 1
    WHEN '充分必要' THEN 12
    WHEN '真假话' THEN 13
    WHEN '结论题' THEN 14
    WHEN '论证逻辑' THEN 14
    WHEN '时态语态' THEN 15
    WHEN '从句' THEN 15
    WHEN '逻辑衔接' THEN 16
    WHEN '细节理解' THEN 16
    WHEN '词义' THEN 16
    ELSE CASE
      WHEN q.type = '数学' THEN 4
      WHEN q.type = '逻辑' THEN 14
      WHEN q.type = '完形' THEN 15
      WHEN q.type = '阅读' THEN 16
      ELSE 15
    END
  END AS chapter_id,
  CASE q.knowledge_point
    WHEN '方程' THEN (SELECT id FROM knowledge_points WHERE chapter_id=4 AND name='一元一次方程' LIMIT 1)
    WHEN '工程' THEN (SELECT id FROM knowledge_points WHERE chapter_id=4 AND name='应用' LIMIT 1)
    WHEN '应用题' THEN (SELECT id FROM knowledge_points WHERE chapter_id=4 AND name='应用' LIMIT 1)
    WHEN '数列' THEN (SELECT id FROM knowledge_points WHERE chapter_id=5 AND name='等差数列' LIMIT 1)
    WHEN '概率' THEN (SELECT id FROM knowledge_points WHERE chapter_id=10 AND name='古典概型' LIMIT 1)
    WHEN '平均值' THEN (SELECT id FROM knowledge_points WHERE chapter_id=1 AND name='平均值' LIMIT 1)
    WHEN '充分必要' THEN (SELECT id FROM knowledge_points WHERE chapter_id=12 AND name='简单命题' LIMIT 1)
    WHEN '真假话' THEN (SELECT id FROM knowledge_points WHERE chapter_id=13 AND name='关系命题' LIMIT 1)
    WHEN '结论题' THEN (SELECT id FROM knowledge_points WHERE chapter_id=14 AND name='论证评价' LIMIT 1)
    WHEN '论证逻辑' THEN (SELECT id FROM knowledge_points WHERE chapter_id=14 AND name='论证方式分析' LIMIT 1)
    WHEN '时态语态' THEN (SELECT id FROM knowledge_points WHERE chapter_id=15 AND name='固定搭配' LIMIT 1)
    WHEN '从句' THEN (SELECT id FROM knowledge_points WHERE chapter_id=15 AND name='固定搭配' LIMIT 1)
    WHEN '逻辑衔接' THEN (SELECT id FROM knowledge_points WHERE chapter_id=16 AND name='细节题' LIMIT 1)
    WHEN '细节理解' THEN (SELECT id FROM knowledge_points WHERE chapter_id=16 AND name='细节题' LIMIT 1)
    WHEN '词义' THEN (SELECT id FROM knowledge_points WHERE chapter_id=16 AND name='词义句意题' LIMIT 1)
    ELSE (SELECT id FROM knowledge_points WHERE chapter_id=15 AND name='词汇辨析' LIMIT 1)
  END AS knowledge_point_id,
  'single',
  q.content,
  q.options,
  q.answer,
  q.analysis,
  q.analysis_idea,
  IFNULL(q.difficulty, 3),
  'legacy',
  q.year,
  IFNULL(q.status, 1),
  IFNULL(q.created_at, NOW()),
  IFNULL(q.updated_at, NOW())
FROM questions_legacy q;

-- ---------- 5. 迁用户数据 ----------
INSERT INTO user_answers (id, user_id, question_id, user_answer, is_correct, time_spent_ms, created_at)
SELECT id, user_id, question_id, user_answer, is_correct, time_spent_ms, IFNULL(created_at, NOW())
FROM user_answers_legacy
WHERE question_id IN (SELECT id FROM questions);

INSERT INTO user_favorites (id, user_id, question_id, created_at)
SELECT id, user_id, question_id, IFNULL(created_at, NOW())
FROM user_favorites_legacy
WHERE question_id IN (SELECT id FROM questions);

INSERT INTO user_wrong_questions (
  id, user_id, question_id, wrong_count, consecutive_correct_count,
  last_wrong_at, auto_removed, updated_at
)
SELECT
  id, user_id, question_id, IFNULL(wrong_count, 1), 0,
  IFNULL(last_wrong_at, NOW()),
  CASE WHEN IFNULL(status, 0) = 1 THEN 1 ELSE 0 END,
  IFNULL(updated_at, NOW())
FROM user_wrong_questions_legacy
WHERE question_id IN (SELECT id FROM questions);

SET FOREIGN_KEY_CHECKS = 1;

-- 校验
SELECT
  (SELECT COUNT(*) FROM questions) AS questions,
  (SELECT COUNT(*) FROM subjects) AS subjects,
  (SELECT name FROM subjects WHERE id=1) AS math_name,
  (SELECT COUNT(*) FROM user_answers) AS answers,
  (SELECT COUNT(*) FROM user_wrong_questions) AS wrongs;
