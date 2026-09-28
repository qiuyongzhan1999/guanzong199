# -*- coding: utf-8 -*-
"""EssayController done 事件补全字段：type/typeLabel/wordCount/essay/inputMode 等"""
import io

p = r"D:\123\gz199\server\src\main\java\com\gz199\web\EssayController.java"
s = io.open(p, encoding="utf-8").read()

old = '''                if (out.containsKey("error")) {
                    emitJson(emitter, Map.of("type", "error", "message", String.valueOf(out.get("error"))));
                } else {
                    Map<String, Object> done = new HashMap<>();
                    done.put("type", "done");
                    done.put("summary", out.get("summary"));
                    done.put("report", out.get("report"));
                    emitJson(emitter, done);
                }'''
new = '''                if (out.containsKey("error")) {
                    emitJson(emitter, Map.of("type", "error", "message", String.valueOf(out.get("error"))));
                } else {
                    Map<String, Object> done = new HashMap<>();
                    done.put("type", "done");
                    done.put("summary", out.get("summary"));
                    done.put("report", out.get("report"));
                    done.put("disclaimer", out.get("disclaimer"));
                    done.put("typeLabel", out.get("typeLabel"));
                    done.put("wordCount", out.get("wordCount"));
                    done.put("essay", out.get("essay"));
                    done.put("hasEssayImage", out.get("hasEssayImage"));
                    done.put("hasMaterialImage", out.get("hasMaterialImage"));
                    done.put("inputMode", out.get("inputMode"));
                    emitJson(emitter, done);
                }'''
assert old in s, "pattern not found"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("PATCHED EssayController done fields")
