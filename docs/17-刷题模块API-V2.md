# 17 · 刷题模块 API（V2）

> 全新建表：`mysql -u root -p --default-character-set=utf8mb4 gz199 < server/sql/practice_tables.sql`  
> 旧题库迁 V2（保留 5000 题 id）：`mysql -u root -p --default-character-set=utf8mb4 gz199 < server/sql/practice_migrate_v2.sql`  
> 会 **DROP** 旧刷题表并重建（含知识树 + 样例题）。

## 错误码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 40001 | 参数错误 |
| 40401 | 资源不存在 |
| 50001 | 刷题表未建立 |

统一响应：`{ ok, code, error?, ...data }`

## 接口清单

### GET `/api/subjects`
- Query：`userKey`
- 返回：`items[]` → `{ id, code, name, shortName, progress:{ todayCount, wrongCount, accuracy, answered, questionCount } }`

### GET `/api/chapters?subject_id=`
- Query：`subject_id`（必填）、`userKey`
- 返回：`items[]` → `{ id, name, questionCount, masteryRate }`

### GET `/api/knowledge-points?chapter_id=`
- Query：`chapter_id`（必填）
- 返回：`items[]` → `{ id, name, sortOrder }`

### GET `/api/questions`
- Query：`subject_id` / `chapter_id` / `knowledge_point_id` / `difficulty` / `question_type` / `mode=order|random` / `page` / `page_size`
- 返回：`items, total, page, pageSize`（不含答案）

### GET `/api/questions/{id}`
- Query：`userKey?` → 附加 `favorited`

### POST `/api/questions/submit`
```json
{ "userKey": "u_xxx", "questionId": 1, "answer": "A", "timeSpent": 3200 }
```
- 返回：`correct, answer, analysis, analysisIdea, wrongUpdated, autoRemoved, consecutiveCorrect, wrongCount, masteryUpdated`

### POST `/api/favorites/toggle`
```json
{ "userKey": "u_xxx", "questionId": 1 }
```
- 返回：`favorited`

### GET `/api/favorites`
- Query：`userKey` / `subject_id?` / `chapter_id?`

### GET `/api/wrong-questions`
- Query：`userKey` / `subject_id?` / `sort=time|wrong_count|knowledge`
- 仅返回 `auto_removed=0`

### GET `/api/stats/knowledge`
- Query：`userKey` / `subject_id?`
- 返回：`items`（全量）、`radar`（雷达用，最多 12）

### GET `/api/stats/overview`
- Query：`userKey` / `subject_id?`
- 返回：`stats.{ totalAnswered, accuracy, wrongOpen, favorites, todayCount, streakDays }`、`trend[]`

### GET `/api/stats/history`
- Query：`userKey` / `subject_id?` / `limit`

### GET `/api/practice/health`
- 检查表是否就绪、题目数量

## 错题自动移出

1. 答错 → 入册 / `wrong_count+1`，`consecutive_correct_count=0`，`auto_removed=0`
2. 答对且在册 → `consecutive_correct_count+1`
3. `consecutive_correct_count >= 2` → `auto_removed=1`，错题列表不再出现
4. **不提供**手动「已掌握」接口

## 目录结构

```
server/
  sql/practice_tables.sql
  src/main/java/com/gz199/
    practice/PracticeRepository.java
    practice/PracticeService.java
    web/PracticeController.java
pages/practice/
  index.vue      # 三科卡片
  chapters.vue   # 章节+掌握度
  quiz.vue       # 答题
  wrong.vue      # 错题滑动
  favorites.vue
  stats.vue
  history.vue
utils/api.js
styles/edu-theme.js   # EDU.subject
styles/edu-ui.scss
```
