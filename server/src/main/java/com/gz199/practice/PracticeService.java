package com.gz199.practice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gz199.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticeService {
    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final PracticeRepository repo;
    private final AppProperties appProps;
    private final ObjectMapper mapper;

    public PracticeService(PracticeRepository repo, AppProperties appProps, ObjectMapper mapper) {
        this.repo = repo;
        this.appProps = appProps;
        this.mapper = mapper;
    }

    public Map<String, Object> ensureSeeded() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!repo.tablesReady()) {
            out.put("ok", false);
            out.put("error", "刷题表未建立，请先执行 server/sql/practice_tables.sql");
            return out;
        }
        int n = repo.countQuestions();
        out.put("tablesReady", true);
        out.put("questionCount", n);
        if (n > 0) {
            out.put("ok", true);
            out.put("seeded", false);
            return out;
        }
        int added = seedFromFile();
        out.put("ok", true);
        out.put("seeded", true);
        out.put("added", added);
        out.put("questionCount", repo.countQuestions());
        return out;
    }

    /** 清空并重新灌入管综样例题（修复编码后用） */
    public Map<String, Object> reseed() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!repo.tablesReady()) {
            out.put("ok", false);
            out.put("error", "刷题表未建立");
            return out;
        }
        repo.clearQuestions();
        int added = seedFromFile();
        out.put("ok", true);
        out.put("added", added);
        out.put("questionCount", repo.countQuestions());
        return out;
    }

    private int seedFromFile() {
        Path dir = Path.of(appProps.getDataDir(), "practice");
        if (!Files.isDirectory(dir)) {
            log.warn("practice dir missing: {}", dir);
            return 0;
        }
        int n = 0;
        try (var stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".json")
                                && name.contains("-bulk-");
                    })
                    .sorted()
                    .toList();
            for (Path path : files) {
                n += seedOneFile(path);
            }
        } catch (Exception e) {
            log.error("list seed files failed", e);
        }
        log.info("seeded total {} questions from {}", n, dir);
        return n;
    }

    private int seedOneFile(Path path) {
        try {
            List<Map<String, Object>> list = mapper.readValue(
                    Files.readString(path, StandardCharsets.UTF_8),
                    new TypeReference<>() {}
            );
            if (list == null || list.isEmpty()) return 0;
            int n = repo.insertQuestionsBatch(list);
            log.info("seeded {} from {}", n, path.getFileName());
            return n;
        } catch (Exception e) {
            log.error("seed failed: " + path, e);
            return 0;
        }
    }

    public Map<String, Object> listQuestions(String subject, String type, String kp, String mode, Integer limit) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        int lim = limit == null ? 20 : limit;
        List<Map<String, Object>> items = repo.listQuestions(blankToNull(subject), blankToNull(type), blankToNull(kp), mode, lim);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("items", items);
        out.put("total", items.size());
        return out;
    }

    public Map<String, Object> modules(String subject, String type) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        List<String> items = repo.listModules(blankToNull(subject), blankToNull(type));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("items", items);
        return out;
    }

    public Map<String, Object> getQuestion(long id, String userKey) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        Map<String, Object> q = repo.findQuestion(id, false);
        if (q == null) {
            return Map.of("ok", false, "error", "题目不存在");
        }
        if (userKey != null && !userKey.isBlank()) {
            q.put("favorited", repo.isFavorite(userKey, id));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("item", q);
        return out;
    }

    public Map<String, Object> submit(Map<String, Object> body) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;

        String userKey = str(body.get("userKey"), "guest");
        long questionId = toLong(body.get("questionId"));
        String answer = str(body.get("answer"), "").trim().toUpperCase();
        String mode = str(body.get("mode"), "order");
        Integer timeMs = body.get("timeSpent") == null ? null : toInt(body.get("timeSpent"));

        Map<String, Object> full = repo.findQuestion(questionId, true);
        if (full == null) {
            return Map.of("ok", false, "error", "题目不存在");
        }
        String right = str(full.get("answer"), "").trim().toUpperCase();
        boolean correct = right.equals(answer);

        repo.insertAnswer(userKey, questionId, answer, correct, timeMs, mode);
        repo.upsertWrong(userKey, questionId, correct);
        repo.upsertKnowledge(userKey, str(full.get("subject"), ""), str(full.get("knowledgePoint"), ""), correct);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("correct", correct);
        out.put("answer", right);
        out.put("analysis", full.get("analysis"));
        out.put("analysisIdea", full.get("analysisIdea"));
        out.put("analysisKp", full.get("analysisKp"));
        out.put("favorited", repo.isFavorite(userKey, questionId));
        return out;
    }

    public Map<String, Object> toggleFavorite(Map<String, Object> body) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        String userKey = str(body.get("userKey"), "guest");
        long questionId = toLong(body.get("questionId"));
        boolean on = repo.toggleFavorite(userKey, questionId);
        return Map.of("ok", true, "favorited", on);
    }

    public Map<String, Object> favorites(String userKey, String subject) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        return Map.of("ok", true, "items", repo.listFavorites(str(userKey, "guest"), blankToNull(subject)));
    }

    public Map<String, Object> wrongList(String userKey, String subject, Integer status) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        int st = status == null ? 0 : status;
        return Map.of("ok", true, "items", repo.listWrong(str(userKey, "guest"), blankToNull(subject), st));
    }

    public Map<String, Object> master(Map<String, Object> body) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        String userKey = str(body.get("userKey"), "guest");
        long questionId = toLong(body.get("questionId"));
        repo.markMastered(userKey, questionId);
        return Map.of("ok", true);
    }

    public Map<String, Object> overview(String userKey, String subject) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        Map<String, Object> stats = repo.overview(str(userKey, "guest"), blankToNull(subject));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("stats", stats);
        out.put("questionCount", repo.countQuestions(blankToNull(subject)));
        return out;
    }

    public Map<String, Object> knowledge(String userKey, String subject) {
        Map<String, Object> gate = ensureSeeded();
        if (Boolean.FALSE.equals(gate.get("ok"))) return gate;
        return Map.of("ok", true, "items", repo.knowledgeStats(str(userKey, "guest"), blankToNull(subject)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
