package com.gz199.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷题模块 JDBC 访问层（V2：科目/章节/知识点）。
 */
@Repository
public class PracticeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PracticeRepository(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper;
    }

    public boolean tablesReady() {
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM subjects", Integer.class);
            // V2 题库必须有 subject_id；旧表只有 subject 字符串时视为未就绪
            jdbc.queryForObject("SELECT COUNT(subject_id) FROM questions WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 本地 guest key → 稳定数值用户 id（微信登录前过渡） */
    public static long toUserId(String userKey) {
        if (userKey == null || userKey.isBlank()) return 1L;
        long h = userKey.hashCode();
        return (h == Long.MIN_VALUE ? 1L : Math.abs(h)) + 1L;
    }

    // ---------- subjects ----------

    public List<Map<String, Object>> listSubjects() {
        return jdbc.queryForList(
                """
                SELECT id, code, name, short_name AS shortName, sort_order AS sortOrder
                FROM subjects WHERE status=1 ORDER BY sort_order ASC, id ASC
                """
        );
    }

    public Map<String, Object> findSubject(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, code, name, short_name AS shortName FROM subjects WHERE id=? AND status=1",
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------- chapters ----------

    public List<Map<String, Object>> listChapters(long subjectId) {
        return jdbc.queryForList(
                """
                SELECT id, subject_id AS subjectId, name, sort_order AS sortOrder
                FROM chapters WHERE subject_id=? AND status=1
                ORDER BY sort_order ASC, id ASC
                """,
                subjectId
        );
    }

    public Map<String, Object> findChapter(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, subject_id AS subjectId, name FROM chapters WHERE id=? AND status=1",
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 章节掌握度：该章下用户做过题的正确率（未做过为 null） */
    public Double chapterMastery(long userId, long chapterId) {
        Integer total = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND q.chapter_id=? AND q.status=1
                """,
                Integer.class, userId, chapterId
        );
        if (total == null || total == 0) return null;
        Integer correct = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND q.chapter_id=? AND q.status=1 AND a.is_correct=1
                """,
                Integer.class, userId, chapterId
        );
        int c = correct == null ? 0 : correct;
        return Math.round(1000.0 * c / total) / 10.0;
    }

    public int countQuestionsInChapter(long chapterId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE chapter_id=? AND status=1",
                Integer.class, chapterId
        );
        return n == null ? 0 : n;
    }

    // ---------- knowledge points ----------

    public List<Map<String, Object>> listKnowledgePoints(long chapterId) {
        return jdbc.queryForList(
                """
                SELECT id, chapter_id AS chapterId, name, sort_order AS sortOrder
                FROM knowledge_points WHERE chapter_id=? AND status=1
                ORDER BY sort_order ASC, id ASC
                """,
                chapterId
        );
    }

    // ---------- questions ----------

    public int countQuestions(Long subjectId) {
        if (subjectId == null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE status=1", Integer.class);
            return n == null ? 0 : n;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE status=1 AND subject_id=?",
                Integer.class, subjectId
        );
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> listQuestions(
            Long subjectId, Long chapterId, Long kpId,
            Integer difficulty, String questionType,
            String mode, int page, int pageSize
    ) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT q.id, q.subject_id AS subjectId, q.chapter_id AS chapterId,
                       q.knowledge_point_id AS knowledgePointId, q.question_type AS questionType,
                       q.stem, q.options_json AS optionsJson, q.difficulty, q.source_tag AS sourceTag,
                       q.year, s.code AS subjectCode, s.name AS subjectName,
                       c.name AS chapterName, kp.name AS knowledgePointName
                FROM questions q
                JOIN subjects s ON s.id=q.subject_id
                JOIN chapters c ON c.id=q.chapter_id
                JOIN knowledge_points kp ON kp.id=q.knowledge_point_id
                WHERE q.status=1
                """
        );
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, subjectId, chapterId, kpId, difficulty, questionType);
        if ("random".equals(mode)) {
            sql.append(" ORDER BY RAND()");
        } else {
            sql.append(" ORDER BY q.id ASC");
        }
        int size = Math.max(1, Math.min(pageSize, 50));
        int offset = Math.max(0, (Math.max(1, page) - 1) * size);
        sql.append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add(offset);
        return mapQuestionRows(jdbc.queryForList(sql.toString(), args.toArray()), false);
    }

    public int countFiltered(
            Long subjectId, Long chapterId, Long kpId,
            Integer difficulty, String questionType
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM questions q WHERE q.status=1");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, subjectId, chapterId, kpId, difficulty, questionType);
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    private void appendFilters(
            StringBuilder sql, List<Object> args,
            Long subjectId, Long chapterId, Long kpId,
            Integer difficulty, String questionType
    ) {
        if (subjectId != null) {
            sql.append(" AND q.subject_id=?");
            args.add(subjectId);
        }
        if (chapterId != null) {
            sql.append(" AND q.chapter_id=?");
            args.add(chapterId);
        }
        if (kpId != null) {
            sql.append(" AND q.knowledge_point_id=?");
            args.add(kpId);
        }
        if (difficulty != null) {
            sql.append(" AND q.difficulty=?");
            args.add(difficulty);
        }
        if (questionType != null && !questionType.isBlank()) {
            sql.append(" AND q.question_type=?");
            args.add(questionType);
        }
    }

    public Map<String, Object> findQuestion(long id, boolean withAnswer) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT q.id, q.subject_id AS subjectId, q.chapter_id AS chapterId,
                       q.knowledge_point_id AS knowledgePointId, q.question_type AS questionType,
                       q.stem, q.options_json AS optionsJson, q.answer, q.analysis, q.analysis_idea AS analysisIdea,
                       q.difficulty, q.source_tag AS sourceTag, q.year,
                       s.code AS subjectCode, s.name AS subjectName,
                       c.name AS chapterName, kp.name AS knowledgePointName
                FROM questions q
                JOIN subjects s ON s.id=q.subject_id
                JOIN chapters c ON c.id=q.chapter_id
                JOIN knowledge_points kp ON kp.id=q.knowledge_point_id
                WHERE q.id=? AND q.status=1 LIMIT 1
                """,
                id
        );
        List<Map<String, Object>> mapped = mapQuestionRows(rows, withAnswer);
        return mapped.isEmpty() ? null : mapped.get(0);
    }

    /** 有题目的年份列表（历年真题入口） */
    public List<Map<String, Object>> listYears() {
        return jdbc.queryForList(
                """
                SELECT year, COUNT(*) AS count
                FROM questions
                WHERE year IS NOT NULL AND status=1
                GROUP BY year
                ORDER BY year DESC
                """
        );
    }

    /** 按年份查所有真题id */
    public List<Long> listQuestionIdsByYear(int year) {
        return jdbc.queryForList(
                "SELECT id FROM questions WHERE year=? AND status=1 ORDER BY id ASC",
                Long.class, year
        );
    }

    // ---------- answers / wrong / knowledge ----------

    public void insertAnswer(String userKey, long questionId, String answer, boolean correct, Integer timeMs) {
        jdbc.update(
                """
                INSERT INTO user_answers(user_id,question_id,user_answer,is_correct,time_spent_ms)
                VALUES(?,?,?,?,?)
                """,
                toUserId(userKey), questionId, answer, correct ? 1 : 0, timeMs
        );
    }

    /**
     * 错题自动移出：
     * - 答错 → 入册/wrong_count+1，连续答对归零，auto_removed=0
     * - 答对且在册 → consecutive_correct_count+1；>=2 则 auto_removed=1
     *
     * @return Map: updatedWrong, autoRemoved, consecutiveCorrect, wrongCount
     */
    public Map<String, Object> applyWrongBook(String userKey, long questionId, boolean correct) {
        long uid = toUserId(userKey);
        Map<String, Object> info = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT wrong_count AS wrongCount, consecutive_correct_count AS consecutiveCorrect,
                       auto_removed AS autoRemoved
                FROM user_wrong_questions WHERE user_id=? AND question_id=?
                """,
                uid, questionId
        );

        if (!correct) {
            if (rows.isEmpty()) {
                jdbc.update(
                        """
                        INSERT INTO user_wrong_questions
                          (user_id,question_id,wrong_count,consecutive_correct_count,last_wrong_at,auto_removed)
                        VALUES(?,?,1,0,NOW(),0)
                        """,
                        uid, questionId
                );
                info.put("updatedWrong", true);
                info.put("wrongCount", 1);
                info.put("consecutiveCorrect", 0);
                info.put("autoRemoved", false);
            } else {
                jdbc.update(
                        """
                        UPDATE user_wrong_questions
                        SET wrong_count=wrong_count+1, consecutive_correct_count=0,
                            last_wrong_at=NOW(), auto_removed=0, updated_at=NOW()
                        WHERE user_id=? AND question_id=?
                        """,
                        uid, questionId
                );
                int wc = toInt(rows.get(0).get("wrongCount"), 0) + 1;
                info.put("updatedWrong", true);
                info.put("wrongCount", wc);
                info.put("consecutiveCorrect", 0);
                info.put("autoRemoved", false);
            }
            return info;
        }

        // 答对
        if (rows.isEmpty()) {
            info.put("updatedWrong", false);
            info.put("autoRemoved", false);
            info.put("consecutiveCorrect", 0);
            info.put("wrongCount", 0);
            return info;
        }
        int prev = toInt(rows.get(0).get("consecutiveCorrect"), 0);
        int next = prev + 1;
        boolean removed = next >= 2;
        jdbc.update(
                """
                UPDATE user_wrong_questions
                SET consecutive_correct_count=?, auto_removed=?, updated_at=NOW()
                WHERE user_id=? AND question_id=?
                """,
                next, removed ? 1 : 0, uid, questionId
        );
        info.put("updatedWrong", true);
        info.put("wrongCount", rows.get(0).get("wrongCount"));
        info.put("consecutiveCorrect", next);
        info.put("autoRemoved", removed);
        return info;
    }

    public void upsertKnowledge(String userKey, long knowledgePointId, boolean correct) {
        if (knowledgePointId <= 0) return;
        long uid = toUserId(userKey);
        jdbc.update(
                """
                INSERT INTO user_knowledge_stats(user_id,knowledge_point_id,total_count,correct_count,mastery_rate)
                VALUES(?,?,1,?,?)
                ON DUPLICATE KEY UPDATE
                  total_count=total_count+1,
                  correct_count=correct_count+?,
                  mastery_rate=ROUND(100.0*(correct_count+?)/(total_count+1), 1),
                  updated_at=NOW()
                """,
                uid, knowledgePointId,
                correct ? 1 : 0, correct ? 100.0 : 0.0,
                correct ? 1 : 0, correct ? 1 : 0
        );
    }

    // ---------- favorites ----------

    public boolean toggleFavorite(String userKey, long questionId) {
        long uid = toUserId(userKey);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id=? AND question_id=?",
                Integer.class, uid, questionId
        );
        if (n != null && n > 0) {
            jdbc.update("DELETE FROM user_favorites WHERE user_id=? AND question_id=?", uid, questionId);
            return false;
        }
        jdbc.update("INSERT INTO user_favorites(user_id,question_id) VALUES(?,?)", uid, questionId);
        return true;
    }

    public boolean isFavorite(String userKey, long questionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites WHERE user_id=? AND question_id=?",
                Integer.class, toUserId(userKey), questionId
        );
        return n != null && n > 0;
    }

    public List<Map<String, Object>> listFavorites(String userKey, Long subjectId, Long chapterId) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT q.id, q.subject_id AS subjectId, q.chapter_id AS chapterId,
                       q.knowledge_point_id AS knowledgePointId, q.question_type AS questionType,
                       q.stem, q.options_json AS optionsJson, q.difficulty, q.source_tag AS sourceTag,
                       s.code AS subjectCode, s.name AS subjectName,
                       c.name AS chapterName, kp.name AS knowledgePointName,
                       f.created_at AS favoritedAt
                FROM user_favorites f
                JOIN questions q ON q.id=f.question_id AND q.status=1
                JOIN subjects s ON s.id=q.subject_id
                JOIN chapters c ON c.id=q.chapter_id
                JOIN knowledge_points kp ON kp.id=q.knowledge_point_id
                WHERE f.user_id=?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subjectId != null) {
            sql.append(" AND q.subject_id=?");
            args.add(subjectId);
        }
        if (chapterId != null) {
            sql.append(" AND q.chapter_id=?");
            args.add(chapterId);
        }
        sql.append(" ORDER BY f.created_at DESC LIMIT 200");
        return mapQuestionRows(jdbc.queryForList(sql.toString(), args.toArray()), false);
    }

    // ---------- wrong list ----------

    public List<Map<String, Object>> listWrong(String userKey, Long subjectId, String sort) {
        String order = "w.last_wrong_at DESC";
        if ("wrong_count".equals(sort)) order = "w.wrong_count DESC, w.last_wrong_at DESC";
        if ("knowledge".equals(sort)) order = "kp.name ASC, w.last_wrong_at DESC";

        StringBuilder sql = new StringBuilder(
                """
                SELECT q.id, q.subject_id AS subjectId, q.chapter_id AS chapterId,
                       q.knowledge_point_id AS knowledgePointId, q.question_type AS questionType,
                       q.stem, q.options_json AS optionsJson, q.difficulty, q.source_tag AS sourceTag,
                       s.code AS subjectCode, s.name AS subjectName,
                       c.name AS chapterName, kp.name AS knowledgePointName,
                       w.wrong_count AS wrongCount,
                       w.consecutive_correct_count AS consecutiveCorrect,
                       w.last_wrong_at AS lastWrongAt
                FROM user_wrong_questions w
                JOIN questions q ON q.id=w.question_id AND q.status=1
                JOIN subjects s ON s.id=q.subject_id
                JOIN chapters c ON c.id=q.chapter_id
                JOIN knowledge_points kp ON kp.id=q.knowledge_point_id
                WHERE w.user_id=? AND w.auto_removed=0
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subjectId != null) {
            sql.append(" AND q.subject_id=?");
            args.add(subjectId);
        }
        sql.append(" ORDER BY ").append(order).append(" LIMIT 200");
        return mapQuestionRows(jdbc.queryForList(sql.toString(), args.toArray()), false);
    }

    // ---------- stats ----------

    public Map<String, Object> subjectProgress(String userKey, long subjectId) {
        long uid = toUserId(userKey);
        Map<String, Object> out = new LinkedHashMap<>();
        Integer today = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND q.subject_id=? AND DATE(a.created_at)=CURDATE()
                """,
                Integer.class, uid, subjectId
        );
        Integer wrongOpen = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_wrong_questions w
                JOIN questions q ON q.id=w.question_id
                WHERE w.user_id=? AND w.auto_removed=0 AND q.subject_id=?
                """,
                Integer.class, uid, subjectId
        );
        Integer answered = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND q.subject_id=?
                """,
                Integer.class, uid, subjectId
        );
        Integer correct = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND q.subject_id=? AND a.is_correct=1
                """,
                Integer.class, uid, subjectId
        );
        int a = answered == null ? 0 : answered;
        int c = correct == null ? 0 : correct;
        out.put("todayCount", today == null ? 0 : today);
        out.put("wrongCount", wrongOpen == null ? 0 : wrongOpen);
        out.put("answered", a);
        out.put("accuracy", a == 0 ? 0 : Math.round(1000.0 * c / a) / 10.0);
        out.put("questionCount", countQuestions(subjectId));
        return out;
    }

    public Map<String, Object> overview(String userKey, Long subjectId) {
        long uid = toUserId(userKey);
        Map<String, Object> out = new LinkedHashMap<>();
        String subJoin = subjectId != null ? " AND q.subject_id=?" : "";
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if (subjectId != null) args.add(subjectId);

        Integer answered = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=?" + subJoin,
                Integer.class, args.toArray()
        );
        Integer correct = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=? AND a.is_correct=1" + subJoin,
                Integer.class, args.toArray()
        );
        Integer wrongOpen = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_wrong_questions w JOIN questions q ON q.id=w.question_id WHERE w.user_id=? AND w.auto_removed=0" + subJoin,
                Integer.class, args.toArray()
        );
        Integer fav = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites f JOIN questions q ON q.id=f.question_id WHERE f.user_id=?" + subJoin,
                Integer.class, args.toArray()
        );
        Integer today = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=?" + subJoin + " AND DATE(a.created_at)=CURDATE()",
                Integer.class, args.toArray()
        );

        int a = answered == null ? 0 : answered;
        int c = correct == null ? 0 : correct;
        out.put("totalAnswered", a);
        out.put("correct", c);
        out.put("accuracy", a == 0 ? 0 : Math.round(1000.0 * c / a) / 10.0);
        out.put("wrongOpen", wrongOpen == null ? 0 : wrongOpen);
        out.put("favorites", fav == null ? 0 : fav);
        out.put("todayCount", today == null ? 0 : today);
        out.put("streakDays", calcStreak(uid, subjectId));
        out.put("questionCount", countQuestions(subjectId));
        return out;
    }

    /** 连续刷题天数：从今天往前数，无断档的天数 */
    public int calcStreak(long userId, Long subjectId) {
        String sql = """
                SELECT DISTINCT DATE(a.created_at) AS d
                FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=?
                """ + (subjectId != null ? " AND q.subject_id=?" : "") + """
                 ORDER BY d DESC LIMIT 60
                """;
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (subjectId != null) args.add(subjectId);
        List<java.sql.Date> days = jdbc.query(sql, (rs, i) -> rs.getDate("d"), args.toArray());
        if (days == null || days.isEmpty()) return 0;
        java.time.LocalDate expect = java.time.LocalDate.now();
        int streak = 0;
        for (java.sql.Date d : days) {
            java.time.LocalDate day = d.toLocalDate();
            if (day.equals(expect) || (streak == 0 && day.equals(expect.minusDays(1)))) {
                if (streak == 0 && day.equals(expect.minusDays(1))) {
                    expect = day;
                }
                if (day.equals(expect)) {
                    streak++;
                    expect = expect.minusDays(1);
                } else break;
            } else break;
        }
        return streak;
    }

    public List<Map<String, Object>> knowledgeRadar(String userKey, Long subjectId) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT kp.id AS knowledgePointId, kp.name AS knowledgePointName,
                       c.name AS chapterName, s.name AS subjectName,
                       COALESCE(uks.total_count, 0) AS totalCount,
                       COALESCE(uks.correct_count, 0) AS correctCount,
                       COALESCE(uks.mastery_rate, 0) AS masteryRate
                FROM knowledge_points kp
                JOIN chapters c ON c.id=kp.chapter_id AND c.status=1
                JOIN subjects s ON s.id=c.subject_id AND s.status=1
                LEFT JOIN user_knowledge_stats uks
                  ON uks.knowledge_point_id=kp.id AND uks.user_id=?
                WHERE kp.status=1
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subjectId != null) {
            sql.append(" AND c.subject_id=?");
            args.add(subjectId);
        }
        sql.append(" ORDER BY c.sort_order ASC, kp.sort_order ASC LIMIT 40");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 近 N 天每日正确率（趋势） */
    public List<Map<String, Object>> accuracyTrend(String userKey, Long subjectId, int days) {
        int d = Math.max(3, Math.min(days, 30));
        String sub = subjectId != null ? " AND q.subject_id=?" : "";
        String sql = """
                SELECT DATE(a.created_at) AS day,
                       COUNT(*) AS total,
                       SUM(a.is_correct) AS correct
                FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                WHERE a.user_id=? AND a.created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
                """ + sub + """
                GROUP BY DATE(a.created_at)
                ORDER BY day ASC
                """;
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        args.add(d);
        if (subjectId != null) args.add(subjectId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            int total = toInt(row.get("total"), 0);
            int correct = toInt(row.get("correct"), 0);
            m.put("day", String.valueOf(row.get("day")));
            m.put("total", total);
            m.put("correct", correct);
            m.put("accuracy", total == 0 ? 0 : Math.round(1000.0 * correct / total) / 10.0);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listHistory(String userKey, Long subjectId, int limit) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT a.id, a.question_id AS questionId, a.user_answer AS userAnswer,
                       a.is_correct AS isCorrect, a.time_spent_ms AS timeSpentMs,
                       a.created_at AS createdAt,
                       q.stem, s.name AS subjectName, kp.name AS knowledgePointName
                FROM user_answers a
                JOIN questions q ON q.id=a.question_id
                JOIN subjects s ON s.id=q.subject_id
                JOIN knowledge_points kp ON kp.id=q.knowledge_point_id
                WHERE a.user_id=?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subjectId != null) {
            sql.append(" AND q.subject_id=?");
            args.add(subjectId);
        }
        sql.append(" ORDER BY a.created_at DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ---------- mapping ----------

    private List<Map<String, Object>> mapQuestionRows(List<Map<String, Object>> rows, boolean withAnswer) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("subjectId", row.get("subjectId"));
            m.put("chapterId", row.get("chapterId"));
            m.put("knowledgePointId", row.get("knowledgePointId"));
            m.put("questionType", row.get("questionType"));
            m.put("stem", row.get("stem"));
            m.put("content", row.get("stem")); // 前端兼容旧字段
            m.put("options", readOptions(row.get("optionsJson")));
            m.put("difficulty", row.get("difficulty"));
            m.put("sourceTag", row.get("sourceTag"));
            if (row.get("year") != null) m.put("year", row.get("year"));
            m.put("subjectCode", row.get("subjectCode"));
            m.put("subjectName", row.get("subjectName"));
            m.put("chapterName", row.get("chapterName"));
            m.put("knowledgePointName", row.get("knowledgePointName"));
            m.put("knowledgePoint", row.get("knowledgePointName"));
            if (row.get("wrongCount") != null) m.put("wrongCount", row.get("wrongCount"));
            if (row.get("consecutiveCorrect") != null) m.put("consecutiveCorrect", row.get("consecutiveCorrect"));
            if (row.get("lastWrongAt") != null) m.put("lastWrongAt", String.valueOf(row.get("lastWrongAt")));
            if (row.get("favoritedAt") != null) m.put("favoritedAt", String.valueOf(row.get("favoritedAt")));
            if (withAnswer) {
                m.put("answer", row.get("answer"));
                m.put("analysis", row.get("analysis"));
                m.put("analysisIdea", row.get("analysisIdea"));
            }
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> readOptions(Object raw) {
        if (raw == null) return List.of();
        try {
            Object parsed = raw;
            if (raw instanceof String || raw instanceof byte[]) {
                String s = raw instanceof byte[]
                        ? new String((byte[]) raw, java.nio.charset.StandardCharsets.UTF_8)
                        : String.valueOf(raw).trim();
                if (s.isEmpty() || "null".equalsIgnoreCase(s)) return List.of();
                parsed = mapper.readValue(s, Object.class);
            }
            if (parsed instanceof Map<?, ?> map) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Map<String, Object> opt = new LinkedHashMap<>();
                    opt.put("key", String.valueOf(e.getKey()));
                    opt.put("text", e.getValue() == null ? "" : String.valueOf(e.getValue()));
                    out.add(opt);
                }
                out.sort((a, b) -> String.valueOf(a.get("key")).compareTo(String.valueOf(b.get("key"))));
                return out;
            }
            if (parsed instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> opt = new LinkedHashMap<>();
                        Object key = m.containsKey("key") ? m.get("key") : m.get("label");
                        Object text = m.containsKey("text") ? m.get("text") : m.get("value");
                        if (key == null && text == null && m.size() == 1) {
                            Map.Entry<?, ?> only = m.entrySet().iterator().next();
                            key = only.getKey();
                            text = only.getValue();
                        }
                        opt.put("key", key == null ? "" : String.valueOf(key));
                        opt.put("text", text == null ? "" : String.valueOf(text));
                        out.add(opt);
                    } else if (item != null) {
                        String s = String.valueOf(item).trim();
                        Map<String, Object> opt = new LinkedHashMap<>();
                        if (s.length() >= 2 && (s.charAt(1) == '.' || s.charAt(1) == '、')) {
                            opt.put("key", s.substring(0, 1));
                            opt.put("text", s.substring(2).trim());
                        } else {
                            opt.put("key", "");
                            opt.put("text", s);
                        }
                        out.add(opt);
                    }
                }
                return out;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    private static int toInt(Object o, int dft) {
        if (o == null) return dft;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return dft;
        }
    }
}
