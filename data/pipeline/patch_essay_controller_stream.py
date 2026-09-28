# -*- coding: utf-8 -*-
"""EssayController 增加 POST /api/essay/grade/stream（SSE 流式批改）"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\web\EssayController.java"
s = io.open(p, encoding="utf-8").read()

if "grade/stream" in s:
    print("ALREADY PATCHED")
else:
    # 1) imports
    old_imp = '''import com.gz199.ai.EssayGradeService;
import com.gz199.ai.EssayOcrService;
import org.springframework.web.bind.annotation.PostMapping;'''
    new_imp = '''import com.gz199.ai.EssayGradeService;
import com.gz199.ai.EssayOcrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;'''
    assert old_imp in s
    s = s.replace(old_imp, new_imp, 1)

    old_imp2 = '''import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController'''
    new_imp2 = '''import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController'''
    assert old_imp2 in s
    s = s.replace(old_imp2, new_imp2, 1)

    # 2) 类内加 stream 池 + mapper
    old_head = '''public class EssayController {
    private final EssayGradeService essayGrade;'''
    new_head = '''public class EssayController {
    private static final ExecutorService STREAM_POOL = Executors.newCachedThreadPool();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EssayGradeService essayGrade;'''
    assert old_head in s
    s = s.replace(old_head, new_head, 1)

    # 3) 在 grade 方法后加 gradeStream
    old_grade = '''    /** AI 批改：粘贴正文 → DeepSeek 结构化阅卷反馈。 */
    @PostMapping("/grade")
    public Map<String, Object> grade(@RequestBody Map<String, Object> body) {
        return essayGrade.grade(body == null ? Map.of() : body);
    }
'''
    new_grade = '''    /** AI 批改：粘贴正文 → DeepSeek 结构化阅卷反馈。 */
    @PostMapping("/grade")
    public Map<String, Object> grade(@RequestBody Map<String, Object> body) {
        return essayGrade.grade(body == null ? Map.of() : body);
    }

    /** AI 批改（流式 SSE）：DeepSeek 边生成边推 delta，结束推 done(report)，失败推 error。 */
    @PostMapping("/grade/stream")
    public SseEmitter gradeStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Map<String, Object> safe = body == null ? Map.of() : body;
        STREAM_POOL.execute(() -> {
            try {
                Map<String, Object> out = essayGrade.gradeStream(safe, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("message")
                                .data(toJson(Map.of("type", "delta", "text", delta))));
                    } catch (Exception e) {
                        throw new RuntimeException("client disconnected");
                    }
                });
                if (out.containsKey("error")) {
                    emitJson(emitter, Map.of("type", "error", "message", String.valueOf(out.get("error"))));
                } else {
                    Map<String, Object> done = new HashMap<>();
                    done.put("type", "done");
                    done.put("summary", out.get("summary"));
                    done.put("report", out.get("report"));
                    emitJson(emitter, done);
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "批改暂时失败" : e.getMessage();
                if (msg.contains("client disconnected")) {
                    // 客户端已断开，不再发送
                } else {
                    try {
                        emitJson(emitter, Map.of("type", "error", "message", msg.length() > 300 ? msg.substring(0, 300) : msg));
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        });
        return emitter;
    }

    private static void emitJson(SseEmitter emitter, Map<String, Object> payload) throws java.io.IOException {
        emitter.send(SseEmitter.event().name("message").data(toJson(payload)));
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\\"type\\":\\"error\\",\\"message\\":\\"serialize failed\\"}";
        }
    }
'''
    assert old_grade in s
    s = s.replace(old_grade, new_grade, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PATCHED EssayController")
