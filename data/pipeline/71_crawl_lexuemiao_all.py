# -*- coding: utf-8 -*-
"""71_crawl_lexuemiao_all.py：全量爬乐学猫（8专业 × 全日制/非全），先存 JSON 不碰库
输出：data/pipeline/lexuemiao_dump/<major_id>_<studymode>.json（每组合一个文件，含列表+详情）
支持断点续爬：已存在的 dump 文件自动跳过。
"""
import urllib.request, json, time, os, sys

TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vd3d3LmxleHVlbWlhby5jb20vYXBpL2FwcC93ZUNoYXQveGN4TG9naW4iLCJpYXQiOjE3OTAwMzYzODksImV4cCI6MTc5MjYyODM4OSwibmJmIjoxNzkwMDM2Mzg5LCJqdGkiOiJtRFNKbHFqeGVEZGlIQUQwIiwic3ViIjo4MjU2OTcsInBydiI6IjlmMWZlOWUwZGZmYmU0NDQyZGM3ODMxMDc1MWY1OTFjZjRkMTQwMjAiLCJyb2xlIjoidXNlciJ9.A0JNya-hHOKJnsyvjbMMsAanDzUmIp5kwC5ng71ah9A"

MAJORS = [
    (28, "125300", "MPAcc会计"),
    (29, "125700", "MAud审计"),
    (30, "125500", "MLis图书情报"),
    (31, "125100", "MBA工商管理"),
    (32, "125200", "MPA公共管理"),
    (33, "125600", "MEM工程管理"),
    (34, "125600", "项目管理MEM"),
    (35, "125400", "MTA旅游管理"),
]
MODES = [(1, "fulltime"), (2, "parttime")]
DUMP_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "lexuemiao_dump")
SLEEP = 0.25

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

def get_json(path, params, headers, tries=3):
    qs = "&".join(k + "=" + str(v) for k, v in params.items())
    url = "https://www.lexuemiao.com/api/app/campus/" + path + "?" + qs
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=15) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            if attempt == tries - 1:
                raise
            time.sleep(2 + attempt)
    return None

def crawl_combination(major_id, major_name, studymode, mode_name):
    dump_file = os.path.join(DUMP_DIR, f"{major_id}_{studymode}.json")
    if os.path.exists(dump_file):
        with open(dump_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        print(f"  [跳过] {major_name} {mode_name} 已有 {data.get('total', 0)} 所")
        return data

    headers = make_headers(major_id, studymode)
    # 1) 列表
    schools = []
    page = 1
    total = None
    while True:
        data = get_json("list", {"page": page, "limit": 50, "major_id": major_id,
                                 "ts": int(time.time() * 1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, headers)
        if not data or data.get("code") != 200:
            print(f"  列表第{page}页失败，停止")
            break
        body = data.get("data") or {}
        total = body.get("total") or 0
        lst = body.get("list") or []
        schools.extend(lst)
        if len(schools) >= total:
            break
        page += 1
        time.sleep(SLEEP)

    # 2) 详情
    details = []
    for i, s in enumerate(schools):
        colleges = s.get("colleges") or []
        college_id = colleges[0].get("id") if colleges else None
        name = s.get("name", "")
        if not college_id:
            continue
        try:
            det = get_json("college-detail", {"college_id": college_id, "major_id": major_id,
                                              "ts": int(time.time() * 1000), "AppID": "wx5d228fe6009c0510", "wechatId": 14}, headers)
            if det and det.get("code") == 200:
                d = det.get("data") or {}
                d["_campus_id"] = s.get("id")
                d["_campus_logo"] = s.get("logo")
                d["_campus_feature"] = s.get("feature")
                details.append(d)
            else:
                print(f"  [{i+1}] {name}: code={det.get('code') if det else 'None'}")
        except Exception as e:
            print(f"  [{i+1}] {name}: {e}")
        time.sleep(SLEEP)

    result = {"major_id": major_id, "major_name": major_name, "studymode": studymode,
              "mode_name": mode_name, "total": len(details), "schools": schools, "details": details}
    with open(dump_file, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False)
    print(f"  {major_name} {mode_name}: 列表{len(schools)}所 -> 详情{len(details)}所")
    return result

def main():
    os.makedirs(DUMP_DIR, exist_ok=True)
    only = sys.argv[1] if len(sys.argv) > 1 else None  # 可选：只爬某个组合，如 28_1
    grand = 0
    for major_id, major_code, major_name in MAJORS:
        for studymode, mode_name in MODES:
            if only and f"{major_id}_{studymode}" != only:
                continue
            print(f"=== {major_name}（major_id={major_id}）{mode_name} ===")
            try:
                r = crawl_combination(major_id, major_name, studymode, mode_name)
                grand += r.get("total", 0)
            except Exception as e:
                print(f"  !! 组合 {major_id}_{studymode} 失败: {e}")
    print(f"\n全部完成，共 {grand} 条详情，位于 {DUMP_DIR}")

if __name__ == "__main__":
    main()
