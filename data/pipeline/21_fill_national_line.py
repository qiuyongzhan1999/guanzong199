# -*- coding: utf-8 -*-
"""21_fill_national_line.py：近5年内 reexam_min_score 为空的行，按国家线补"""
import os, pymysql, datetime

B_PROV = {"内蒙古","广西","海南","贵州","云南","西藏","甘肃","青海","宁夏","新疆"}
now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

c = pymysql.connect(host="127.0.0.1", user="root",
                    password=os.environ["DB_PASSWORD"], database="gz199")
cur = c.cursor()

# 1. 国家线字典：(year, family) -> (a_total,a_eng,a_comp,b_total,b_eng,b_comp)
cur.execute("SELECT year, family, a_total,a_english,a_comprehensive, b_total,b_english,b_comprehensive FROM nation_lines")
nation = {}
for r in cur.fetchall():
    nation[(r[0], str(r[1]))] = r[1:]

# 2. schools code -> province
cur.execute("SELECT code, province FROM schools")
prov = {r[0]: (r[1] or "") for r in cur.fetchall()}

def family_of(major):
    # MEM 125600 走 1256 国家线（表里 family=125600）
    return major

# 3. 找所有 year 2022-2026 且 reexam_min_score IS NULL 的行
cur.execute("""SELECT id, school_code, major_code, year FROM school_year_stats
               WHERE year BETWEEN 2022 AND 2026 AND reexam_min_score IS NULL""")
rows = cur.fetchall()
print(f"待补分数线行数: {len(rows)}")

filled_a = filled_b = skip = 0
for row_id, school_code, major_code, year in rows:
    fam = family_of(major_code)
    nl = nation.get((year, fam))
    if not nl:
        # MEM 方向码 125601-04 也走 125600 国家线
        if major_code.startswith("1256"):
            nl = nation.get((year, "125600"))
    if not nl:
        skip += 1; continue
    # nl = (family, a_total,a_eng,a_comp,b_total,b_eng,b_comp)
    a_total,a_eng,a_comp,b_total,b_eng,b_comp = nl[1:]
    p = prov.get(school_code, "")
    is_b = any(p.startswith(x) or x in p for x in B_PROV)
    score = b_total if is_b else a_total
    eng = b_eng if is_b else a_eng
    comp = b_comp if is_b else a_comp
    cur.execute("""UPDATE school_year_stats SET
        reexam_min_score=%s, nation_a_total=%s, nation_a_english=%s, nation_a_comprehensive=%s,
        nation_b_total=%s, nation_b_english=%s, nation_b_comprehensive=%s,
        nation_source_url=%s, nation_source_name=%s, data_confidence=%s
        WHERE id=%s""",
        (score, a_total,a_eng,a_comp, b_total,b_eng,b_comp,
         "https://yz.chsi.com.cn/kyzx/kp/", "教育部国家线", "national_line_fallback", row_id))
    if is_b: filled_b += 1
    else: filled_a += 1

# admission_stats 也同步补（它也有 reexam_min_score）
cur.execute("""UPDATE admission_stats SET reexam_min_score=
  (SELECT a_total FROM nation_lines WHERE nation_lines.year=admission_stats.year
   AND (nation_lines.family=admission_stats.major_code OR
        (admission_stats.major_code LIKE '1256%' AND nation_lines.family='125600')))
  WHERE year BETWEEN 2022 AND 2026 AND reexam_min_score IS NULL""")
adm_filled = cur.rowcount

c.commit()
print(f"A区补: {filled_a}  B区补: {filled_b}  跳过: {skip}")
print(f"admission_stats 同步补: {adm_filled}")

# 复核
cur.execute("""SELECT major_code, year, COUNT(*) total,
  SUM(reexam_min_score IS NOT NULL) have FROM school_year_stats
  WHERE year BETWEEN 2022 AND 2026 GROUP BY major_code, year ORDER BY major_code, year DESC""")
names={"125100":"MBA","125200":"MPA","125300":"MPAcc","125400":"MTA","125500":"MLis","125600":"MEM","125700":"MAud"}
print(f"\n{'专业':6} {'年':6} {'总行':6} {'有分':6}")
for r in cur.fetchall():
    print(f"{names.get(r[0],r[0]):6} {r[1]:6} {r[2]:6} {r[3]:6}")
c.close()
