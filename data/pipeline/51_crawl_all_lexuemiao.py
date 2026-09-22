# -*- coding: utf-8 -*-
"""51_crawl_all_lexuemiao.py：批量爬乐学猫所有MPAcc学校"""
import urllib.request, json, time, os, pymysql

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
    "majorid": "28",
    "accept": "*/*",
    "referer": "https://servicewechat.com/wx5d228fe6009c0510/3/page-frame.html",
}

def fetch_list(page, limit=50, major_id=28):
    ts=int(time.time()*1000)
    url=f"https://www.lexuemiao.com/api/app/campus/list?page={page}&limit={limit}&major_id={major_id}&ts={ts}&AppID=wx5d228fe6009c0510&wechatId=14"
    req=urllib.request.Request(url,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))

def fetch_detail(college_id, major_id=28):
    ts=int(time.time()*1000)
    url=f"https://www.lexuemiao.com/api/app/campus/college-detail?college_id={college_id}&major_id={major_id}&ts={ts}&AppID=wx5d228fe6009c0510&wechatId=14"
    req=urllib.request.Request(url,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=10) as r:
        return json.loads(r.read().decode("utf-8"))

# 1. 爬所有学校列表
print("=== 爬学校列表 ===")
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

print(f"\n共{len(all_schools)}所学校")

# 保存列表
with open("lexuemiao_schools.json","w",encoding="utf-8") as f:
    json.dump(all_schools, f, ensure_ascii=False, indent=2)
print("学校列表已保存到 lexuemiao_schools.json")
