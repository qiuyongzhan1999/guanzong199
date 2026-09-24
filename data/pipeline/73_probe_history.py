# -*- coding: utf-8 -*-
"""探测乐学喵是否有 2022/2023 历史数据：year 参数 / 其他端点 / PC 网页"""
import urllib.request, json, time, re

TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vd3d3LmxleHVlbWlhby5jb20vYXBpL2FwcC93ZUNoYXQveGN4TG9naW4iLCJpYXQiOjE3OTAwMzYzODksImV4cCI6MTc5MjYyODM4OSwibmJmIjoxNzkwMDM2Mzg5LCJqdGkiOiJtRFNKbHFqeGVEZGlIQUQwIiwic3ViIjo4MjU2OTcsInBydiI6IjlmMWZlOWUwZGZmYmU0NDQyZGM3ODMxMDc1MWY1OTFjZjRkMTQwMjAiLCJyb2xlIjoidXNlciJ9.A0JNya-hHOKJnsyvjbMMsAanDzUmIp5kwC5ng71ah9A"

def make_headers(major_id, studymode):
    return {
        "Host": "www.lexuemiao.com", "appid": "wx5d228fe6009c0510",
        "authorization": "Bearer " + TOKEN, "xweb_xhr": "1",
        "wechatid": "14", "devicetype": "XCX",
        "deviceid": "6728AA6F-40D7-5DDD-5E96-E5FC62F4277A",
        "studymode": str(studymode), "majorid": str(major_id),
        "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "content-type": "application/json", "accept": "*/*",
        "referer": "https://servicewechat.com/wx5d228fe6009c0510/3/page-frame.html",
    }

def get(url, headers, timeout=10):
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except Exception as e:
        return None, str(e)

# 找北京非全 MPAcc 的 college_id
d = json.load(open(r"D:\123\gz199\data\pipeline\lexuemiao_dump\28_2.json", encoding="utf-8"))
bj = next((x for x in d["details"] if "北京" in (x.get("school_name") or "")), None)
print("北京详情 school_name:", bj.get("school_name"), "id(college):", bj.get("id"), "campus_id:", bj.get("campus_id"))
cid = bj.get("id")

base = "https://www.lexuemiao.com/api/app/campus/"
h = make_headers(28, 2)

print("\n=== 1) college-detail 加 year/cohort 参数 ===")
for extra in [{"year": 2023}, {"year": "2023"}, {"cohort": 2023}, {"year": 2022}, {"exam_year": 2023}]:
    params = {"college_id": cid, "major_id": 28, "ts": int(time.time()*1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}
    params.update(extra)
    qs = "&".join(f"{k}={v}" for k, v in params.items())
    st, body = get(base + "college-detail?" + qs, h)
    snippet = body[:160].replace("\n", " ")
    print(f"  {extra}: status={st} body={snippet}")

print("\n=== 2) 常见历史/趋势端点 ===")
for path in ["score-line", "history", "score", "trend", "score-trend", "college-score",
             "college-detail/score", "admission", "college-admission", "college", "detail",
             "college-detail/history", "score-line/list", "college-score-line"]:
    st, body = get(f"{base}{path}?college_id={cid}&major_id=28&ts={int(time.time()*1000)}", h)
    if st == 200 and body and "404" not in body[:50]:
        print(f"  /{path}: status={st} body={body[:200]}")
    else:
        print(f"  /{path}: status={st}")

print("\n=== 3) 乐学喵 PC 网页详情页 ===")
for u in [f"https://www.lexuemiao.com/college/{cid}", f"https://www.lexuemiao.com/school/{cid}",
          f"https://www.lexuemiao.com/detail/{cid}", f"https://www.lexuemiao.com/",
          f"https://www.lexuemiao.com/campus/{cid}"]:
    st, body = get(u, {"user-agent": "Mozilla/5.0"})
    if st == 200:
        has_hist = ("2023" in body and "2022" in body)
        print(f"  {u}: status={st} len={len(body)} 含2022/2023={has_hist} title={re.search(r'<title>(.*?)</title>', body, re.S).group(1)[:60] if re.search(r'<title>(.*?)</title>', body, re.S) else '?'}")
    else:
        print(f"  {u}: status={st}")

print("\n探测完成")
