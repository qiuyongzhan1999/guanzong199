# -*- coding: utf-8 -*-
"""59_crawl_mba.py：批量爬乐学猫MBA工商管理"""
import urllib.request, json, time, os, pymysql, re

TOKEN="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vd3d3LmxleHVlbWlhby5jb20vYXBpL2FwcC93ZUNoYXQveGN4TG9naW4iLCJpYXQiOjE3OTAwMzYzODksImV4cCI6MTc5MjYyODM4OSwibmJmIjoxNzkwMDM2Mzg5LCJqdGkiOiJtRFNKbHFqeGVEZGlIQUQwIiwic3ViIjo4MjU2OTcsInBydiI6IjlmMWZlOWUwZGZmYmU0NDQyZGM3ODMxMDc1MWY1OTFjZjRkMTQwMjAiLCJyb2xlIjoidXNlciJ9.A0JNya-hHOKJnsyvjbMMsAanDzUmIp5kwC5ng71ah9A"

HEADERS={
    "Host": "www.lexuemiao.com",
    "appid": "wx5d228fe6009c0510",
    "authorization": f"Bearer {TOKEN}",
    "xweb_xhr": "1",
    "wechatid": "14",
    "devicetype": "XCX",
    "deviceid": "6728AA6F-40D7-5DDD-5E96-E5FC62F4277A",
    "studymode": "1",
    "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "content-type": "application/json",
    "majorid": "31",
    "accept": "*/*",
    "referer": "https://servicewechat.com/wx5d228fe6009c0510/3/page-frame.html",
}

def fetch_list(page, limit=50, major_id=31):
    ts=int(time.time()*1000)
    url=f"https://www.lexuemiao.com/api/app/campus/list?page={page}&limit={limit}&major_id={major_id}&ts={ts}&AppID=wx5d228fe6009c0510&wechatId=14"
    req=urllib.request.Request(url,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))

def fetch_detail(college_id, major_id=31):
    ts=int(time.time()*1000)
    url=f"https://www.lexuemiao.com/api/app/campus/college-detail?college_id={college_id}&major_id={major_id}&ts={ts}&AppID=wx5d228fe6009c0510&wechatId=14"
    req=urllib.request.Request(url,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))

# 1. 爬学校列表
print("=== 爬MBA学校列表 ===")
all_schools=[]
page=1
while True:
    data=fetch_list(page, limit=50)
    if data.get('code')!=200: break
    schools=data['data']['list']
    all_schools.extend(schools)
    print(f"  第{page}页: {len(schools)}所")
    if len(all_schools) >= data['data']['total']: break
    page+=1
    time.sleep(0.5)

print(f"\n共{len(all_schools)}所MBA学校")

# 2. 爬详情
print("\n=== 爬详情 ===")
all_details=[]
for i, s in enumerate(all_schools):
    college_id=s['colleges'][0]['id'] if s.get('colleges') else None
    if not college_id: continue
    try:
        data=fetch_detail(college_id)
        if data.get('code')==200:
            all_details.append(data['data'])
            if (i+1)%20==0:
                print(f"  已爬{i+1}/{len(all_schools)}")
    except Exception as e:
        print(f"  [{i+1}] {s['name']}: {e}")
    time.sleep(0.3)

print(f"\n爬完！共{len(all_details)}所详情")

# 3. 入库
print("\n=== 入库 ===")
c=pymysql.connect(host="127.0.0.1",user="root",password="qyz123456",database="gz199")
cur=c.cursor()

def parse_score_line(s):
    if not s: return (None,None,None)
    parts=re.split(r"[/／]", str(s))
    try:
        return (int(parts[0]), int(parts[1]), int(parts[2]))
    except:
        return (None,None,None)

imported=0
skipped=0

for d in all_details:
    school_name=d.get('school_name','')
    if not school_name: continue
    cur.execute("SELECT code FROM schools WHERE name=%s",(school_name,))
    row=cur.fetchone()
    if not row:
        cur.execute("SELECT code FROM schools WHERE name LIKE %s",(f"%{school_name}%",))
        row=cur.fetchone()
    if not row:
        skipped+=1
        continue
    code=row[0]

    cur.execute("SELECT id FROM school_data_packs WHERE school_code=%s AND major_code='125100' AND study_mode='fulltime'",(code,))
    pack=cur.fetchone()
    if pack:
        pack_id=pack[0]
    else:
        cur.execute("""INSERT INTO school_data_packs (school_code,major_code,study_mode,school_name,start_year,end_year)
                      VALUES(%s,'125100','fulltime',%s,2024,2026)""",(code,school_name))
        pack_id=cur.lastrowid

    national=d.get('national_scores',{})
    score_line=d.get('score_line',{})
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

    for year in [2024,2025,2026]:
        reexam_total,eng,com = parse_score_line(score_line.get(str(year)))
        nation_total,nation_eng,nation_com = parse_score_line(national.get(str(year)))
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
          VALUES(%s,%s,'125100','fulltime',%s,
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

        if min_s or admit_count:
            cur.execute("""INSERT INTO admission_stats
              (school_code,year,major_code,study_mode,reexam_min_score,min_score,max_score,
               reexam_count,admit_count,source_url,source_name,provider)
              VALUES(%s,%s,'125100','fulltime',%s,%s,%s,%s,%s,
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
print(f"\n入库完成: {imported}所成功, {skipped}所跳过")

cur.execute("SELECT COUNT(*) FROM school_year_stats WHERE major_code='125100' AND data_confidence='confirmed'")
print(f"MBA真实数据: {cur.fetchone()[0]}条")
c.close()
