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

@RestController
@RequestMapping("/api/practice")
public class PracticeController {
    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return practice.ensureSeeded();
    }

    @PostMapping("/reseed")
    public Map<String, Object> reseed() {
        return practice.reseed();
    }

    @GetMapping("/questions")
    public Map<String, Object> questions(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false, defaultValue = "order") String mode,
            @RequestParam(required = false) Integer limit
    ) {
        return practice.listQuestions(subject, type, knowledgePoint, mode, limit);
    }

    @GetMapping("/questions/{id}")
    public Map<String, Object> one(
            @PathVariable long id,
            @RequestParam(required = false) String userKey
    ) {
        return practice.getQuestion(id, userKey);
    }

    @PostMapping("/questions/submit")
    public Map<String, Object> submit(@RequestBody Map<String, Object> body) {
        return practice.submit(body == null ? Map.of() : body);
    }

    @PostMapping("/favorites/toggle")
    public Map<String, Object> favToggle(@RequestBody Map<String, Object> body) {
        return practice.toggleFavorite(body == null ? Map.of() : body);
    }

    @GetMapping("/favorites")
    public Map<String, Object> favorites(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(required = false) String subject
    ) {
        return practice.favorites(userKey, subject);
    }

    @GetMapping("/wrong-questions")
    public Map<String, Object> wrong(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false, defaultValue = "0") Integer status
    ) {
        return practice.wrongList(userKey, subject, status);
    }

    @PostMapping("/wrong-questions/master")
    public Map<String, Object> master(@RequestBody Map<String, Object> body) {
        return practice.master(body == null ? Map.of() : body);
    }

    @GetMapping("/stats/overview")
    public Map<String, Object> overview(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(required = false) String subject
    ) {
        return practice.overview(userKey, subject);
    }

    @GetMapping("/stats/knowledge")
    public Map<String, Object> knowledge(
            @RequestParam(required = false, defaultValue = "guest") String userKey,
            @RequestParam(required = false) String subject
    ) {
        return practice.knowledge(userKey, subject);
    }
}
