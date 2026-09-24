# -*- coding: utf-8 -*-
"""72_import_lexuemiao_all.py：清库（旧来源数据）+ 从 lexuemiao_dump 全量入库
只保留乐学喵数据：school_data_packs / school_year_stats / admission_stats / admission_score_bands / school_programs
"""
import json, os, re, sys, datetime, pymysql

DUMP_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lexuemiao_dump")
MAJOR_CODES = ["125100", "125200", "125300", "125400", "125500", "125600", "125700"]
# 处理顺序：33(MEM) 在 34(项目管理MEM) 之前，34 重复学校跳过
COMBOS = [
    (28, "125300", "MPAcc会计", 1, "fulltime"), (28, "125300", "MPAcc会计", 2, "parttime"),
    (29, "125700", "MAud审计", 1, "fulltime"), (29, "125700", "MAud审计", 2, "parttime"),
    (30, "125500", "MLis图书情报", 1, "fulltime"), (30, "125500", "MLis图书情报", 2, "parttime"),
    (31, "125100", "MBA工商管理", 1, "fulltime"), (31, "125100", "MBA工商管理", 2, "parttime"),
    (32, "125200", "MPA公共管理", 1, "fulltime"), (32, "125200", "MPA公共管理", 2, "parttime"),
    (33, "125600", "MEM工程管理", 1, "fulltime"), (33, "125600", "MEM工程管理", 2, "parttime"),
    (34, "125600", "项目管理MEM", 1, "fulltime"), (34, "125600", "项目管理MEM", 2, "parttime"),
    (35, "125400", "MTA旅游管理", 1, "fulltime"), (35, "125400", "MTA旅游管理", 2, "parttime"),
]
MODE_LABEL = {"fulltime": "全日制", "parttime": "非全日制"}
# 更名学校：乐学喵旧名 -> 本地 schools 表现行名
NAME_ALIAS = {
    "闽江学院": "闽江大学",
    "重庆三峡学院": "重庆三峡科技大学",
    "榆林学院": "榆林大学",
    "滨州医学院": "山东医药大学",
    "绍兴文理学院": "绍兴大学",
}

def connect():
    return pymysql.connect(host="127.0.0.1", user="root",
                           password=os.environ.get("DB_PASSWORD", "qyz123456"),
                           database="gz199", charset="utf8mb4")

def parse_score_line(s):
    """'201/52/104' -> (201,52,104)；'/'、空 -> (None,None,None)"""
    if not s:
        return (None, None, None)
    parts = re.split(r"[/／]", str(s).strip())
    out = []
    for p in parts:
        p = p.strip()
        try:
            out.append(int(p) if p else None)
        except Exception:
            out.append(None)
    while len(out) < 3:
        out.append(None)
    return tuple(out[:3])

def first_int(s):
    if not s:
        return None
    m = re.search(r"\d+", str(s))
    return int(m.group(0)) if m else None

def fmt_wan(v):
    if not v:
        return None
    w = float(v) / 10000.0
    if abs(w - round(w)) < 0.0001:
        return f"{int(round(w))}万/全程"
    return f"{w:.1f}万/全程"

def fmt_duration(v):
    if not v:
        return None
    s = str(v).strip()
    if not s:
        return None
    return s + "年" if re.search(r"\d", s) and "年" not in s else (s or None)

def fmt_plan(v):
    return f"计划招生{int(v)}人" if v else None

def normalize_name(n):
    """全角括号转半角、去空白；并附上去掉括号内容后的主名"""
    s = str(n).replace("（", "(").replace("）", ")").replace(" ", "").replace("\n", "")
    core = re.sub(r"\(.*?\)", "", s).strip()
    return s, core

def find_school(cur, name):
    if not name:
        return None
    name = NAME_ALIAS.get(name.strip(), name)
    s, core = normalize_name(name)
    # 1) 规范化后精确
    cur.execute("SELECT code FROM schools WHERE name=%s", (s,))
    row = cur.fetchone()
    if row:
        return row[0]
    # 2) 规范化后 LIKE（含空格差异兜底）
    cur.execute("SELECT code FROM schools WHERE name LIKE %s", (f"%{s}%",))
    row = cur.fetchone()
    if row:
        return row[0]
    # 3) 去掉括号内容后的主名精确（如 中国石油大学(北京)->中国石油大学）
    if core and core != s:
        cur.execute("SELECT code FROM schools WHERE name=%s", (core,))
        row = cur.fetchone()
        if row:
            return row[0]
    # 4) 主名 LIKE
    if core and core != s:
        cur.execute("SELECT code FROM schools WHERE name LIKE %s", (f"%{core}%",))
        row = cur.fetchone()
        if row:
            return row[0]
    return None

