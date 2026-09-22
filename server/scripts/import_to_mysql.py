# -*- coding: utf-8 -*-
"""
把 data/*.json 导入 MySQL（gz199）。

哪些是「真数据」、哪些不要硬灌：
- schools.json / catalog.json：研招网结构数据 → 全量导入
- nation_lines.json：有具体分数的国家线 → 导入
- programs.json / admissions.json：仅导入有来源且有实质字段的行（跳过空壳/待查）
- ai_packs/*.json：仅导入含有效招录/学费数字的包（跳过全 null 空壳）

用法（PowerShell）：
  $env:DB_PASSWORD = "你的密码"
  python server/scripts/import_to_mysql.py

可选：
  python server/scripts/import_to_mysql.py --host 127.0.0.1 --user root --db gz199
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data"
PACKS = DATA / "ai_packs"


def connect(args):
    password = args.password or os.environ.get("DB_PASSWORD") or ""
    return pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=password,
        database=args.db,
        charset="utf8mb4",
        autocommit=False,
    )


def load_json(name: str):
    path = DATA / name
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def is_blank(v) -> bool:
    if v is None:
        return True
    s = str(v).strip()
    return s == "" or s == "null" or s == "待同步" or s == "待查"


def useful_text(v) -> bool:
    return not is_blank(v)


def import_schools(cur, rows: list) -> int:
    cur.execute("DELETE FROM schools")
    sql = """
    INSERT INTO schools (
      sch_id, code, name, province, city, authority, logo_url,
      tags_json, flags_json, is_double_first, is_self_line, has_grad_school, status
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'online')
    """
    n = 0
    for s in rows:
        tags = s.get("tags") or []
        flags = s.get("flags") or []
        cur.execute(
            sql,
            (
                str(s.get("schId") or s.get("code") or ""),
                str(s.get("code") or ""),
                s.get("name") or "",
                s.get("province"),
                s.get("city"),
                s.get("authority"),
                s.get("logo"),
                json.dumps(tags, ensure_ascii=False),
                json.dumps(flags, ensure_ascii=False),
                1 if any("双一流" in str(t) for t in tags) else 0,
                1 if any("自划线" in str(f) for f in flags) else 0,
                1 if any("研究生院" in str(f) for f in flags) else 0,
            ),
        )
        n += 1
    return n


def import_catalog(cur, rows: list) -> int:
    cur.execute("DELETE FROM school_catalog")
    sql = """
    INSERT INTO school_catalog (school_code, major_code, major_name, study_mode, year, source_url)
    VALUES (%s,%s,%s,%s,%s,%s)
    """
    n = 0
    for r in rows:
        cur.execute(
            sql,
            (
                str(r.get("schoolCode") or ""),
                str(r.get("majorCode") or ""),
                r.get("majorName") or "",
                str(r.get("studyMode") or ""),
                r.get("year"),
                r.get("sourceUrl"),
            ),
        )
        n += 1
    return n


def import_nation(cur, rows: list) -> int:
    cur.execute("DELETE FROM nation_lines")
    sql = """
    INSERT INTO nation_lines (
      year, family, major_codes_json,
      a_total, a_english, a_comprehensive,
      b_total, b_english, b_comprehensive,
      source_url, source_name, provider, synced_at
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    n = 0
    for r in rows:
        if r.get("aTotal") is None and r.get("bTotal") is None:
            continue
        codes = r.get("majorCodes") or []
        family = str(r.get("family") or (codes[0] if codes else ""))
        synced = r.get("syncedAt")
        cur.execute(
            sql,
            (
                int(r["year"]),
                family,
                json.dumps(codes, ensure_ascii=False),
                r.get("aTotal"),
                r.get("aEnglish"),
                r.get("aComprehensive"),
                r.get("bTotal"),
                r.get("bEnglish"),
                r.get("bComprehensive"),
                r.get("sourceUrl"),
                r.get("sourceName"),
                r.get("provider"),
                synced if synced and "T" not in str(synced) else None,
            ),
        )
        n += 1
    return n


def import_programs(cur, rows: list) -> int:
    cur.execute("DELETE FROM school_programs")
    sql = """
    INSERT INTO school_programs (
      school_code, year, major_code, major_name, study_mode, study_mode_label,
      tuition_text, plan_text, duration_text,
      source_url, source_name, provider, checked_at, synced_at
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    n = 0
    skipped = 0
    for r in rows:
        has_body = useful_text(r.get("tuitionText")) or useful_text(r.get("planText")) or useful_text(r.get("durationText"))
        has_source = useful_text(r.get("sourceUrl")) or useful_text(r.get("checkedAt"))
        if not (has_body and has_source):
            skipped += 1
            continue
        cur.execute(
            sql,
            (
                str(r.get("schoolCode") or ""),
                int(r["year"]),
                str(r.get("majorCode") or ""),
                r.get("majorName") or "",
                str(r.get("studyMode") or ""),
                r.get("studyModeLabel"),
                r.get("tuitionText") if useful_text(r.get("tuitionText")) else None,
                r.get("planText") if useful_text(r.get("planText")) else None,
                r.get("durationText") if useful_text(r.get("durationText")) else None,
                r.get("sourceUrl"),
                r.get("sourceName"),
                r.get("provider"),
                r.get("checkedAt"),
                r.get("syncedAt"),
            ),
        )
        n += 1
    print(f"  programs skipped empty={skipped}")
    return n


def import_admissions(cur, rows: list) -> tuple[int, int]:
    cur.execute("DELETE FROM admission_score_bands")
    cur.execute("DELETE FROM admission_stats")
    sql = """
    INSERT INTO admission_stats (
      school_code, year, major_code, study_mode,
      reexam_min_score, min_score, max_score, admit_count, reexam_count,
      pending_note, source_url, source_name, provider, checked_at, synced_at
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    band_sql = """
    INSERT INTO admission_score_bands (
      admission_id, score_label, score_min, score_max, reexam_count, admit_count, sort_order
    ) VALUES (%s,%s,%s,%s,%s,%s,%s)
    """
    n = 0
    bands_n = 0
    skipped = 0
    for r in rows:
        has_score = (
            r.get("reexamMinScore") is not None
            or r.get("minScore") is not None
            or r.get("admitCount") is not None
        )
        if not has_score:
            skipped += 1
            continue
        cur.execute(
            sql,
            (
                str(r.get("schoolCode") or ""),
                int(r["year"]),
                str(r.get("majorCode") or ""),
                str(r.get("studyMode") or ""),
                r.get("reexamMinScore"),
                r.get("minScore"),
                r.get("maxScore"),
                r.get("admitCount"),
                r.get("reexamCount"),
                r.get("pendingNote"),
                r.get("sourceUrl"),
                r.get("sourceName"),
                r.get("provider"),
                r.get("checkedAt"),
                r.get("syncedAt"),
            ),
        )
        admission_id = cur.lastrowid
        n += 1
        for i, band in enumerate(r.get("scoreBands") or []):
            if not isinstance(band, dict):
                continue
            cur.execute(
                band_sql,
                (
                    admission_id,
                    str(band.get("scoreLabel") or band.get("score_label") or ""),
                    band.get("scoreMin") or band.get("score_min"),
                    band.get("scoreMax") or band.get("score_max"),
                    band.get("reexamCount") or band.get("reexam_count"),
                    band.get("admitCount") or band.get("admit_count"),
                    i,
                ),
            )
            bands_n += 1
    print(f"  admissions skipped empty={skipped}")
    return n, bands_n


def pack_useful(pack: dict) -> bool:
    yearly = pack.get("yearly_data") or []
    hits = 0
    for row in yearly:
        if not isinstance(row, dict):
            continue
        for k in ("reexam_min_score", "min_score", "admit_count", "nation_a_total"):
            if row.get(k) is not None:
                hits += 1
        if useful_text(row.get("tuition_text")):
            hits += 1
    info = pack.get("major_info") or {}
    tuition = info.get("tuition") or {}
    if useful_text(tuition.get("per_year")) or useful_text(info.get("tuition_text")):
        hits += 1
    return hits >= 2


def import_packs(cur) -> tuple[int, int]:
    cur.execute("DELETE FROM school_year_stats")
    cur.execute("DELETE FROM school_data_packs")
    pack_sql = """
    INSERT INTO school_data_packs (
      school_code, school_name, major_code, major_name, study_mode, study_mode_label,
      start_year, end_year, skill, provider, major_info_json, exam_rules_json,
      data_source_json, fetched_at
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    year_sql = """
    INSERT INTO school_year_stats (
      pack_id, school_code, major_code, study_mode, year, status,
      tuition_text, plan_text, duration_text,
      program_source_url, program_source_name,
      reexam_min_score, min_score, max_score, admit_count, reexam_count,
      nation_a_total, nation_a_english, nation_a_comprehensive,
      nation_b_total, nation_b_english, nation_b_comprehensive,
      nation_source_url, nation_source_name,
      admission_source_url, admission_source_name,
      pending_note, score_bands_json, data_confidence
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """
    packs_n = 0
    years_n = 0
    skipped = 0
    if not PACKS.exists():
        return 0, 0
    for path in sorted(PACKS.glob("*.json")):
        if path.name.upper() == "README.MD" or path.name.lower() == "readme.md":
            continue
        try:
            pack = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            skipped += 1
            continue
        if not pack_useful(pack):
            skipped += 1
            continue
        cur.execute(
            pack_sql,
            (
                str(pack.get("schoolCode") or ""),
                pack.get("schoolName"),
                str(pack.get("majorCode") or ""),
                pack.get("majorName"),
                str(pack.get("studyMode") or ""),
                pack.get("studyModeLabel"),
                int(pack.get("startYear") or 0),
                int(pack.get("endYear") or 0),
                pack.get("skill"),
                pack.get("provider"),
                json.dumps(pack.get("major_info") or {}, ensure_ascii=False),
                json.dumps(pack.get("exam_rules") or {}, ensure_ascii=False),
                json.dumps(pack.get("data_source") or {}, ensure_ascii=False),
                pack.get("fetchedAt"),
            ),
        )
        pack_id = cur.lastrowid
        packs_n += 1
        for row in pack.get("yearly_data") or []:
            if not isinstance(row, dict):
                continue
            year = row.get("year")
            if year is None:
                continue
            cur.execute(
                year_sql,
                (
                    pack_id,
                    str(pack.get("schoolCode") or ""),
                    str(pack.get("majorCode") or ""),
                    str(pack.get("studyMode") or ""),
                    int(year),
                    row.get("status"),
                    row.get("tuition_text") if useful_text(row.get("tuition_text")) else None,
                    row.get("plan_text") if useful_text(row.get("plan_text")) else None,
                    row.get("duration_text") if useful_text(row.get("duration_text")) else None,
                    row.get("program_source_url"),
                    row.get("program_source_name"),
                    row.get("reexam_min_score"),
                    row.get("min_score"),
                    row.get("max_score"),
                    row.get("admit_count"),
                    row.get("reexam_count"),
                    row.get("nation_a_total"),
                    row.get("nation_a_english"),
                    row.get("nation_a_comprehensive"),
                    row.get("nation_b_total"),
                    row.get("nation_b_english"),
                    row.get("nation_b_comprehensive"),
                    row.get("nation_source_url"),
                    row.get("nation_source_name"),
                    row.get("admission_source_url"),
                    row.get("admission_source_name"),
                    row.get("pending_note"),
                    json.dumps(row.get("score_bands") or [], ensure_ascii=False),
                    row.get("data_confidence"),
                ),
            )
            years_n += 1
    print(f"  packs skipped empty={skipped}")
    return packs_n, years_n


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="")
    parser.add_argument("--db", default="gz199")
    args = parser.parse_args()

    if not (args.password or os.environ.get("DB_PASSWORD")):
        print("FAIL: set DB_PASSWORD or pass --password")
        return 1

    conn = connect(args)
    try:
        cur = conn.cursor()
        print("import schools…")
        print(" ", import_schools(cur, load_json("schools.json")))
        print("import catalog…")
        print(" ", import_catalog(cur, load_json("catalog.json")))
        print("import nation_lines…")
        print(" ", import_nation(cur, load_json("nation_lines.json")))
        print("import programs (skip empty)…")
        print(" ", import_programs(cur, load_json("programs.json")))
        print("import admissions (skip empty)…")
        a_n, b_n = import_admissions(cur, load_json("admissions.json"))
        print(f"  admissions={a_n} bands={b_n}")
        print("import ai_packs (skip empty)…")
        p_n, y_n = import_packs(cur)
        print(f"  packs={p_n} year_rows={y_n}")
        conn.commit()
        print("DONE")
        return 0
    except Exception as e:
        conn.rollback()
        print("FAIL", e)
        return 2
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
