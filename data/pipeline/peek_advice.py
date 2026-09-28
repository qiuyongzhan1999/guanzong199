# -*- coding: utf-8 -*-
"""查看 AI 建议真实输出文本，确认表格结构与列"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"

def stream_collect(url, body, timeout=180):
    deltas, done, error = [], None, None
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"),
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

# 模拟用户场景：会计非全，区间可能较宽
for lo, hi in [(230, 270), (235, 250)]:
    deltas, done, err = stream_collect(BASE + "/api/match/advice/stream",
        {"majorCode": "125300", "studyMode": "parttime", "scoreMin": lo, "scoreMax": hi, "provinces": []})
    advice = (done or {}).get("advice", "") or ""
    print(f"\n===== 会计非全 [{lo},{hi}] 建议全文 ({len(advice)}字) =====")
    print(advice[:2000])