def upsert_pack(cur, code, school_name, major_code, major_name, study_mode, detail):
    cur.execute("SELECT id FROM school_data_packs WHERE school_code=%s AND major_code=%s AND study_mode=%s",
                (code, major_code, study_mode))
    row = cur.fetchone()
    if row:
        pack_id = row[0]
    else:
        cur.execute("""INSERT INTO school_data_packs
            (school_code, school_name, major_code, major_name, study_mode, study_mode_label,
             start_year, end_year, skill, provider, major_info_json, exam_rules_json, data_source_json, fetched_at)
            VALUES (%s,%s,%s,%s,%s,%s,2024,2026,'lexuemiao','乐学猫',%s,%s,%s,%s)""",
            (code, school_name, major_code, major_name, study_mode, MODE_LABEL.get(study_mode),
             json.dumps({
                 "name": detail.get("name"),
                 "research_direction": detail.get("research_direction"),
                 "class_mode": detail.get("class_mode"),
                 "reexam_mode": detail.get("reexam_mode"),
                 "initial_retest_ratio": detail.get("initial_retest_ratio"),
                 "is_easy": detail.get("is_easy"),
                 "zone": detail.get("zone"),
                 "campus_logo": detail.get("_campus_logo"),
                 "campus_feature": detail.get("_campus_feature"),
             }, ensure_ascii=False),
             json.dumps({
                 "tuition": detail.get("tuition"),
                 "tuition_text": fmt_wan(detail.get("tuition")),
                 "study_years": detail.get("study_years"),
                 "planned_enroll_num": detail.get("planned_enroll_num"),
                 "initial_retest_ratio": detail.get("initial_retest_ratio"),
             }, ensure_ascii=False),
             json.dumps({
                 "source": "乐学猫", "url": "https://www.lexuemiao.com/", "fetched_at": datetime.date.today().isoformat(),
             }, ensure_ascii=False),
             datetime.datetime.now()))
        pack_id = cur.lastrowid
    return pack_id

