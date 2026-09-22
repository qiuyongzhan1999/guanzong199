-- 管综199 · MySQL 8 建表（utf8mb4）
-- 对齐当前前端已用字段：院校名片、专业目录、培养信息、国家线、招录、近5年包。
-- 用法：先建库，再 source 本文件。

CREATE DATABASE IF NOT EXISTS gz199
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE gz199;

-- ---------------------------------------------------------------------------
-- 1. 院校名片（对应 data/schools.json + 研招网 schId）
-- ---------------------------------------------------------------------------
CREATE TABLE schools (
  id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  sch_id           VARCHAR(32)  NOT NULL COMMENT '研招网 schId，详情页路由用',
  code             VARCHAR(16)  NOT NULL COMMENT '单位代码，如 10034，校徽路径用',
  name             VARCHAR(128) NOT NULL,
  province         VARCHAR(32)  NULL,
  city             VARCHAR(32)  NULL,
  authority        VARCHAR(64)  NULL COMMENT '主管部门',
  logo_url         VARCHAR(512) NULL,
  tags_json        JSON         NULL COMMENT '双一流等标签数组',
  flags_json       JSON         NULL COMMENT '研究生院/自划线等',
  is_double_first  TINYINT(1)   NOT NULL DEFAULT 0,
  is_self_line     TINYINT(1)   NOT NULL DEFAULT 0,
  has_grad_school  TINYINT(1)   NOT NULL DEFAULT 0,
  detail_url       VARCHAR(512) NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'online',
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sch_id (sch_id),
  UNIQUE KEY uk_code (code),
  KEY idx_province (province),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='院校名片';

-- ---------------------------------------------------------------------------
-- 2. 专业目录（对应 data/catalog.json：谁招哪个专业+就读方式）
-- ---------------------------------------------------------------------------
CREATE TABLE school_catalog (
  id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  school_code  VARCHAR(16)  NOT NULL,
  major_code   VARCHAR(16)  NOT NULL COMMENT '125100…；工程管理可为 125601-125604',
  major_name   VARCHAR(64)  NOT NULL,
  study_mode   VARCHAR(16)  NOT NULL COMMENT 'fulltime|parttime',
  year         INT          NULL COMMENT '目录年份，可空表示长期有效',
  source_url   VARCHAR(512) NULL,
  UNIQUE KEY uk_catalog (school_code, major_code, study_mode, year),
  KEY idx_major (major_code),
  KEY idx_school_major (school_code, major_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业目录（择校过滤）';

-- ---------------------------------------------------------------------------
-- 3. 培养信息（对应 data/programs.json）
-- ---------------------------------------------------------------------------
CREATE TABLE school_programs (
  id                 BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  school_code        VARCHAR(16)  NOT NULL,
  year               INT          NOT NULL,
  major_code         VARCHAR(16)  NOT NULL,
  major_name         VARCHAR(64)  NOT NULL,
  study_mode         VARCHAR(16)  NOT NULL COMMENT 'fulltime|parttime',
  study_mode_label   VARCHAR(16)  NULL COMMENT '全日制|非全日制',
  tuition_text       VARCHAR(512) NULL COMMENT '展示文案，如 3.4万元/年',
  tuition_per_year   DECIMAL(10,2) NULL COMMENT '万元/年，可选数值',
  tuition_total      DECIMAL(10,2) NULL COMMENT '全程万元',
  plan_text          VARCHAR(256) NULL,
  duration_text      VARCHAR(64)  NULL,
  source_url         VARCHAR(512) NULL,
  source_name        VARCHAR(128) NULL,
  provider           VARCHAR(32)  NULL COMMENT 'manual|deepseek|import',
  checked_at         DATE         NULL,
  synced_at          DATETIME     NULL,
  UNIQUE KEY uk_program (school_code, year, major_code, study_mode),
  KEY idx_program_lookup (school_code, major_code, study_mode, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培养信息（学费/学制/计划）';

-- ---------------------------------------------------------------------------
-- 4. 国家线（对应 data/nation_lines.json）
-- ---------------------------------------------------------------------------
CREATE TABLE nation_lines (
  id               BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  year             INT          NOT NULL,
  family           VARCHAR(16)  NOT NULL COMMENT '专业族，如 125100 / 125300',
  major_codes_json JSON         NOT NULL COMMENT '适用专业代码数组',
  a_total          INT          NULL,
  a_english        INT          NULL,
  a_comprehensive  INT          NULL,
  b_total          INT          NULL,
  b_english        INT          NULL,
  b_comprehensive  INT          NULL,
  source_url       VARCHAR(512) NULL,
  source_name      VARCHAR(128) NULL,
  provider         VARCHAR(32)  NULL,
  synced_at        DATETIME     NULL,
  UNIQUE KEY uk_nation (year, family),
  KEY idx_nation_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理类联考国家线';

-- ---------------------------------------------------------------------------
-- 5. 招录汇总（对应 data/admissions.json）
-- ---------------------------------------------------------------------------
CREATE TABLE admission_stats (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  school_code       VARCHAR(16)  NOT NULL,
  year              INT          NOT NULL,
  major_code        VARCHAR(16)  NOT NULL,
  study_mode        VARCHAR(16)  NOT NULL,
  reexam_min_score  INT          NULL COMMENT '复试最低分',
  min_score         INT          NULL COMMENT '拟录取最低分',
  max_score         INT          NULL COMMENT '拟录取最高分',
  admit_count       INT          NULL,
  reexam_count      INT          NULL,
  pending_note      VARCHAR(512) NULL,
  source_url        VARCHAR(512) NULL,
  source_name       VARCHAR(128) NULL,
  provider          VARCHAR(32)  NULL,
  checked_at        DATE         NULL,
  synced_at         DATETIME     NULL,
  UNIQUE KEY uk_admission (school_code, year, major_code, study_mode),
  KEY idx_admission_lookup (school_code, major_code, study_mode, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招录分数与人数';

-- ---------------------------------------------------------------------------
-- 6. 分数段（对应 admissions.scoreBands）
-- ---------------------------------------------------------------------------
CREATE TABLE admission_score_bands (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  admission_id      BIGINT UNSIGNED NOT NULL,
  score_label       VARCHAR(32)  NOT NULL COMMENT '如 256 或 250-254',
  score_min         INT          NULL,
  score_max         INT          NULL,
  reexam_count      INT          NULL,
  admit_count       INT          NULL,
  sort_order        INT          NOT NULL DEFAULT 0,
  KEY idx_band_admission (admission_id),
  CONSTRAINT fk_band_admission FOREIGN KEY (admission_id)
    REFERENCES admission_stats(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='进复试/录取分数段';

-- ---------------------------------------------------------------------------
-- 7. 近5年数据包头（对应 data/ai_packs/{code}_{major}_{mode}.json）
-- ---------------------------------------------------------------------------
CREATE TABLE school_data_packs (
  id                BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  school_code       VARCHAR(16)  NOT NULL,
  school_name       VARCHAR(128) NULL,
  major_code        VARCHAR(16)  NOT NULL,
  major_name        VARCHAR(64)  NULL,
  study_mode        VARCHAR(16)  NOT NULL,
  study_mode_label  VARCHAR(16)  NULL,
  start_year        INT          NOT NULL,
  end_year          INT          NOT NULL,
  skill             VARCHAR(64)  NULL,
  provider          VARCHAR(32)  NULL,
  major_info_json   JSON         NULL COMMENT '学费/学制等汇总',
  exam_rules_json   JSON         NULL,
  data_source_json  JSON         NULL,
  fetched_at        DATE         NULL,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pack (school_code, major_code, study_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='近5年包元数据';

-- ---------------------------------------------------------------------------
-- 8. 近5年按年明细（图表 + 换年秒切）
-- ---------------------------------------------------------------------------
CREATE TABLE school_year_stats (
  id                     BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  pack_id                BIGINT UNSIGNED NOT NULL,
  school_code            VARCHAR(16)  NOT NULL,
  major_code             VARCHAR(16)  NOT NULL,
  study_mode             VARCHAR(16)  NOT NULL,
  year                   INT          NOT NULL,
  status                 VARCHAR(64)  NULL,
  tuition_text           VARCHAR(512) NULL,
  plan_text              VARCHAR(256) NULL,
  duration_text          VARCHAR(64)  NULL,
  program_source_url     VARCHAR(512) NULL,
  program_source_name    VARCHAR(128) NULL,
  reexam_min_score       INT          NULL,
  min_score              INT          NULL,
  max_score              INT          NULL,
  admit_count            INT          NULL,
  reexam_count           INT          NULL,
  nation_a_total         INT          NULL,
  nation_a_english       INT          NULL,
  nation_a_comprehensive INT          NULL,
  nation_b_total         INT          NULL,
  nation_b_english       INT          NULL,
  nation_b_comprehensive INT          NULL,
  nation_source_url      VARCHAR(512) NULL,
  nation_source_name     VARCHAR(128) NULL,
  admission_source_url   VARCHAR(512) NULL,
  admission_source_name  VARCHAR(128) NULL,
  pending_note           VARCHAR(512) NULL,
  score_bands_json       JSON         NULL,
  data_confidence        VARCHAR(64)  NULL,
  UNIQUE KEY uk_year_row (school_code, major_code, study_mode, year),
  KEY idx_pack (pack_id),
  KEY idx_trend (school_code, major_code, study_mode, year),
  CONSTRAINT fk_year_pack FOREIGN KEY (pack_id)
    REFERENCES school_data_packs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='近5年逐年招录/国家线';

-- ---------------------------------------------------------------------------
-- 查询约定（对接 school-detail）
-- ---------------------------------------------------------------------------
-- 详情页主键：school_code + major_code + study_mode + year
-- 1) 培养：school_programs
-- 2) 招录：admission_stats (+ admission_score_bands)
-- 3) 国家线：nation_lines（按 major_codes_json 含 major_code；A/B 类看省份）
-- 4) 图表：school_year_stats WHERE school+major+mode ORDER BY year
-- 有 pack 时优先读 school_year_stats；没有再回退 programs/admissions/nation_lines
