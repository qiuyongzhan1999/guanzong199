package com.gz199.web;

import com.gz199.practice.PracticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 刷题 API（V2）。
 *
 * <pre>
 * GET  /api/subjects
 * GET  /api/chapters?subject_id=
 * GET  /api/knowledge-points?chapter_id=
 * GET  /api/questions
 * GET  /api/questions/years
 * GET  /api/questions/by-year?year=
 * GET  /api/questions/{id}
 * POST /api/questions/submit
 * POST /api/favorites/toggle
 * GET  /api/favorites
 * GET  /api/wrong-questions
 * GET  /api/stats/knowledge
 * GET  /api/stats/overview
 * GET  /api/stats/history
 * GET  /api/practice/health  （兼容旧入口）
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class PracticeController {
    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    @GetMapping({"/practice/health", "/health/practice"})
    public Map<String, Object> health() {
        return practice.health();
    }

    @GetMapping("/subjects")
    public Map<String, Object> subjects(
            @RequestParam(required = false, defaultValue = "guest") String userKey
    ) {
        return practice.subjects(userKey);
    }

    @GetMapping("/chapters")
    public Map<String, Object> chapters(
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "guest") String userKey
    ) {
        return practice.chapters(userKey, subjectId);
    }

    @GetMapping("/knowledge-points")
    public Map<String, Object> knowledgePoints(
            @RequestParam(name = "chapter_id", required = false) Long chapterId
    ) {
        return practice.knowledgePoints(chapterId);
    }

    @GetMapping("/questions")
    public Map<String, Object> questions(
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(name = "chapter_id", required = false) Long chapterId,
            @RequestParam(name = "knowledge_point_id", required = false) Long knowledgePointId,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(name = "question_type", required = false) String questionType,
            @RequestParam(required = false, defaultValue = "order") String mode,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") Integer pageSize
    ) {
        return practice.listQuestions(
                subjectId, chapterId, knowledgePointId,
                difficulty, questionType, mode, page, pageSize
        );
    }

    /** 必须写在 /questions/{id} 之前，避免被当成 id */
    @GetMapping("/questions/years")
    public Map<String, Object> years() {
        return practice.listYears();
    }

    @GetMapping("/questions/by-year")
    public Map<String, Object> byYear(@RequestParam int year) {
        return practice.listQuestionsByYear(year);
    }

    @GetMapping("/questions/{id}")
    public Map<String, Object> one(
            @PathVariable long id,
            @RequestParam(required = false) String userKey
    ) {
        return practice.getQuestion(id, userKey);
    }

    @PostMapping("/questions/submit")
    public Map<String, Object> submit(@RequestBody(required = false) Map<String, Object> body) {
        return practice.submit(body == null ? Map.of() : body);
    }

    @PostMapping("/favorites/toggle")
    public Map<String, Object> favToggle(@RequestBody(required = false) Map<String, Object> body) {
        return practice.toggleFavorite(body == null ? Map.of() : body);
    }

    @GetMapping("/favorites")
    public Map<String, Object> favorites(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(name = "chapter_id", required = false) Long chapterId
    ) {
        return practice.favorites(userKey, subjectId, chapterId);
    }

    @GetMapping("/wrong-questions")
    public Map<String, Object> wrong(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "time") String sort
    ) {
        return practice.wrongQuestions(userKey, subjectId, sort);
    }

    @GetMapping("/stats/knowledge")
    public Map<String, Object> knowledge(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(name = "subject_id", required = false) Long subjectId
    ) {
        return practice.statsKnowledge(userKey, subjectId);
    }

    @GetMapping("/stats/overview")
    public Map<String, Object> overview(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(name = "subject_id", required = false) Long subjectId
    ) {
        return practice.statsOverview(userKey, subjectId);
    }

    @GetMapping("/stats/history")
    public Map<String, Object> history(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(name = "subject_id", required = false) Long subjectId,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return practice.history(userKey, subjectId, limit);
    }
}
