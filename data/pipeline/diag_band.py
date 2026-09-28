# -*- coding: utf-8 -*-
"""诊断：为什么全是冲 —— 用多组区间调 /api/match，对比 benchmark 与展示列"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"

def match(major, mode, lo, hi, provinces=None):
    body = {"majorCode": major, "studyMode": mode, "scoreMin": lo, "scoreMax": hi, "provinces": provinces or []}
    req = urllib.request.Request(BASE + "/api/match", data=json.dumps(body).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8", "ignore"))

def show(title, data):
    counts = {}
    print(f"\n--- {title} ---")
    for row in data.get("ranked", []):
        b = row.get("band")
        counts[b] = counts.get(b, 0) + 1
    print("分布:", counts, "| 总:", len(data.get("ranked", [])))
    for row in data.get("ranked", [])[:4]:
        print(f"  {row.get('name')} | band={row.get('band')} | benchmark={row.get('benchmark')}({row.get('benchmarkLabel')}) "
              f"| 近三年最低={row.get('recentMinScores')} 最高={row.get('recentMaxScores')} 平均={row.get('recentMinAvg')} "
              f"| 录取率={row.get('admitRate')} | E分列(year)={row.get('year')}")

for major, mode, label in [("125300", "parttime", "会计非全"), ("125300", "fulltime", "会计全日制"), ("125500", "fulltime", "图书情报全日制")]:
    d = match(major, mode, 230, 240)
    show(f"{label} 区间[230,240]", d)
    d2 = match(major, mode, 220, 250)
    show(f"{label} 区间[220,250]", d2)
    d3 = match(major, mode, 250, 260)
    show(f"{label} 区间[250,260]", d3)
