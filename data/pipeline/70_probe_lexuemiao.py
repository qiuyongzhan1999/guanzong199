# -*- coding: utf-8 -*-
"""70_probe_lexuemiao.py：探测乐学猫接口真实返回结构（列表 + 详情，全日制/非全）"""
import urllib.request, json, time

TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vd3d3LmxleHVlbWlhby5jb20vYXBpL2FwcC93ZUNoYXQveGN4TG9naW4iLCJpYXQiOjE3OTAwMzYzODksImV4cCI6MTc5MjYyODM4OSwibmJmIjoxNzkwMDM2Mzg5LCJqdGkiOiJtRFNKbHFqeGVEZGlIQUQwIiwic3ViIjo4MjU2OTcsInBydiI6IjlmMWZlOWUwZGZmYmU0NDQyZGM3ODMxMDc1MWY1OTFjZjRkMTQwMjAiLCJyb2xlIjoidXNlciJ9.A0JNya-hHOKJnsyvjbMMsAanDzUmIp5kwC5ng71ah9A"

def make_headers(major_id, studymode):
    return {
        "Host": "www.lexuemiao.com",
        "appid": "wx5d228fe6009c0510",
        "authorization": "Bearer " + TOKEN,
        "xweb_xhr": "1",
        "wechatid": "14",
        "devicetype": "XCX",
        "deviceid": "6728AA6F-40D7-5DDD-5E96-E5FC62F4277A",
        "studymode": str(studymode),
        "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "content-type": "application/json",
        "majorid": str(major_id),
        "accept": "*/*",
        "referer": "https://servicewechat.com/wx5d228fe6009c0510/3/page-frame.html",
    }

def get_json(path, params, headers):
    qs = "&".join(k + "=" + str(v) for k, v in params.items())
    url = "https://www.lexuemiao.com/api/app/campus/" + path + "?" + qs
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=12) as r:
        return json.loads(r.read().decode("utf-8"))

def dump(title, obj):
    print("\n==========", title, "==========")
    print(json.dumps(obj, ensure_ascii=False, indent=1)[:4000])

# 1) 全日制 MPAcc 列表（第一页）
d1 = get_json("list", {"page": 1, "limit": 2, "major_id": 28, "ts": int(time.time()*1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, make_headers(28, 1))
dump("全日制 MPAcc 列表 page1", d1)

# 2) 非全日制 MPAcc 列表（第一页）
d2 = get_json("list", {"page": 1, "limit": 2, "major_id": 28, "ts": int(time.time()*1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, make_headers(28, 2))
dump("非全日制 MPAcc 列表 page1", d2)

# 3) 找一个 college_id 分别用 fulltime/parttime 爬详情
def first_college_id(data):
    lst = (data.get("data") or {}).get("list") or []
    for s in lst:
        colleges = s.get("colleges") or []
        if colleges:
            return colleges[0].get("id"), s.get("name")
    return None, None

cid1, name1 = first_college_id(d1)
cid2, name2 = first_college_id(d2)
print("\n列表1学校:", name1, "college_id:", cid1)
print("列表2学校:", name2, "college_id:", cid2)

if cid1:
    det = get_json("college-detail", {"college_id": cid1, "major_id": 28, "ts": int(time.time()*1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, make_headers(28, 1))
    dump("全日制详情（college_id=%s）" % cid1, det)
if cid2:
    det2 = get_json("college-detail", {"college_id": cid2, "major_id": 28, "ts": int(time.time()*1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, make_headers(28, 2))
    dump("非全日制详情（college_id=%s）" % cid2, det2)
print("\n探测完成")
