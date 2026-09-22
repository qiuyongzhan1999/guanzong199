# -*- coding: utf-8 -*-
"""53_import_lexuemiao.py：乐学猫数据入库"""
import json, re, os, pymysql

# 读详情
with open("lexuemiao_details.json","r",encoding="utf-8") as f:
    details=json.load(f)

print(f"共{len(details)}所学校详情")

# 连数据库
c=pymysql.connect(host="127.0.0.1",user="root",password=os.environ["DB_PASSWORD"],database="gz199")
cur=c.cursor()

# 解析函数
def parse_score_line(s):
    if not s: return (None,None,None)
    parts=re.split(r"[/／]", str(s))
    try:
        return (int(parts[0]), int(parts[1]), int(parts[2]))
    except:
        return (None,None,None)

imported=0
skipped=0

for d in details:
    school_name=d.get('school_name','')
    if not school_name: continue

    # 找学校code
    cur.execute("SELECT code FROM schools WHERE name=%s",(school_name,))
    row=cur.fetchone()
    if not row:
        # 模糊匹配
        cur.execute("SELECT code FROM schools WHERE name LIKE %s",(f"%{school_name}%",))
        row=cur.fetchone()
    if not row:
        skipped+=1
        continue
    code=row[0]

    # 找或建pack
    cur.execute("SELECT id FROM school_data_packs WHERE school_code=%s AND major_code='125300' AND study_mode='fulltime'",(code,))
    pack=cur.fetchone()
    if pack:
        pack_id=pack[0]
    else:
        cur.execute("""INSERT INTO school_data_packs (school_code,major_code,study_mode,school_name,start_year,end_year)
                      VALUES(%s,'125300','fulltime',%s,2024,2026)""",(code,school_name))
        pack_id=cur.lastrowid

    # 解析国家线和院校线
    national=d.get('national_scores',{})
    score_line=d.get('score_line',{})

    # 解析admissions
    admissions=d.get('admissions',[])
    adm_map={}
    for a in admissions:
        year=a['year']
        desc=a.get('description','')
        score_desc=a.get('score_desc','')
        m=re.search(r'进入复试(\d+)人', desc)
        reexam=int(m.group(1)) if m else None
        m=re.search(r'录取(\d+)人', desc)
        admit=int(m.group(1)) if m else None
        m=re.search(r'最低分(\d+)分', score_desc)
        min_s=int(m.group(1)) if m else None
        m=re.search(r'最高分(\d+)分', score_desc)
        max_s=int(m.group(1)) if m else None
        adm_map[year]={'reexam':reexam,'admit':admit,'min':min_s,'max':max_s}

    # 写入2024-2026
    for year in [2024,2025,2026]:
        # 院校线
        reexam_total,eng,com = parse_score_line(score_line.get(str(year)))
        # 国家线
        nation_total,nation_eng,nation_com = parse_score_line(national.get(str(year)))
        # 录取数据
        adm=adm_map.get(year,{})
        min_s=adm.get('min')
        max_s=adm.get('max')
        reexam_count=adm.get('reexam')
        admit_count=adm.get('admit')

        cur.execute("""INSERT INTO school_year_stats
          (pack_id,school_code,major_code,study_mode,year,
           reexam_min_score,min_score,max_score,reexam_count,admit_count,
           data_confidence,admission_source_url,admission_source_name,
           nation_a_total,nation_a_english,nation_a_comprehensive)
          VALUES(%s,%s,'125300','fulltime',%s,
           %s,%s,%s,%s,%s,
           'confirmed','https://www.lexuemiao.com/','乐学猫',
           %s,%s,%s)
          ON DUPLICATE KEY UPDATE
           reexam_min_score=VALUES(reexam_min_score),
           min_score=VALUES(min_score),
           max_score=VALUES(max_score),
           reexam_count=VALUES(reexam_count),
           admit_count=VALUES(admit_count),
           data_confidence='confirmed'""",
          (pack_id,code,year,reexam_total,min_s,max_s,reexam_count,admit_count,
           nation_total,nation_eng,nation_com))

        # admission_stats
        if min_s or admit_count:
            cur.execute("""INSERT INTO admission_stats
              (school_code,year,major_code,study_mode,reexam_min_score,min_score,max_score,
               reexam_count,admit_count,source_url,source_name,provider)
              VALUES(%s,%s,'125300','fulltime',%s,%s,%s,%s,%s,
               'https://www.lexuemiao.com/','乐学猫','crawler')
              ON DUPLICATE KEY UPDATE
               reexam_min_score=VALUES(reexam_min_score),
               min_score=VALUES(min_score),
               max_score=VALUES(max_score),
               reexam_count=VALUES(reexam_count),
               admit_count=VALUES(admit_count)""",
              (code,year,reexam_total,min_s,max_s,reexam_count,admit_count))

    imported+=1

c.commit()
print(f"\n入库完成: {imported}所成功, {skipped}所跳过(没匹配到学校)")

# 复核
cur.execute("""SELECT COUNT(*) FROM school_year_stats WHERE major_code='125300' AND data_confidence='confirmed'""")
print(f"\nMPAcc真实数据总行数: {cur.fetchone()[0]}")
c.close()
