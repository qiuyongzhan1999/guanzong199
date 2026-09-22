# -*- coding: utf-8 -*-
"""22_fill_2026_rows.py：为每个已有pack补2026年行（若无），分数=国家线兜底"""
import os, pymysql, datetime
B_PROV = {"内蒙古","广西","海南","贵州","云南","西藏","甘肃","青海","宁夏","新疆"}
now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
c = pymysql.connect(host="127.0.0.1", user="root",
                    password=os.environ["DB_PASSWORD"], database="gz199")
cur = c.cursor()

cur.execute("SELECT year, family, a_total,a_english,a_comprehensive, b_total,b_english,b_comprehensive FROM nation_lines")
nation={}
for r in cur.fetchall():
    nation[(r[0], str(r[1]))] = (r[2],r[3],r[4],r[5],r[6],r[7])

cur.execute("SELECT code, province FROM schools")
prov={r[0]:(r[1] or "") for r in cur.fetchall()}

# 所有pack
cur.execute("SELECT id, school_code, major_code, study_mode, school_name FROM school_data_packs")
packs=cur.fetchall()
print(f"pack总数: {len(packs)}")
inserted=0
for pack_id, school_code, major_code, study_mode, school_name in packs:
    cur.execute("SELECT 1 FROM school_year_stats WHERE pack_id=%s AND year=2026",(pack_id,))
    if cur.fetchone(): continue
    fam = "125600" if major_code.startswith("1256") else major_code
    nl = nation.get((2026, fam))
    if not nl: continue
    a_total,a_eng,a_comp,b_total,b_eng,b_comp = nl
    p=prov.get(school_code,"")
    is_b = any(x in p for x in B_PROV)
    score = b_total if is_b else a_total
    eng = b_eng if is_b else a_eng
    comp = b_comp if is_b else a_comp
    cur.execute("""INSERT INTO school_year_stats
        (pack_id,school_code,major_code,study_mode,year,
         reexam_min_score,nation_a_total,nation_a_english,nation_a_comprehensive,
         nation_b_total,nation_b_english,nation_b_comprehensive,
         nation_source_url,nation_source_name,data_confidence)
        VALUES(%s,%s,%s,%s,2026,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
        (pack_id,school_code,major_code,study_mode,score,
         a_total,a_eng,a_comp,b_total,b_eng,b_comp,
         "https://yz.chsi.com.cn/kyzx/kp/","教育部国家线","national_line_fallback"))
    inserted+=1
    # admission_stats 也补
    cur.execute("""INSERT IGNORE INTO admission_stats
        (school_code,year,major_code,study_mode,reexam_min_score,source_url,source_name,provider,synced_at)
        VALUES(%s,2026,%s,%s,%s,%s,%s,%s,%s)""",
        (school_code,major_code,study_mode,score,
         "https://yz.chsi.com.cn/kyzx/kp/","教育部国家线","national_fallback",now))
c.commit()
print(f"补2026行: {inserted}")
# 复核2026
cur.execute("""SELECT major_code, COUNT(*) FROM school_year_stats
   WHERE year=2026 GROUP BY major_code ORDER BY major_code""")
names={"125100":"MBA","125200":"MPA","125300":"MPAcc","125400":"MTA","125500":"MLis","125600":"MEM","125700":"MAud"}
for r in cur.fetchall(): print(f"  2026 {names.get(r[0],r[0])}: {r[1]}行")
c.close()
