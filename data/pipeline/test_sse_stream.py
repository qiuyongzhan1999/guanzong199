# -*- coding: utf-8 -*-
"""测试 SSE 流式接口（标准库 urllib，无第三方依赖）"""
import json
import time
import urllib.request

BASE = "http://127.0.0.1:8080"

def test_stream(name, url, body, timeout=120):
    print(f"\n===== {name} =====")
    t0 = time.time()
    got = []
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            print("HTTP", r.status, r.headers.get("Content-Type"))
            while True:
                line = r.readline()
                if not line:
                    break
                line = line.decode("utf-8", "ignore").strip()
                if not line:
                    continue
                if not line.startswith("data:"):
                    print("  raw:", line[:120])
                    continue
                data = line[5:].strip()
                if not data or data == "[DONE]":
                    continue
                try:
                    ev = json.loads(data)
                except Exception:
                    print("  non-json:", data[:120])
                    continue
                typ = ev.get("type")
                if typ == "delta":
                    got.append(ev.get("text", ""))
                    print(f"  delta(+{len(ev.get('text',''))}ch) total={sum(len(x) for x in got)}ch")
                elif typ == "done":
                    adv = str(ev.get("advice", ""))
                    rep = ev.get("report")
                    print(f"  DONE keys={list(ev.keys())} advice_len={len(adv)} report_keys={list(rep.keys()) if isinstance(rep, dict) else 'n/a'}")
                    got.append(("DONE", ev))
                elif typ == "error":
                    print("  ERROR:", ev.get("message"))
                    got.append(("ERROR", ev))
                else:
                    print("  OTHER:", str(ev)[:200])
        print(f"  ---- {name} 完成，耗时 {time.time()-t0:.1f}s，delta 块数={len([g for g in got if isinstance(g,str)])}")
        return got
    except Exception as e:
        print("  FAIL:", repr(e))
        return []

if __name__ == "__main__":
    # 1. 择校建议流式
    test_stream(
        "match/advice/stream",
        BASE + "/api/match/advice/stream",
        {
            "majorCode": "125300",
            "studyMode": "parttime",
            "scoreMin": 200,
            "scoreMax": 230,
            "provinces": []
        }
    )
    # 2. 作文批改流式（论说文，>80字）
    essay = ("论说文测试：坚持与创新是企业发展的双轮驱动。面对复杂多变的市场环境，"
             "企业唯有坚守初心、脚踏实地，才能在激烈竞争中站稳脚跟。同时，创新又是突破瓶颈的关键，"
             "固步自封终将被时代淘汰。因此，既要稳中求进，也要勇于变革，方能行稳致远。")
    test_stream(
        "essay/grade/stream",
        BASE + "/api/essay/grade/stream",
        {"type": "thesis", "essay": essay}
    )
