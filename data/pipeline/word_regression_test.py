# -*- coding: utf-8 -*-
"""背单词功能全流程回归测试：词库 / 发音 / 学习时长 / 组队打卡"""
import urllib.request, urllib.parse, json, time, io

BASE = "http://localhost:8080"
HDR = {"Content-Type": "application/json", "X-UserId": "test-user-a"}
HDR2 = {"Content-Type": "application/json", "X-UserId": "test-user-b"}

def get(path, params=None, headers=None):
    qs = ("?" + urllib.parse.urlencode(params)) if params else ""
    req = urllib.request.Request(BASE + path + qs, headers=headers or {})
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.status, json.loads(r.read().decode("utf-8"))

def post(path, data, headers):
    req = urllib.request.Request(BASE + path, data=json.dumps(data).encode("utf-8"),
                                 headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.status, json.loads(r.read().decode("utf-8"))

def raw(path, params):
    qs = urllib.parse.urlencode(params)
    req = urllib.request.Request(BASE + path + "?" + qs)
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.status, r.headers.get("Content-Type", ""), r.read()

ok = 0; fail = 0
def chk(cond, msg):
    global ok, fail
    print(f"  [{'OK ' if cond else 'FAIL'}] {msg}")
    if cond: ok += 1
    else: fail += 1

# 清理上次测试残留（测试用户 + 测试队伍）
import pymysql
_conn = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456", database="gz199", charset="utf8mb4")
_cur = _conn.cursor()
_cur.execute("DELETE FROM study_record WHERE user_id IN ('test-user-a','test-user-b')")
_cur.execute("DELETE FROM team_checkin WHERE user_id IN ('test-user-a','test-user-b')")
_cur.execute("DELETE FROM team_member WHERE user_id IN ('test-user-a','test-user-b')")
_cur.execute("DELETE FROM team WHERE creator_id IN ('test-user-a','test-user-b')")
_conn.commit(); _conn.close()

print("===== 1. 词库（ECDICT 分类词书） =====")
st, d = get("/api/word/books")
chk(st == 200 and isinstance(d, list) and len(d) == 7, f"books: {len(d) if isinstance(d, list) else '?'} 类词书")
book_map = {b.get("book"): b for b in d} if isinstance(d, list) else {}
chk(book_map.get("ky", {}).get("total", 0) > 4000, f"考研词书: {book_map.get('ky', {}).get('total')} 词")
st, d = get("/api/word/count", {"book": "ky"})
chk(st == 200 and d["total"] > 4000, f"count(ky): total={d.get('total')}")
st, d = get("/api/word/list", {"page": 1, "pageSize": 5, "book": "cet4"})
items = d.get("items", [])
chk(st == 200 and len(items) == 5 and items[0].get("word"), f"list(cet4): {len(items)} 条, 首词={items[0].get('word') if items else '?'}")
chk(bool(items[0].get("meaningCn")) and bool(items[0].get("phonetic")), f"字段完整: 释义/音标存在")

print("===== 2. 发音代理（有道） =====")
st, ctype, body = raw("/api/audio/word", {"word": "abandon", "type": 1})
chk(st == 200 and len(body) > 1000 and "audio" in ctype, f"英音: status={st} len={len(body)} type={ctype}")
st, ctype, body2 = raw("/api/audio/word", {"word": "abandon", "type": 0})
chk(st == 200 and len(body2) > 1000, f"美音: status={st} len={len(body2)}")

print("===== 3. 学习时长 =====")
st, d = post("/api/study/report", {"durationSec": 65, "wordsSeen": 10}, HDR)
chk(st == 200 and d.get("ok"), "上报 65s/10词")
st, d = post("/api/study/report", {"durationSec": 35, "wordsSeen": 5}, HDR)
chk(st == 200 and d.get("ok"), "上报 35s/5词（应累加）")
st, d = get("/api/study/today", headers=HDR)
chk(d.get("durationSec") == 100 and d.get("wordsSeen") == 15, f"今日累计: {d.get('durationSec')}s/{d.get('wordsSeen')}词")
st, d = get("/api/study/statistics", {"range": "total"}, HDR)
chk(d.get("todaySec") == 100 and d.get("totalSec") == 100, f"统计: today={d.get('todaySec')} total={d.get('totalSec')} days={d.get('studyDays')}")
# 另一用户隔离
st, d = get("/api/study/statistics", {"range": "total"}, HDR2)
chk(d.get("totalSec") == 0, f"用户隔离: B用户 total={d.get('totalSec')}（应为0）")

print("===== 4. 组队打卡 =====")
st, d = post("/api/team/create", {"name": "测试上岸营", "dailyTargetWords": 20}, HDR)
chk(st == 200 and d.get("ok"), f"创建队伍: invite={d.get('team',{}).get('inviteCode')}")
team_a = d["team"]; tid = team_a["id"]; code = team_a["inviteCode"]
chk(len(code) == 6, "邀请码 6 位")
# 用户 B 加入
st, d = post("/api/team/join", {"inviteCode": code}, HDR2)
chk(st == 200 and d.get("ok"), "B 加入成功")
# B 重复加入应报错
st, d = post("/api/team/join", {"inviteCode": code}, HDR2)
chk(not d.get("ok") and "已在" in d.get("error", ""), "B 重复加入被拦截")
# 队伍信息
st, d = get("/api/team/my-teams", headers=HDR)
chk(len(d.get("items", [])) == 1 and d["items"][0]["currentMembers"] == 2, f"我的队伍: {len(d.get('items',[]))} 个, 成员={d['items'][0]['currentMembers'] if d.get('items') else '?'}")
# 打卡：模拟昨天已通过接口打卡（昨天打卡后 team_member.team_streak 应为 1）
import pymysql
conn = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456", database="gz199", charset="utf8mb4")
cur = conn.cursor()
cur.execute("INSERT INTO team_checkin (team_id, user_id, checkin_date, words_learned, is_completed) VALUES (%s,'test-user-a',DATE_SUB(CURDATE(), INTERVAL 1 DAY),25,1)", (tid,))
cur.execute("UPDATE team_member SET team_streak=1 WHERE team_id=%s AND user_id='test-user-a'", (tid,))
conn.commit(); conn.close()
# 今天打卡（连续：昨天+今天 = streak 2）
st, d = post("/api/team/checkin", {"teamId": tid, "wordsLearned": 30}, HDR)
chk(st == 200 and d.get("ok") and d.get("isCompleted") == 1, f"今日打卡(30词>=20目标): completed={d.get('isCompleted')}")
# 连续天数应为 2（昨天+今天）
st, d = get("/api/team/my-teams", headers=HDR)
chk(d["items"][0]["streak"] == 2, f"连续打卡: streak={d['items'][0]['streak']}（应为2）")
# 今日重复打卡被拦截
st, d = post("/api/team/checkin", {"teamId": tid, "wordsLearned": 10}, HDR)
chk(not d.get("ok") and "已打卡" in d.get("error", ""), "今日重复打卡被拦截")
# 不达标打卡
st, d = post("/api/team/checkin", {"teamId": tid, "wordsLearned": 5}, HDR2)
chk(st == 200 and d.get("ok") and d.get("isCompleted") == 0, f"B 打卡5词未达标: completed={d.get('isCompleted')}")
# 日历
st, d = get(f"/api/team/{tid}/calendar", {"month": time.strftime("%Y-%m")}, HDR)
days = d.get("days", {})
today_idx = str(time.localtime().tm_mday)
chk(st == 200 and days.get(today_idx) == 1, f"日历今日打卡人数(达标): {days.get(today_idx)}（应为1: 仅A达标）")
# 注意：日历只统计 is_completed=1，今天 A 达标1人 + 昨天1人
st, d = get(f"/api/team/{tid}/members", headers=HDR)
chk(len(d.get("items", [])) == 2, f"成员列表: {len(d.get('items',[]))} 人")

print("\n===== 5. 边缘 =====")
st, d = get("/api/word/list", {"page": 9999, "pageSize": 20})
chk(st == 200 and d.get("items") == [], "超页返回空")
st, d = get("/api/team/by-invite", {"code": "XXXXXX"})
chk(not d.get("ok"), "无效邀请码被拦截")
st, d = post("/api/study/report", {"durationSec": 0, "wordsSeen": 0}, HDR)
chk(d.get("ok") and d.get("ignored"), "0 秒上报被忽略")

print(f"\n========== 结果: {ok} 通过 / {fail} 失败 ==========")
