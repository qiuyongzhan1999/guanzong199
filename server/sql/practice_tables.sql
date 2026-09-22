-- 刷题模块 V2：科目 / 章节 / 知识点 / 题目 / 用户数据
-- 旧表若结构不符会 DROP 重建（会清空刷题相关数据）
-- mysql -u root -p gz199 < server/sql/practice_tables.sql

USE gz199;

SET NAMES utf8mb4;

-- ---------- 清理旧刷题表 ----------
DROP TABLE IF EXISTS user_knowledge_stats;
DROP TABLE IF EXISTS user_wrong_questions;
DROP TABLE IF EXISTS user_favorites;
DROP TABLE IF EXISTS user_answers;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS knowledge_points;
DROP TABLE IF EXISTS chapters;
DROP TABLE IF EXISTS subjects;

-- ---------- 1. 科目 ----------
CREATE TABLE subjects (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  code          VARCHAR(32)  NOT NULL COMMENT 'math / logic / english',
  name          VARCHAR(64)  NOT NULL COMMENT '展示名',
  short_name    VARCHAR(32)  NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_subjects_code (code),
  KEY idx_subjects_sort (sort_order, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷题科目';

-- ---------- 2. 章节 ----------
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

-- ---------- 3. 知识点 ----------
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

-- ---------- 4. 题目 ----------
CREATE TABLE questions (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  subject_id          BIGINT       NOT NULL,
  chapter_id          BIGINT       NOT NULL,
  knowledge_point_id  BIGINT       NOT NULL,
  question_type       VARCHAR(32)  NOT NULL DEFAULT 'single' COMMENT 'single/multi/blank',
  stem                TEXT         NOT NULL COMMENT '题干',
  options_json        JSON         NULL COMMENT '[{key,text}]',
  answer              VARCHAR(64)  NOT NULL COMMENT '标准答案，如 A 或 A,B',
  analysis            TEXT         NULL COMMENT '详解',
  analysis_idea       TEXT         NULL COMMENT '破题思路',
  difficulty          TINYINT      NOT NULL DEFAULT 3 COMMENT '1-5',
  source_tag          VARCHAR(128) NULL COMMENT '来源标记，如 样例/自编',
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

-- ---------- 5. 答题记录 ----------
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

-- ---------- 6. 收藏 ----------
CREATE TABLE user_favorites (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  question_id     BIGINT       NOT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fav_user_q (user_id, question_id),
  KEY idx_fav_user_time (user_id, created_at),
  CONSTRAINT fk_fav_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

-- ---------- 7. 错题（连续答对>=2 自动移出） ----------
CREATE TABLE user_wrong_questions (
  id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id                   BIGINT       NOT NULL,
  question_id               BIGINT       NOT NULL,
  wrong_count               INT          NOT NULL DEFAULT 1,
  consecutive_correct_count INT          NOT NULL DEFAULT 0 COMMENT '连续答对次数',
  last_wrong_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  auto_removed              TINYINT      NOT NULL DEFAULT 0 COMMENT '1=连续答对>=2已移出',
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wrong_user_q (user_id, question_id),
  KEY idx_wrong_active (user_id, auto_removed, last_wrong_at),
  KEY idx_wrong_count (user_id, wrong_count),
  CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES questions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题册';

-- ---------- 8. 知识点掌握度 ----------
CREATE TABLE user_knowledge_stats (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id             BIGINT         NOT NULL,
  knowledge_point_id  BIGINT         NOT NULL,
  total_count         INT            NOT NULL DEFAULT 0,
  correct_count       INT            NOT NULL DEFAULT 0,
  mastery_rate        DECIMAL(5,1)   NOT NULL DEFAULT 0.0 COMMENT '0-100',
  updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uks_user_kp (user_id, knowledge_point_id),
  KEY idx_uks_mastery (user_id, mastery_rate),
  CONSTRAINT fk_uks_kp FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点掌握度';

-- ==================== 知识树种子 ====================

INSERT INTO subjects (id, code, name, short_name, sort_order) VALUES
(1, 'math',    '管综数学', '数学', 1),
(2, 'logic',   '管综逻辑', '逻辑', 2),
(3, 'english', '英语二',   '英语二', 3);

-- 数学 11 章
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
(11, 1, '第十一章 数据描述', 11);

-- 逻辑 3 模块
INSERT INTO chapters (id, subject_id, name, sort_order) VALUES
(12, 2, '形式逻辑', 1),
(13, 2, '综合推理', 2),
(14, 2, '论证逻辑', 3);

-- 英语 4 模块（英译汉合并为一章；阅读含新题型）
INSERT INTO chapters (id, subject_id, name, sort_order) VALUES
(15, 3, '完形填空', 1),
(16, 3, '阅读理解', 2),
(17, 3, '英译汉', 3),
(18, 3, '写作基础', 4);

-- 数学知识点
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
(11, '平均值', 1), (11, '方差与标准差', 2), (11, '图表分析', 3);

-- 逻辑知识点
INSERT INTO knowledge_points (chapter_id, name, sort_order) VALUES
(12, '概念', 1), (12, '简单命题', 2), (12, '复合命题', 3), (12, '模态命题', 4),
(13, '关系命题', 1), (13, '数学相关推理', 2), (13, '排列组合推理', 3),
(14, '论证方式分析', 1), (14, '论证评价', 2), (14, '谬误识别', 3);

-- 英语知识点
INSERT INTO knowledge_points (chapter_id, name, sort_order) VALUES
(15, '词汇辨析', 1), (15, '逻辑衔接', 2), (15, '固定搭配', 3),
(16, '细节题', 1), (16, '主旨题', 2), (16, '态度题', 3), (16, '词义句意题', 4),
(16, '推断题', 5), (16, '例证题', 6), (16, '新题型', 7),
(17, '句子主干拆分', 1), (17, '语序调整', 2), (17, '热点话题翻译', 3),
(18, '小作文要点', 1), (18, '大作文框架', 2);

-- ==================== 样例题（联调用，上线前替换正版题库） ====================

INSERT INTO questions (subject_id, chapter_id, knowledge_point_id, question_type, stem, options_json, answer, analysis, analysis_idea, difficulty, source_tag) VALUES
(1, 1, (SELECT id FROM knowledge_points WHERE chapter_id=1 AND name='整数' LIMIT 1),
 'single',
 '若正整数 n 满足 2n+1=15，则 n 等于？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','6'), JSON_OBJECT('key','B','text','7'), JSON_OBJECT('key','C','text','8'), JSON_OBJECT('key','D','text','9')),
 'B', '由 2n+1=15 得 2n=14，n=7。', '移项解一元方程。', 1, '样例'),
(1, 5, (SELECT id FROM knowledge_points WHERE chapter_id=5 AND name='等差数列' LIMIT 1),
 'single',
 '等差数列 {a_n} 中 a_1=3，公差 d=2，则 a_5=？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','9'), JSON_OBJECT('key','B','text','11'), JSON_OBJECT('key','C','text','13'), JSON_OBJECT('key','D','text','15')),
 'B', 'a_n=a_1+(n-1)d=3+4×2=11。', '通项公式。', 2, '样例'),
(1, 10, (SELECT id FROM knowledge_points WHERE chapter_id=10 AND name='古典概型' LIMIT 1),
 'single',
 '掷一枚均匀硬币一次，正面朝上的概率是？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','0'), JSON_OBJECT('key','B','text','1/4'), JSON_OBJECT('key','C','text','1/2'), JSON_OBJECT('key','D','text','1')),
 'C', '两种等可能结果，正面概率 1/2。', '古典概型基本定义。', 1, '样例'),

