# -*- coding: utf-8 -*-
"""SchoolDataController 增加 POST /api/match/advice/stream（SSE 流式择校建议）"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\web\SchoolDataController.java"
s = io.open(p, encoding="utf-8").read()

if "match/advice/stream" in s:
    print("ALREADY PATCHED")
else:
    # 1) imports
    old_imp = '''import com.gz199.data.MysqlSchoolRepository;
import org.springframework.web.bind.annotation.GetMapping;'''
    new_imp = '''import com.gz199.data.MysqlSchoolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;'''
    assert old_imp in s
    s = s.replace(old_imp, new_imp, 1)

    old_imp2 = '''import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;'''
    new_imp2 = '''import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;'''
    assert old_imp2 in s
    s = s.replace(old_imp2, new_imp2, 1)

    old_imp3 = '''import java.util.Objects;
import java.util.stream.Collectors;'''
    new_imp3 = '''import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;'''
    assert old_imp3 in s
    s = s.replace(old_imp3, new_imp3, 1)

    # 2) 类内加 stream 池 + mapper
    old_head = '''public class SchoolDataController {
    private final JsonDataStore store;'''
    new_head = '''public class SchoolDataController {
    private static final ExecutorService STREAM_POOL = Executors.newCachedThreadPool();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JsonDataStore store;'''
    assert old_head in s
    s = s.replace(old_head, new_head, 1)

    # 3) 在 matchAdviceBody 方法后加 stream 方法
    old_advice = '''    /** 智能择校文字建议（较慢，前端列表出来后再调）。 */
    @PostMapping("/match/advice")
    public Map<String, Object> matchAdviceBody(@RequestBody Map<String, Object> body) {
        return matchAdvice.advise(body == null ? Map.of() : body);
    }
'''
    new_advice = '''    /** 智能择校文字建议（较慢，前端列表出来后再调）。 */
    @PostMapping("/match/advice")
    public Map<String, Object> matchAdviceBody(@RequestBody Map<String, Object> body) {
        return matchAdvice.advise(body == null ? Map.of() : body);
    }

    /** 智能择校文字建议（流式 SSE）：边生成边推 delta，结束推 done，失败推 error。 */
    @PostMapping("/match/advice/stream")
    public SseEmitter matchAdviceStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        Map<String, Object> safe = body == null ? Map.of() : body;
        STREAM_POOL.execute(() -> {
            try {
                String advice = matchAdvice.streamAdvice(safe, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("message")
                                .data(toJson(Map.of("type", "delta", "text", delta))));
                    } catch (Exception e) {
                        throw new RuntimeException("client disconnected");
                    }
                });
                emitJson(emitter, Map.of("type", "done", "advice", advice == null ? "" : advice));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "建议暂时生成失败" : e.getMessage();
                if (msg.contains("client disconnected")) {
                    // 客户端已断开
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
    assert old_advice in s
    s = s.replace(old_advice, new_advice, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PATCHED SchoolDataController")
