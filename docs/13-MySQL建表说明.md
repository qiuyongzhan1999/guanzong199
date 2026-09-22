# 13 · MySQL 建表说明（可直接对接）

> 可执行 SQL：[`server/sql/schema.sql`](../server/sql/schema.sql)  
> 字段对齐当前已落地的 JSON：`schools` / `catalog` / `programs` / `nation_lines` / `admissions` / `ai_packs`。

---

## 1. 怎么建

```bash
mysql -u root -p < server/sql/schema.sql
```

或 Navicat / DBeaver 打开 `server/sql/schema.sql` 整份执行。库名默认 `gz199`，字符集 `utf8mb4`。

连接串示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/gz199?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: gz199_app
    password: ${DB_PASSWORD}
```

---

## 2. 表与现有数据文件对照

| 表 | 对应文件 / 包 | 详情页用途 |
|----|---------------|------------|
| `schools` | `data/schools.json` | 校名、省份、标签、logo |
| `school_catalog` | `data/catalog.json` | 该校有哪些专业+全日制/非全 |
| `school_programs` | `data/programs.json` | 学费、学制、计划 |
| `nation_lines` | `data/nation_lines.json` | 国家线 A/B |
| `admission_stats` | `data/admissions.json` | 复试线、录取分、人数 |
| `admission_score_bands` | `admissions.scoreBands` | 分数段表 |
| `school_data_packs` | `data/ai_packs/*.json` 头 | 近5年包是否存在 |
| `school_year_stats` | `ai_packs.yearly_data` | 图表、换年秒切 |

接口主键一律：

```text
school_code + major_code + study_mode + year
```

工程管理界面码 `1256` 入库时拆成 `125601`–`125604`，或在查询层做映射（前端已有同样逻辑）。

---

## 3. 读路径（快）

详情页**不要**在请求里同步调模型。推荐顺序：

1. 读 `school_year_stats`（有 pack 明细就秒开图表）
2. 没有再读 `school_programs` + `admission_stats` + `nation_lines`
3. DeepSeek 补数只写库 / 写 `ai_packs`，由后台任务或显式 `enrich=true` 触发

当前 Java 已改成：`/api/school-detail` **默认 `enrich=false`**，只读 JSON/包。

---

## 4. 字段要点（对接时别漏）

### schools

- `sch_id`：小程序路由 `?id=`
- `code`：单位代码，和 programs / catalog 关联
- `tags_json` / `flags_json`：双一流、自划线等；也可用布尔列冗余加速筛选

### school_programs

- 展示优先 `tuition_text` / `plan_text` / `duration_text`（前端直接显示）
- `tuition_per_year` / `tuition_total` 可选，方便以后排序筛选

### nation_lines

- `major_codes_json`：一条线可覆盖多个专业（如会计/图情/审计）
- 前端按省份决定 A/B：`内蒙古,广西,海南,贵州,云南,西藏,甘肃,青海,宁夏,新疆` → B 类

### admission_stats

- `reexam_min_score`：复试线  
- `min_score` / `max_score`：拟录取最低/最高  
- `admit_count` / `reexam_count`：录取人数 / 进复试人数  
- `pending_note`：缺数说明

### school_year_stats

- 图表用：`reexam_min_score`、`min_score`、`admit_count`、`reexam_count`
- 换年用同一行里的学费/国家线字段，避免再打一轮接口

---

## 5. 导入建议

| 来源 | 进哪张表 |
|------|----------|
| 研招网院校列表 | `schools` |
| 2026 专业目录 | `school_catalog` |
| 人工核对简章 | `school_programs` |
| 教育部国家线 | `nation_lines` |
| 复试/拟录取名单 | `admission_stats` + `admission_score_bands` |
| 已有 `ai_packs/*.json` | `school_data_packs` + `school_year_stats` |

脚本可把现有 JSON 直接 upsert；唯一键见各表 `UNIQUE KEY`。

---

## 7. 导入现有 JSON（已跑通）

```powershell
$env:DB_PASSWORD = "你的密码"
python server/scripts/import_to_mysql.py
```

策略：

| 数据 | 处理 |
|------|------|
| schools / catalog | 研招网结构，**全量导入** |
| nation_lines | 有分数才导入 |
| programs / admissions | **跳过空壳**（无来源或无实质字段） |
| ai_packs | **跳过全 null 空壳包** |

联网拉最新国家线（需 DeepSeek Key）：

```powershell
$env:DEEPSEEK_API_KEY = "sk-..."
$env:DB_PASSWORD = "你的密码"
python server/scripts/refresh_nation_lines.py
```

招录学费等逐校数据：打开院校详情会后台 `enrich=true` 联网搜索，写回 `school_data_packs` / `school_year_stats`。全量 2000+ 组合不适合一次跑完。

接口读路径：`schools` 表有数据后，`/api/school-detail` 优先 MySQL（响应里 `source: mysql`）。