(2, 12, (SELECT id FROM knowledge_points WHERE chapter_id=12 AND name='复合命题' LIMIT 1),
 'single',
 '「如果下雨，那么地面湿」的否定是？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','如果下雨，那么地面不湿'), JSON_OBJECT('key','B','text','下雨且地面不湿'), JSON_OBJECT('key','C','text','如果不下雨，那么地面湿'), JSON_OBJECT('key','D','text','不下雨或地面湿')),
 'B', '「P→Q」的否定是「P∧¬Q」。', '充分条件假言命题的否定。', 3, '样例'),
(2, 14, (SELECT id FROM knowledge_points WHERE chapter_id=14 AND name='谬误识别' LIMIT 1),
 'single',
 '以下哪项属于「诉诸权威」谬误？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','因为专家说如此，所以结论成立'), JSON_OBJECT('key','B','text','前提真且推理有效'), JSON_OBJECT('key','C','text','用数据直接证明'), JSON_OBJECT('key','D','text','指出对方自相矛盾')),
 'A', '仅因权威表态就接受结论，未检验论据本身。', '识别论证谬误类型。', 2, '样例'),

(3, 15, (SELECT id FROM knowledge_points WHERE chapter_id=15 AND name='固定搭配' LIMIT 1),
 'single',
 'He is good ____ English.',
 JSON_ARRAY(JSON_OBJECT('key','A','text','at'), JSON_OBJECT('key','B','text','in'), JSON_OBJECT('key','C','text','on'), JSON_OBJECT('key','D','text','for')),
 'A', '固定搭配 be good at。', '完形常见固定搭配。', 1, '样例'),
(3, 16, (SELECT id FROM knowledge_points WHERE chapter_id=16 AND name='主旨题' LIMIT 1),
 'single',
 '主旨题通常优先看？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','首段与尾段中心句'), JSON_OBJECT('key','B','text','只看例子细节'), JSON_OBJECT('key','C','text','只看生词'), JSON_OBJECT('key','D','text','只看选项长短')),
 'A', '主旨多在首尾或各段主题句。', '阅读主旨定位策略。', 2, '样例'),
(3, 17, (SELECT id FROM knowledge_points WHERE chapter_id=17 AND name='句子主干拆分' LIMIT 1),
 'single',
 '英译汉时优先处理？',
 JSON_ARRAY(JSON_OBJECT('key','A','text','修饰语堆砌'), JSON_OBJECT('key','B','text','句子主干主谓宾'), JSON_OBJECT('key','C','text','专有名词音译'), JSON_OBJECT('key','D','text','标点符号')),
 'B', '先抓主干再挂修饰，语序更稳。', '翻译步骤：主干→修饰。', 2, '样例');
