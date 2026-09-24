# -*- coding: utf-8 -*-
"""全量回归测试：16 组列表 + 详情抽查 + 数据一致性"""
import urllib.request, urllib.parse, json, pymysql, sys

BASE = "http://localhost:8080"

def get(path, params):
    qs = urllib.parse.urlencode(params)
    with urllib.request.urlopen(BASE + path + "?" + qs, timeout=25) as r:
        return json.loads(r.read().decode("utf-8"))

MAJORS = {
    "125300": "会计MPAcc", "125700": "审计MAud", "125500": "图书情报MLis", "125100": "工商管理MBA",
    "125200": "公共管理MPA", "125600": "工程管理MEM", "125400": "旅游管理MTA",
}
STUDY = {"fulltime": "全日制", "parttime": "非全日制"}

problems = []
def chk(cond, msg):
    tag = "OK " if cond else "FAIL"
    print(f"  [{tag}] {msg}")
    if not cond:
        problems.append(msg)

print("========== A. 16 组列表接口 ==========")
totals = {}
for major, mname in MAJORS.items():
    for sm, smname in STUDY.items():
        d = get("/api/schools", {"majorCode": major, "studyMode": sm, "trait": "all", "page": 1, "pageSize": 50})
        totals[(major, sm)] = d.get("total", 0)
        print(f"  {mname}/{smname}: total={d.get('total')} items={len(d.get('items') or [])}")

print("\n========== B. 库一致性：列表出现的学校是否真有该专业 ==========")
c = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456", database="gz199", charset="utf8mb4")
cur = c.cursor()
for (major, sm), total in totals.items():
    if total == 0:
        chk(False, f"{MAJORS[major]}/{STUDY[sm]} 列表 total=0")
        continue
    pages = (total + 49) // 50
    codes = set()
    for pg in range(1, min(pages, 3) + 1):
        d = get("/api/schools", {"majorCode": major, "studyMode": sm, "trait": "all", "page": pg, "pageSize": 50})
        for it in (d.get("items") or []):
            if it.get("code"): codes.add(it["code"])
    if not codes:
        chk(False, f"{MAJORS[major]}/{STUDY[sm]} 无学校代码")
        continue
    placeholders = ",".join(["%s"] * len(codes))
    cur.execute(f"SELECT DISTINCT school_code FROM school_programs WHERE major_code=%s AND study_mode=%s AND school_code IN ({placeholders})", [major, sm] + list(codes))
    have = {r[0] for r in cur.fetchall()}
    missing = codes - have
    chk(not missing, f"{MAJORS[major]}/{STUDY[sm]}: 抽查{len(codes)}所，缺专业记录 {len(missing)} 所 {list(missing)[:5]}")

print("\n========== C. 详情接口抽查（专业/学制/数据） ==========")
samples = [
    ("10634", "125500", "fulltime", "川北医学院/图情/全日制"),
    ("10634", "125200", "fulltime", "川北医学院/MPA/全日制"),
    ("11415", "125300", "parttime", "中国地质大学(北京)/会计/非全"),
    ("10491", "125300", "parttime", "中国地质大学(武汉)/会计/非全"),
    ("10001", "125100", "fulltime", "北京大学/MBA/全日制"),
    ("10126", "125200", "fulltime", "内蒙古大学/MPA/全日制"),
]
for code, major, sm, label in samples:
    try:
        d = get("/api/school-detail", {"schoolCode": code, "year": "2026", "majorCode": major, "studyMode": sm})
        yd = d.get("yearlyData") or []
        years = [y.get("year") for y in yd]
        opts = [m.get("code") for m in (d.get("majorOptions") or [])]
        row = next((y for y in yd if y.get("year") == 2026), None)
        score = row.get("reexam_min_score") if row else None
        ok = major in opts and 2026 in years
        chk(ok, f"{label}: majorOptions含{major}={major in opts} yearly={years} 2026复试线={score}")
    except Exception as e:
        chk(False, f"{label}: 接口异常 {e}")

print("\n========== D. 数据覆盖：school_programs 三年 vs school_year_stats ==========")
cur.execute("""SELECT p.school_code, p.major_code, p.study_mode,
    COUNT(DISTINCT p.year) AS prog_years,
    (SELECT COUNT(*) FROM school_year_stats y WHERE y.school_code=p.school_code AND y.major_code=p.major_code AND y.study_mode=p.study_mode) AS stat_rows
    FROM school_programs p GROUP BY p.school_code, p.major_code, p.study_mode
    HAVING prog_years < 3 OR stat_rows < 3""")
rows = cur.fetchall()
print(f"  专业组合总数(去重): ", end="")
cur.execute("SELECT COUNT(*) FROM (SELECT DISTINCT school_code, major_code, study_mode FROM school_programs) t")
print(cur.fetchone()[0])
print(f"  三年不齐的专业组合: {len(rows)}")
for r in rows[:15]:
    print("    ", r)

print("\n========== E. school_year_stats 有但 school_programs 无（或反之） ==========")
cur.execute("""SELECT COUNT(*) FROM school_year_stats y LEFT JOIN school_programs p
    ON p.school_code=y.school_code AND p.major_code=y.major_code AND p.study_mode=y.study_mode AND p.year=y.year
    WHERE p.id IS NULL""")
print("  school_year_stats 无对应 program 行:", cur.fetchone()[0])
cur.execute("""SELECT COUNT(*) FROM school_programs p LEFT JOIN school_year_stats y
    ON p.school_code=y.school_code AND p.major_code=y.major_code AND p.study_mode=y.study_mode AND p.year=y.year
    WHERE y.id IS NULL""")
print("  school_programs 无对应 stats 行:", cur.fetchone()[0])

c.close()
print("\n========== 结果 ==========")
if problems:
    print(f"发现 {len(problems)} 个问题:")
    for p in problems:
        print("  -", p)
else:
    print("全部通过")
