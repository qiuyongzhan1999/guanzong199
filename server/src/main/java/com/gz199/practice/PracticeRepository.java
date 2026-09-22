package com.gz199.practice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            jdbc.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int countQuestions() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE status=1", Integer.class);
        return n == null ? 0 : n;
    }

    public int countQuestions(String subject) {
        if (subject == null || subject.isBlank()) return countQuestions();
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE status=1 AND subject=?",
                Integer.class,
                subject
        );
        return n == null ? 0 : n;
    }

    public void clearQuestions() {
        jdbc.update("DELETE FROM user_answers");
        jdbc.update("DELETE FROM user_favorites");
        jdbc.update("DELETE FROM user_wrong_questions");
        jdbc.update("DELETE FROM user_knowledge_stats");
        jdbc.update("DELETE FROM questions");
    }

    public long insertQuestion(Map<String, Object> q) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO questions(subject,type,knowledge_point,content,options,answer,
                      analysis,analysis_idea,analysis_kp,difficulty,year,status)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,1)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            bindQuestion(ps, q);
            return ps;
        }, keys);
        Number id = keys.getKey();
        return id == null ? 0L : id.longValue();
    }

    /** 批量入库（灌题库用） */
    public int insertQuestionsBatch(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return 0;
        jdbc.batchUpdate(
                """
                INSERT INTO questions(subject,type,knowledge_point,content,options,answer,
                  analysis,analysis_idea,analysis_kp,difficulty,year,status)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,1)
                """,
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        bindQuestion(ps, list.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return list.size();
                    }
                }
        );
        return list.size();
    }

    private void bindQuestion(PreparedStatement ps, Map<String, Object> q) throws java.sql.SQLException {
        ps.setString(1, str(q.get("subject")));
        ps.setString(2, str(q.get("type")));
        Object kp = q.get("knowledge_point");
        if (kp == null) kp = q.get("knowledgePoint");
        ps.setString(3, str(kp));
        ps.setString(4, str(q.get("content")));
        ps.setString(5, writeJson(q.get("options")));
        ps.setString(6, str(q.get("answer")));
        ps.setString(7, str(q.get("analysis")));
        Object idea = q.get("analysisIdea");
        if (idea == null) idea = q.get("analysis_idea");
        ps.setString(8, str(idea));
        Object akp = q.get("analysisKp");
        if (akp == null) akp = q.get("analysis_kp");
        ps.setString(9, str(akp));
        ps.setInt(10, toInt(q.get("difficulty"), 3));
        Object year = q.get("year");
        if (year == null) ps.setObject(11, null);
        else ps.setInt(11, toInt(year, 0));
    }

    public List<String> listModules(String subject, String type) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT DISTINCT knowledge_point
                FROM questions
                WHERE status=1 AND knowledge_point IS NOT NULL AND knowledge_point<>''
                """
        );
        List<Object> args = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            sql.append(" AND subject=?");
            args.add(subject);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND type=?");
            args.add(type);
        }
        sql.append(" ORDER BY knowledge_point ASC");
        return jdbc.queryForList(sql.toString(), String.class, args.toArray());
    }

    public List<Map<String, Object>> listQuestions(String subject, String type, String kp, String mode, int limit) {
        StringBuilder sql = new StringBuilder(
                """
                SELECT id, subject, type, knowledge_point AS knowledgePoint, content, options AS optionsJson,
                       difficulty, year
                FROM questions WHERE status=1
                """
        );
        List<Object> args = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            sql.append(" AND subject=?");
            args.add(subject);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND type=?");
            args.add(type);
        }
        if (kp != null && !kp.isBlank()) {
            sql.append(" AND knowledge_point=?");
            args.add(kp);
        }
        if ("random".equals(mode)) {
            sql.append(" ORDER BY RAND()");
        } else {
            sql.append(" ORDER BY id ASC");
        }
        sql.append(" LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 50)));
        return mapRows(jdbc.queryForList(sql.toString(), args.toArray()), false);
    }

    public Map<String, Object> findQuestion(long id, boolean withAnswer) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT id, subject, type, knowledge_point AS knowledgePoint, content, options AS optionsJson,
                       answer, analysis, analysis_idea AS analysisIdea, analysis_kp AS analysisKp,
                       difficulty, year
                FROM questions WHERE id=? AND status=1 LIMIT 1
                """,
                id
        );
        List<Map<String, Object>> mapped = mapRows(rows, withAnswer);
        return mapped.isEmpty() ? null : mapped.get(0);
    }

    /** 本地 guest key → 稳定的数值用户 id（微信登录前过渡） */
    public static long toUserId(String userKey) {
        if (userKey == null || userKey.isBlank()) return 1L;
        long h = userKey.hashCode();
        return (h == Long.MIN_VALUE ? 1L : Math.abs(h)) + 1L;
    }

    public void insertAnswer(String userKey, long questionId, String answer, boolean correct, Integer timeMs, String mode) {
        jdbc.update(
                """
                INSERT INTO user_answers(user_id,question_id,user_answer,is_correct,time_spent_ms,mode)
                VALUES(?,?,?,?,?,?)
                """,
                toUserId(userKey), questionId, answer, correct ? 1 : 0, timeMs, mode
        );
    }

    public void upsertWrong(String userKey, long questionId, boolean correct) {
        if (correct) return;
        jdbc.update(
                """
                INSERT INTO user_wrong_questions(user_id,question_id,wrong_count,last_wrong_at,status)
                VALUES(?,?,1,NOW(),0)
                ON DUPLICATE KEY UPDATE wrong_count=wrong_count+1, last_wrong_at=NOW(), status=0, updated_at=NOW()
                """,
                toUserId(userKey), questionId
        );
    }

    public void upsertKnowledge(String userKey, String subject, String kp, boolean correct) {
        if (kp == null || kp.isBlank()) return;
        long uid = toUserId(userKey);
        jdbc.update(
                """
                INSERT INTO user_knowledge_stats(user_id,subject,knowledge_point,total,correct,mastery_rate)
                VALUES(?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  total=total+1,
                  correct=correct+?,
                  mastery_rate=ROUND(100.0*(correct+?)/ (total+1), 1),
                  updated_at=NOW()
                """,
                uid, subject, kp, 1, correct ? 1 : 0, correct ? 100.0 : 0.0,
                correct ? 1 : 0, correct ? 1 : 0
        );
    }

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

    public List<Map<String, Object>> listFavorites(String userKey, String subject) {
        String sql = """
                SELECT q.id, q.subject, q.type, q.knowledge_point AS knowledgePoint, q.content,
                       q.options AS optionsJson, q.difficulty, f.created_at AS favoritedAt
                FROM user_favorites f
                JOIN questions q ON q.id=f.question_id AND q.status=1
                WHERE f.user_id=?
                """;
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subject != null && !subject.isBlank()) {
            sql += " AND q.subject=?";
            args.add(subject);
        }
        sql += " ORDER BY f.created_at DESC LIMIT 100";
        return mapRows(jdbc.queryForList(sql, args.toArray()), false);
    }

    public List<Map<String, Object>> listWrong(String userKey, String subject, int status) {
        String sql = """
                SELECT q.id, q.subject, q.type, q.knowledge_point AS knowledgePoint, q.content,
                       q.options AS optionsJson, q.difficulty,
                       w.wrong_count AS wrongCount, w.last_wrong_at AS lastWrongAt, w.status
                FROM user_wrong_questions w
                JOIN questions q ON q.id=w.question_id AND q.status=1
                WHERE w.user_id=? AND w.status=?
                """;
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        args.add(status);
        if (subject != null && !subject.isBlank()) {
            sql += " AND q.subject=?";
            args.add(subject);
        }
        sql += " ORDER BY w.last_wrong_at DESC LIMIT 100";
        return mapRows(jdbc.queryForList(sql, args.toArray()), false);
    }

    public void markMastered(String userKey, long questionId) {
        jdbc.update(
                "UPDATE user_wrong_questions SET status=1, updated_at=NOW() WHERE user_id=? AND question_id=?",
                toUserId(userKey), questionId
        );
    }

    public Map<String, Object> overview(String userKey, String subject) {
        Map<String, Object> out = new LinkedHashMap<>();
        String subFilter = subject != null && !subject.isBlank() ? " AND q.subject=?" : "";
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (!subFilter.isEmpty()) args.add(subject);

        Integer answered = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=?" + subFilter,
                Integer.class, args.toArray()
        );
        Integer correct = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=? AND a.is_correct=1" + subFilter,
                Integer.class, args.toArray()
        );
        Integer wrongOpen = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_wrong_questions w JOIN questions q ON q.id=w.question_id WHERE w.user_id=? AND w.status=0" + subFilter,
                Integer.class, args.toArray()
        );
        Integer fav = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites f JOIN questions q ON q.id=f.question_id WHERE f.user_id=?" + subFilter,
                Integer.class, args.toArray()
        );
        Integer today = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_answers a JOIN questions q ON q.id=a.question_id WHERE a.user_id=?" + subFilter + " AND DATE(a.created_at)=CURDATE()",
                Integer.class, args.toArray()
        );
        int a = answered == null ? 0 : answered;
        int c = correct == null ? 0 : correct;
        out.put("answered", a);
        out.put("correct", c);
        out.put("accuracy", a == 0 ? 0 : Math.round(1000.0 * c / a) / 10.0);
        out.put("wrongOpen", wrongOpen == null ? 0 : wrongOpen);
        out.put("favorites", fav == null ? 0 : fav);
        out.put("today", today == null ? 0 : today);
        return out;
    }

    public List<Map<String, Object>> knowledgeStats(String userKey, String subject) {
        String sql = """
                SELECT knowledge_point AS knowledgePoint, total, correct, mastery_rate AS masteryRate
                FROM user_knowledge_stats WHERE user_id=?
                """;
        List<Object> args = new ArrayList<>();
        args.add(toUserId(userKey));
        if (subject != null && !subject.isBlank()) {
            sql += " AND subject=?";
            args.add(subject);
        }
        sql += " ORDER BY mastery_rate ASC, total DESC LIMIT 30";
        return jdbc.queryForList(sql, args.toArray());
    }

    private List<Map<String, Object>> mapRows(List<Map<String, Object>> rows, boolean withAnswer) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("subject", row.get("subject"));
            m.put("type", row.get("type"));
            m.put("knowledgePoint", row.get("knowledgePoint"));
            m.put("content", row.get("content"));
            m.put("options", readOptions(row.get("optionsJson")));
            m.put("difficulty", row.get("difficulty"));
            if (row.get("year") != null) m.put("year", row.get("year"));
            if (row.get("wrongCount") != null) m.put("wrongCount", row.get("wrongCount"));
            if (row.get("lastWrongAt") != null) m.put("lastWrongAt", String.valueOf(row.get("lastWrongAt")));
            if (row.get("status") != null) m.put("status", row.get("status"));
            if (row.get("favoritedAt") != null) m.put("favoritedAt", String.valueOf(row.get("favoritedAt")));
            if (withAnswer) {
                m.put("answer", row.get("answer"));
                m.put("analysis", row.get("analysis"));
                m.put("analysisIdea", row.get("analysisIdea"));
                m.put("analysisKp", row.get("analysisKp"));
            }
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> readOptions(Object raw) {
        if (raw == null) return List.of();
        try {
            return mapper.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeJson(Object raw) {
        try {
            if (raw instanceof String s) return s;
            return mapper.writeValueAsString(raw == null ? List.of() : raw);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
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