def import_one(cur, detail, major_code, major_name, study_mode, seen):
    school_name = detail.get("school_name", "")
    code = find_school(cur, school_name)
    if not code:
        return "skip_nomatch"
    key = (code, major_code, study_mode)
    if key in seen:
        return "skip_dup"
    seen.add(key)

    pack_id = upsert_pack(cur, code, school_name, major_code, major_name, study_mode, detail)

    tuition_text = fmt_wan(detail.get("tuition"))
    duration_text = fmt_duration(detail.get("study_years"))
    plan_text = fmt_plan(detail.get("planned_enroll_num"))
    national = detail.get("national_scores") or {}
    score_line = detail.get("score_line") or {}
    admissions = detail.get("admissions") or []
    last_min = detail.get("last_year_min_score")

    # admissions 按年索引（乐学喵只给最新一届）
    adm_map = {}
    for a in admissions:
        y = a.get("year")
        if not y:
            continue
        desc = a.get("description", "")
        sdesc = a.get("score_desc", "")
        adm_map[int(y)] = {
            "reexam": first_int(re.search(r"进入复试(\d+)人", desc).group(1) if re.search(r"进入复试(\d+)人", desc) else None),
            "admit": first_int(re.search(r"录取(\d+)人", desc).group(1) if re.search(r"录取(\d+)人", desc) else None),
            "min": first_int(re.search(r"最低分(\d+)分", sdesc).group(1) if re.search(r"最低分(\d+)分", sdesc) else None),
            "max": first_int(re.search(r"最高分(\d+)分", sdesc).group(1) if re.search(r"最高分(\d+)分", sdesc) else None),
            "ranges": a.get("ranges") or [],
        }

    for year in [2024, 2025, 2026]:
        reexam_total, reexam_eng, reexam_com = parse_score_line(score_line.get(str(year)))
        nation_total, nation_eng, nation_com = parse_score_line(national.get(str(year)))
        adm = adm_map.get(year, {})
        min_s = adm.get("min")
        max_s = adm.get("max")
        reexam_count = adm.get("reexam")
        admit_count = adm.get("admit")
        # 去年最低分补到 2025（乐学喵 admissions 只给最新一届）
        if year == 2025 and min_s is None and last_min:
            min_s = last_min

        cur.execute("""INSERT INTO school_year_stats
            (pack_id, school_code, major_code, study_mode, year, status,
             tuition_text, plan_text, duration_text,
             program_source_url, program_source_name,
             reexam_min_score, min_score, max_score, reexam_count, admit_count,
             nation_a_total, nation_a_english, nation_a_comprehensive,
             admission_source_url, admission_source_name, data_confidence)
            VALUES (%s,%s,%s,%s,%s,'confirmed',%s,%s,%s,'https://www.lexuemiao.com/','乐学猫',
             %s,%s,%s,%s,%s,%s,%s,%s,'https://www.lexuemiao.com/','乐学猫','confirmed')
            ON DUPLICATE KEY UPDATE
             pack_id=VALUES(pack_id), status='confirmed',
             tuition_text=VALUES(tuition_text), plan_text=VALUES(plan_text), duration_text=VALUES(duration_text),
             program_source_url=VALUES(program_source_url), program_source_name=VALUES(program_source_name),
             reexam_min_score=VALUES(reexam_min_score), min_score=VALUES(min_score),
             max_score=VALUES(max_score), reexam_count=VALUES(reexam_count), admit_count=VALUES(admit_count),
             nation_a_total=VALUES(nation_a_total), nation_a_english=VALUES(nation_a_english),
             nation_a_comprehensive=VALUES(nation_a_comprehensive),
             admission_source_url=VALUES(admission_source_url), admission_source_name=VALUES(admission_source_name),
             data_confidence='confirmed'""",
            (pack_id, code, major_code, study_mode, year,
             tuition_text, plan_text, duration_text,
             reexam_total, min_s, max_s, reexam_count, admit_count,
             nation_total, nation_eng, nation_com))

        # admission_stats（任一分数据）
        if reexam_total is not None or min_s is not None or admit_count is not None:
            cur.execute("""INSERT INTO admission_stats
                (school_code, year, major_code, study_mode, reexam_min_score, min_score, max_score,
                 reexam_count, admit_count, source_url, source_name, provider)
                VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'https://www.lexuemiao.com/','乐学猫','crawler')
                ON DUPLICATE KEY UPDATE
                 reexam_min_score=VALUES(reexam_min_score), min_score=VALUES(min_score),
                 max_score=VALUES(max_score), reexam_count=VALUES(reexam_count), admit_count=VALUES(admit_count),
                 source_name='乐学猫'""",
                (code, year, major_code, study_mode, reexam_total, min_s, max_s, reexam_count, admit_count))
            cur.execute("SELECT id FROM admission_stats WHERE school_code=%s AND year=%s AND major_code=%s AND study_mode=%s",
                        (code, year, major_code, study_mode))
            row = cur.fetchone()
            if row:
                adm_id = row[0]
                cur.execute("DELETE FROM admission_score_bands WHERE admission_id=%s", (adm_id,))
                for band in adm.get("ranges") or []:
                    label = band.get("score_range")
                    m = re.search(r"(\d+)\s*[—\-~]\s*(\d+)", str(label))
                    bmin, bmax = (int(m.group(1)), int(m.group(2))) if m else (None, None)
                    cur.execute("""INSERT INTO admission_score_bands
                        (admission_id, score_label, score_min, score_max, reexam_count, admit_count, sort_order)
                        VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                        (adm_id, label, bmin, bmax,
                         first_int(band.get("retest_num")), first_int(band.get("admission_num")),
                         band.get("sort") or 0))

        # school_programs（三年培养信息）
        cur.execute("""INSERT INTO school_programs
            (school_code, year, major_code, major_name, study_mode, study_mode_label,
             tuition_text, tuition_total, plan_text, duration_text, source_url, source_name, provider, checked_at)
            VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'https://www.lexuemiao.com/','乐学猫','crawler',%s)
            ON DUPLICATE KEY UPDATE
             major_name=VALUES(major_name), study_mode_label=VALUES(study_mode_label),
             tuition_text=VALUES(tuition_text), tuition_total=VALUES(tuition_total),
             plan_text=VALUES(plan_text), duration_text=VALUES(duration_text),
             source_name='乐学猫', provider='crawler', checked_at=VALUES(checked_at)""",
            (code, year, major_code, major_name or detail.get("major"), study_mode,
             MODE_LABEL.get(study_mode), tuition_text,
             round(float(detail.get("tuition")) / 10000.0, 2) if detail.get("tuition") else None,
             plan_text, duration_text, datetime.date.today()))
    return "ok"

def clear_old(cur):
    placeholders = ",".join(["%s"] * len(MAJOR_CODES))
    cur.execute(f"DELETE FROM school_year_stats WHERE major_code IN ({placeholders})", MAJOR_CODES)
    y = cur.rowcount
    cur.execute(f"DELETE FROM admission_score_bands WHERE admission_id IN (SELECT id FROM admission_stats WHERE major_code IN ({placeholders}))", MAJOR_CODES)
    b = cur.rowcount
    cur.execute(f"DELETE FROM admission_stats WHERE major_code IN ({placeholders})", MAJOR_CODES)
    a = cur.rowcount
    cur.execute(f"DELETE FROM school_data_packs WHERE major_code IN ({placeholders})", MAJOR_CODES)
    p = cur.rowcount
    cur.execute(f"DELETE FROM school_programs WHERE major_code IN ({placeholders})", MAJOR_CODES)
    pr = cur.rowcount
    print(f"清库：year_stats={y} bands={b} admission_stats={a} packs={p} programs={pr}")

def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--clear-only":
        c = connect()
        cur = c.cursor()
        clear_old(cur)
        c.commit()
        c.close()
        return
    c = connect()
    cur = c.cursor()
    print("=== 清库 ===")
    clear_old(cur)
    c.commit()

    seen = set()
    stats = {}
    for major_id, major_code, major_name, studymode, mode_name in COMBOS:
        dump_file = os.path.join(DUMP_DIR, f"{major_id}_{studymode}.json")
        if not os.path.exists(dump_file):
            print(f"!! 缺少 {dump_file}，跳过 {major_name} {mode_name}")
            continue
        with open(dump_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        details = data.get("details") or []
        ok = skip_nomatch = skip_dup = 0
        nomatch_list = []
        for d in details:
            r = import_one(cur, d, major_code, major_name, mode_name, seen)
            if r == "ok":
                ok += 1
            elif r == "skip_nomatch":
                skip_nomatch += 1
                nomatch_list.append(d.get("school_name", ""))
            else:
                skip_dup += 1
        c.commit()
        key = f"{major_name}({major_code}){mode_name}"
        stats[key] = {"ok": ok, "nomatch": skip_nomatch, "dup": skip_dup}
        print(f"{major_name} {mode_name}: 入库{ok} 未匹配{skip_nomatch} 重复跳过{skip_dup}")
        if nomatch_list:
            print(f"  未匹配: {', '.join(nomatch_list)}")

    print("\n=== 汇总 ===")
    for k, v in stats.items():
        print(f"  {k}: {v['ok']}所")

    cur.execute("""SELECT major_code, study_mode, COUNT(DISTINCT school_code), COUNT(*)
                   FROM school_year_stats GROUP BY major_code, study_mode ORDER BY major_code, study_mode""")
    print("\n=== school_year_stats 分布 ===")
    for row in cur.fetchall():
        print(f"  {row[0]} {row[1]}: {row[2]}所学校 {row[3]}行")
    cur.execute("SELECT COUNT(*), SUM(reexam_min_score IS NOT NULL), SUM(min_score IS NOT NULL), SUM(admit_count IS NOT NULL) FROM school_year_stats")
    n, re, mn, ad = cur.fetchone()
    print(f"\n总行数={n} 有复试线={re} 有最低分={mn} 有录取数={ad}")
    cur.execute("SELECT COUNT(*) FROM school_year_stats WHERE admission_source_name IS NULL OR admission_source_name=''")
    print(f"来源为空的残留行={cur.fetchone()[0]}（应为0）")
    c.close()
    print("\n完成")

if __name__ == "__main__":
    main()
