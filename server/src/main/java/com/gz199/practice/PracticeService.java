package com.gz199.practice;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷题业务层：科目进度、答题判分、错题自动移出、掌握度。
 *
 * 错误码约定（code）：
 * 0 成功；40001 参数错误；40401 资源不存在；50001 表未就绪
 */
@Service
public class PracticeService {
    public static final int OK = 0;
    public static final int ERR_PARAM = 40001;
    public static final int ERR_NOT_FOUND = 40401;
    public static final int ERR_TABLES = 50001;

    private final PracticeRepository repo;

    public PracticeService(PracticeRepository repo) {
        this.repo = repo;
    }

    private Map<String, Object> gate() {
        if (!repo.tablesReady()) {
            return err(ERR_TABLES, "刷题表未建立，请执行 server/sql/practice_tables.sql");
        }
        return null;
    }

    private static Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("code", OK);
        if (data != null) out.putAll(data);
        return out;
    }

    private static Map<String, Object> err(int code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("code", code);
        out.put("error", message);
        return out;
    }

    /** GET /api/subjects */
    public Map<String, Object> subjects(String userKey) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        String key = str(userKey, "guest");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> s : repo.listSubjects()) {
            Map<String, Object> row = new LinkedHashMap<>(s);
            long id = toLong(s.get("id"));
            row.put("progress", repo.subjectProgress(key, id));
            items.add(row);
        }
        return ok(Map.of("items", items));
    }

    /** GET /api/chapters?subject_id= */
    public Map<String, Object> chapters(String userKey, Long subjectId) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        if (subjectId == null || subjectId <= 0) return err(ERR_PARAM, "subject_id 必填");
        if (repo.findSubject(subjectId) == null) return err(ERR_NOT_FOUND, "科目不存在");
        long uid = PracticeRepository.toUserId(str(userKey, "guest"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> c : repo.listChapters(subjectId)) {
            Map<String, Object> row = new LinkedHashMap<>(c);
            long cid = toLong(c.get("id"));
            Double mastery = repo.chapterMastery(uid, cid);
            row.put("masteryRate", mastery);
            row.put("questionCount", repo.countQuestionsInChapter(cid));
            items.add(row);
        }
        return ok(Map.of("subjectId", subjectId, "items", items));
    }

    /** GET /api/knowledge-points?chapter_id= */
    public Map<String, Object> knowledgePoints(Long chapterId) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        if (chapterId == null || chapterId <= 0) return err(ERR_PARAM, "chapter_id 必填");
        if (repo.findChapter(chapterId) == null) return err(ERR_NOT_FOUND, "章节不存在");
        return ok(Map.of("chapterId", chapterId, "items", repo.listKnowledgePoints(chapterId)));
    }

    /** GET /api/questions */
    public Map<String, Object> listQuestions(
            Long subjectId, Long chapterId, Long kpId,
            Integer difficulty, String questionType,
            String mode, Integer page, Integer pageSize
    ) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        String m = mode == null ? "order" : mode;
        List<Map<String, Object>> items = repo.listQuestions(
                subjectId, chapterId, kpId, difficulty, questionType, m, p, size
        );
        int total = repo.countFiltered(subjectId, chapterId, kpId, difficulty, questionType);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", Math.max(1, Math.min(size, 50)));
        return ok(data);
    }

    /** GET /api/questions/{id} */
    public Map<String, Object> getQuestion(long id, String userKey) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        Map<String, Object> q = repo.findQuestion(id, false);
        if (q == null) return err(ERR_NOT_FOUND, "题目不存在");
        if (userKey != null && !userKey.isBlank()) {
            q.put("favorited", repo.isFavorite(userKey, id));
        }
        return ok(Map.of("item", q));
    }

    /** GET /api/questions/years */
    public Map<String, Object> listYears() {
        Map<String, Object> g = gate();
        if (g != null) return g;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : repo.listYears()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year", row.get("year"));
            m.put("count", row.get("count"));
            items.add(m);
        }
        return ok(Map.of("items", items));
    }

    /** GET /api/questions/by-year?year=2024 */
    public Map<String, Object> listQuestionsByYear(int year) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        if (year <= 0) return err(ERR_PARAM, "year 必填");
        List<Long> ids = repo.listQuestionIdsByYear(year);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ids", ids);
        data.put("year", year);
        data.put("total", ids.size());
        return ok(data);
    }

    /** POST /api/questions/submit */
    public Map<String, Object> submit(Map<String, Object> body) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        if (body == null) body = Map.of();

        String userKey = str(body.get("userKey"), "guest");
        long questionId = toLong(first(body, "questionId", "question_id"));
        String answer = str(first(body, "answer", "userAnswer"), "").trim().toUpperCase();
        Integer timeMs = null;
        Object ts = first(body, "timeSpent", "time_spent_ms");
        if (ts != null) timeMs = toInt(ts);

        if (questionId <= 0 || answer.isEmpty()) {
            return err(ERR_PARAM, "questionId 与 answer 必填");
        }

        Map<String, Object> full = repo.findQuestion(questionId, true);
        if (full == null) return err(ERR_NOT_FOUND, "题目不存在");

        String right = str(full.get("answer"), "").trim().toUpperCase();
        boolean correct = right.equals(answer);

        repo.insertAnswer(userKey, questionId, answer, correct, timeMs);
        Map<String, Object> wrongInfo = repo.applyWrongBook(userKey, questionId, correct);
        long kpId = toLong(full.get("knowledgePointId"));
        repo.upsertKnowledge(userKey, kpId, correct);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("correct", correct);
        data.put("answer", right);
        data.put("analysis", full.get("analysis"));
        data.put("analysisIdea", full.get("analysisIdea"));
        data.put("favorited", repo.isFavorite(userKey, questionId));
        data.put("wrongUpdated", wrongInfo.get("updatedWrong"));
        data.put("autoRemoved", wrongInfo.get("autoRemoved"));
        data.put("consecutiveCorrect", wrongInfo.get("consecutiveCorrect"));
        data.put("wrongCount", wrongInfo.get("wrongCount"));
        data.put("masteryUpdated", kpId > 0);
        return ok(data);
    }

    /** POST /api/favorites/toggle */
    public Map<String, Object> toggleFavorite(Map<String, Object> body) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        if (body == null) body = Map.of();
        String userKey = str(body.get("userKey"), "guest");
        long questionId = toLong(first(body, "questionId", "question_id"));
        if (questionId <= 0) return err(ERR_PARAM, "questionId 必填");
        if (repo.findQuestion(questionId, false) == null) return err(ERR_NOT_FOUND, "题目不存在");
        boolean on = repo.toggleFavorite(userKey, questionId);
        return ok(Map.of("favorited", on));
    }

    /** GET /api/favorites */
    public Map<String, Object> favorites(String userKey, Long subjectId, Long chapterId) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        return ok(Map.of(
                "items",
                repo.listFavorites(str(userKey, "guest"), subjectId, chapterId)
        ));
    }

    /** GET /api/wrong-questions */
    public Map<String, Object> wrongQuestions(String userKey, Long subjectId, String sort) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        return ok(Map.of(
                "items",
                repo.listWrong(str(userKey, "guest"), subjectId, sort)
        ));
    }

    /** GET /api/stats/knowledge */
    public Map<String, Object> statsKnowledge(String userKey, Long subjectId) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        List<Map<String, Object>> items = repo.knowledgeRadar(str(userKey, "guest"), subjectId);
        // 雷达图精简：取有作答或前 12 个
        List<Map<String, Object>> radar = new ArrayList<>();
        for (Map<String, Object> it : items) {
            int total = toInt(it.get("totalCount"));
            if (total > 0) radar.add(it);
        }
        if (radar.isEmpty()) {
            radar = items.size() > 12 ? items.subList(0, 12) : items;
        } else if (radar.size() > 12) {
            radar = radar.subList(0, 12);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("radar", radar);
        return ok(data);
    }

    /** GET /api/stats/overview */
    public Map<String, Object> statsOverview(String userKey, Long subjectId) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        String key = str(userKey, "guest");
        Map<String, Object> stats = repo.overview(key, subjectId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("trend", repo.accuracyTrend(key, subjectId, 14));
        return ok(data);
    }

    /** GET /api/stats/history 答题历史 */
    public Map<String, Object> history(String userKey, Long subjectId, Integer limit) {
        Map<String, Object> g = gate();
        if (g != null) return g;
        int lim = limit == null ? 50 : limit;
        return ok(Map.of("items", repo.listHistory(str(userKey, "guest"), subjectId, lim)));
    }

    /** 健康检查 */
    public Map<String, Object> health() {
        Map<String, Object> g = gate();
        if (g != null) return g;
        return ok(Map.of(
                "tablesReady", true,
                "questionCount", repo.countQuestions(null),
                "subjectCount", repo.listSubjects().size()
        ));
    }

    private static Object first(Map<String, Object> body, String a, String b) {
        if (body.containsKey(a) && body.get(a) != null) return body.get(a);
        return body.get(b);
    }

    private static String str(Object o, String dft) {
        if (o == null) return dft;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? dft : s;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int toInt(Object o) {
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
