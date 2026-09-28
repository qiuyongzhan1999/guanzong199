# -*- coding: utf-8 -*-
"""综合测试：流式接口 done 字段 + 原接口回归"""
import json
import time
import urllib.request

BASE = "http://127.0.0.1:8080"

def post_json(url, body, timeout=120):
    req = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "ignore")

def stream_collect(url, body, timeout=180):
    """读取 SSE，返回 (delta_texts, done_ev, error_ev)"""
    deltas, done, error = [], None, None
    req = urllib.request.Request(
        url, data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        while True:
            line = r.readline()
            if not line:
                break
            line = line.decode("utf-8", "ignore").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if not data or data == "[DONE]":
                continue
            try:
                ev = json.loads(data)
            except Exception:
                continue
            if ev.get("type") == "delta":
                deltas.append(ev.get("text", ""))
            elif ev.get("type") == "done":
                done = ev
            elif ev.get("type") == "error":
                error = ev
    return deltas, done, error

def check(name, cond, detail=""):
    print(("  PASS " if cond else "  FAIL ") + name + ((" | " + detail) if detail and not cond else ""))
    return cond

results = []
print("===== 1. 流式批改 done 字段 =====")
essay = ("论说文测试：坚持与创新是企业发展的双轮驱动。面对复杂多变的市场环境，"
         "企业唯有坚守初心、脚踏实地，才能在激烈竞争中站稳脚跟。同时，创新又是突破瓶颈的关键，"
         "固步自封终将被时代淘汰。因此，既要稳中求进，也要勇于变革，方能行稳致远。")
t0 = time.time()
deltas, done, err = stream_collect(BASE + "/api/essay/grade/stream",
    {"type": "thesis", "essay": essay})
results.append(check("delta 块数 > 50", len(deltas) > 50, f"got {len(deltas)}"))
results.append(check("done 事件存在", done is not None))
if done:
    for k in ["type", "typeLabel", "wordCount", "essay", "summary", "report", "disclaimer", "inputMode"]:
        results.append(check(f"done.{k} 存在", k in done))
    results.append(check("done.report.score 存在", isinstance(done.get("report"), dict) and "score" in done.get("report", {})))
print(f"  耗时 {time.time()-t0:.1f}s")

print("\n===== 2. 流式择校建议 =====")
t0 = time.time()
deltas2, done2, err2 = stream_collect(BASE + "/api/match/advice/stream",
    {"majorCode": "125300", "studyMode": "parttime", "scoreMin": 200, "scoreMax": 230, "provinces": []})
results.append(check("advice delta 块数 > 20", len(deltas2) > 20, f"got {len(deltas2)}"))
results.append(check("done.advice 存在", done2 is not None and done2.get("advice")))
print(f"  耗时 {time.time()-t0:.1f}s")

print("\n===== 3. 原接口回归 =====")
st, body = post_json(BASE + "/api/essay/grade", {"type": "thesis", "essay": essay})
j = json.loads(body)
results.append(check("grade HTTP 200", st == 200))
results.append(check("grade 无 error", not j.get("error"), str(j.get("error"))[:60]))
results.append(check("grade.report.score", isinstance(j.get("report"), dict) and "score" in j.get("report", {})))

st, body = post_json(BASE + "/api/match/advice",
    {"majorCode": "125300", "studyMode": "parttime", "scoreMin": 200, "scoreMax": 230, "provinces": []})
j2 = json.loads(body)
results.append(check("advice HTTP 200", st == 200))
results.append(check("advice 有内容", j2.get("advice") or j2.get("adviceBlocks"), str(j2.get("adviceNote"))[:60]))

print("\n===== 4. ai/ping =====")
req = urllib.request.Request(BASE + "/api/ai/ping")
with urllib.request.urlopen(req, timeout=60) as r:
    pj = json.loads(r.read().decode("utf-8", "ignore"))
results.append(check("ping ok", pj.get("ok") is True, str(pj.get("error"))[:60]))

print("\n===== 5. 校验失败分支（essay 过短）=====")
deltas3, done3, err3 = stream_collect(BASE + "/api/essay/grade/stream", {"type": "thesis", "essay": "太短"})
results.append(check("短作文返回 error 事件", err3 is not None, str(err3.get("message"))[:60]))
results.append(check("短作文无 done", done3 is None))

fails = [r for r in results if not r]
print(f"\n===== 结果：{len(results)-len(fails)}/{len(results)} 通过，{len(fails)} 失败 =====")
raise SystemExit(1 if fails else 0)
