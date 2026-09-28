# -*- coding: utf-8 -*-
"""专门诊断会计非全的 band 分布与 benchmark"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"

def match(major, mode, lo, hi):
    body = {"majorCode": major, "studyMode": mode, "scoreMin": lo, "scoreMax": hi, "provinces": []}
    req = urllib.request.Request(BASE + "/api/match", data=json.dumps(body).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8", "ignore"))

for lo, hi in [(230, 240), (235, 240), (240, 250), (250, 260), (220, 230), (210, 220)]:
    d = match("125300", "parttime", lo, hi)
    ranked = d.get("ranked", [])
    unmatched = d.get("unmatched", [])
    counts = {}
    for row in ranked:
        b = row.get("band")
        counts[b] = counts.get(b, 0) + 1
    print(f"[{lo},{hi}] ranked={len(ranked)} unmatched={len(unmatched)} 分布={counts}")
    for row in ranked[:5]:
        print(f"   {row.get('name')} | band={row.get('band')} | benchmark={row.get('benchmark')}({row.get('benchmarkLabel')}) | 近三年最低={row.get('recentMinScores')} 最高={row.get('recentMaxScores')} | 录取率={row.get('admitRate')} | year={row.get('year')}")
    for row in unmatched[:3]:
        print(f"   [无对照] {row.get('name')} | 近三年最低={row.get('recentMinScores')} 最高={row.get('recentMaxScores')}")
