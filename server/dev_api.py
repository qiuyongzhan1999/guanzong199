# -*- coding: utf-8 -*-
"""
本地开发用轻量 API（不依赖 JDK）。正式后端是 server/ 里的 Spring Boot。

用法：
  python server/dev_api.py
  浏览器打开 http://127.0.0.1:8080/api/health

然后把小程序 utils/api.js 的 API_BASE 改成 http://127.0.0.1:8080
开发者工具勾选：不校验合法域名

本服务只读 JSON / ai_packs，不调 DeepSeek（秒级）。
"""
from __future__ import annotations

import json
from functools import lru_cache
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

DATA = Path(__file__).resolve().parents[1] / "data"
PACKS = DATA / "ai_packs"
PORT = 8080
B_PROVINCES = {"内蒙古", "广西", "海南", "贵州", "云南", "西藏", "甘肃", "青海", "宁夏", "新疆"}


@lru_cache(maxsize=32)
def load(name: str):
    path = DATA / name
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def as_int(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def match(row, school_code, year, major_code, study_mode):
    if school_code and str(row.get("schoolCode")) != school_code:
        return False
    if year is not None and as_int(row.get("year")) != year:
        return False
    if major_code and str(row.get("majorCode")) != major_code:
        return False
    if study_mode and str(row.get("studyMode")) != study_mode:
        return False
    return True


def pick_nation(row, province):
    is_b = province in B_PROVINCES
    total = row["bTotal"] if is_b else row["aTotal"]
    en = row["bEnglish"] if is_b else row["aEnglish"]
    comp = row["bComprehensive"] if is_b else row["aComprehensive"]
    return {
        "year": row["year"],
        "zone": "B类" if is_b else "A类",
        "total": total,
        "english": en,
        "comprehensive": comp,
        "text": f"{total}（{'B' if is_b else 'A'}类）",
        "detail": f"英{en} / 综{comp}",
        "sourceUrl": row.get("sourceUrl"),
        "sourceName": row.get("sourceName"),
        "syncedAt": row.get("syncedAt"),
    }


def years():
    ys = sorted({as_int(r["year"]) for r in load("nation_lines.json") if r.get("year")}, reverse=True)
    return ys


def load_pack(school, major, mode):
    path = PACKS / f"{school}_{major}_{mode}.json"
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def trend_from_pack(pack, school, major, mode, end_year):
    out = []
    if pack and isinstance(pack.get("yearly_data"), list):
        for row in pack["yearly_data"]:
            out.append({
                "year": as_int(row.get("year")),
                "reexamMinScore": as_int(row.get("reexam_min_score") or row.get("reexamMinScore")),
                "minScore": as_int(row.get("min_score") or row.get("minScore")),
                "maxScore": as_int(row.get("max_score") or row.get("maxScore")),
                "admitCount": as_int(row.get("admit_count") or row.get("admitCount")),
                "reexamCount": as_int(row.get("reexam_count") or row.get("reexamCount")),
                "nationTotalA": as_int(row.get("nation_a_total")),
                "status": row.get("status"),
            })
    else:
        for y in range(end_year - 4, end_year + 1):
            adm = next(
                (r for r in load("admissions.json") if match(r, school, y, major, mode)),
                None,
            )
            out.append({
                "year": y,
                "reexamMinScore": None if not adm else adm.get("reexamMinScore"),
                "minScore": None if not adm else adm.get("minScore"),
                "maxScore": None if not adm else adm.get("maxScore"),
                "admitCount": None if not adm else adm.get("admitCount"),
                "reexamCount": None if not adm else adm.get("reexamCount"),
            })
    out.sort(key=lambda x: x.get("year") or 0)
    return out


def handle(path: str, qs: dict):
    def q(name, default=None):
        vals = qs.get(name)
        return vals[0] if vals else default

    if path == "/api/health":
        return {"ok": True, "dataDir": str(DATA), "engine": "dev_api.py", "deepseek": False}

    if path == "/api/years":
        return years()

    if path == "/api/nation-lines":
        year = as_int(q("year"))
        major = q("majorCode")
        rows = []
        for row in load("nation_lines.json"):
            if year is not None and as_int(row.get("year")) != year:
                continue
            if major and major not in (row.get("majorCodes") or []):
                continue
            rows.append(row)
        return rows

    if path == "/api/programs":
        return [
            r
            for r in load("programs.json")
            if match(r, q("schoolCode"), as_int(q("year")), q("majorCode"), q("studyMode"))
        ]

    if path == "/api/admissions":
        return [
            r
            for r in load("admissions.json")
            if match(r, q("schoolCode"), as_int(q("year")), q("majorCode"), q("studyMode"))
        ]

    if path == "/api/school-detail":
        school = q("schoolCode")
        year = as_int(q("year"))
        major = q("majorCode")
        mode = q("studyMode")
        province = q("province") or ""
        year_list = years()
        end_year = year if not year_list else max(year or 0, year_list[0])
        pack = load_pack(school, major, mode)
        program = next(
            (r for r in load("programs.json") if match(r, school, year, major, mode)),
            None,
        )
        admission = next(
            (r for r in load("admissions.json") if match(r, school, year, major, mode)),
            None,
        )
        nation = next(
            (
                r
                for r in load("nation_lines.json")
                if as_int(r.get("year")) == year and major in (r.get("majorCodes") or [])
            ),
            None,
        )
        body = {
            "program": program,
            "admission": admission,
            "nationLine": pick_nation(nation, province) if nation else None,
            "years": year_list,
            "yearlyTrend": trend_from_pack(pack, school, major, mode, end_year),
            "hasPack": pack is not None,
        }
        if pack:
            body["yearlyData"] = pack.get("yearly_data")
            body["examRules"] = pack.get("exam_rules")
            body["dataSource"] = pack.get("data_source")
            body["majorInfo"] = pack.get("major_info")
        return body

    return {"error": "not found", "path": path}


class Handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)
        body = handle(parsed.path, qs)
        raw = json.dumps(body, ensure_ascii=False).encode("utf-8")
        code = 404 if isinstance(body, dict) and body.get("error") == "not found" else 200
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self._cors()
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")

    def log_message(self, fmt, *args):
        print(fmt % args)


if __name__ == "__main__":
    print(f"dev API on http://127.0.0.1:{PORT}  data={DATA} (read-only, no DeepSeek)")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
