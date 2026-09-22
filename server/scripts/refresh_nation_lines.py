# -*- coding: utf-8 -*-
"""
用 DeepSeek Responses + web_search 拉最新管理类联考国家线，写入 MySQL nation_lines。

用法：
  $env:DEEPSEEK_API_KEY = "sk-..."
  $env:DB_PASSWORD = "..."
  python server/scripts/refresh_nation_lines.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.request
from datetime import date

import pymysql

BASE = "https://api.deepseek.com"
MODEL = "deepseek-v4-pro"

FAMILIES = [
    {"family": "125100", "majorCodes": ["125100"], "name": "工商管理"},
    {"family": "125200", "majorCodes": ["125200"], "name": "公共管理"},
    {"family": "125300", "majorCodes": ["125300", "125500", "125700"], "name": "会计/图情/审计"},
    {"family": "125400", "majorCodes": ["125400"], "name": "旅游管理"},
    {"family": "1256", "majorCodes": ["125601", "125602", "125603", "125604", "1256"], "name": "工程管理类"},
]


def post_responses(key: str, prompt: str) -> dict:
    body = {
        "model": MODEL,
        "instructions": (
            "你必须联网搜索研招网/教育部官网，只输出 JSON。"
            "查不到的年份字段填 null。禁止编造。"
        ),
        "input": prompt,
        "stream": False,
        "max_output_tokens": 4096,
        "reasoning": {"effort": "none"},
        "text": {"format": {"type": "json_object"}},
        "tools": [{"type": "web_search"}],
        "tool_choice": {"type": "web_search"},
    }
    req = urllib.request.Request(
        BASE + "/responses",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read().decode("utf-8"))


def extract_text(obj: dict) -> str:
    if obj.get("output_text"):
        return str(obj["output_text"])
    chunks = []
    for item in obj.get("output") or []:
        if item.get("type") != "message":
            continue
        for part in item.get("content") or []:
            if part.get("type") == "output_text" and part.get("text"):
                chunks.append(part["text"])
    return "\n".join(chunks)


def main() -> int:
    key = (os.environ.get("DEEPSEEK_API_KEY") or "").strip()
    password = os.environ.get("DB_PASSWORD") or ""
    if not key:
        print("FAIL: set DEEPSEEK_API_KEY")
        return 1
    if not password:
        print("FAIL: set DB_PASSWORD")
        return 1

    end_year = date.today().year + 1
    start_year = end_year - 4
    prompt = f"""
搜索中国研究生招生信息网（yz.chsi.com.cn）近{start_year}-{end_year}年
管理类联考各专业国家线（A类/B类总分、英语、综合）。

返回 JSON：
{{
  "lines": [
    {{
      "year": 2025,
      "family": "125100",
      "majorCodes": ["125100"],
      "aTotal": 0, "aEnglish": 0, "aComprehensive": 0,
      "bTotal": 0, "bEnglish": 0, "bComprehensive": 0,
      "sourceUrl": "",
      "sourceName": ""
    }}
  ]
}}

family 取值：125100 工商管理；125200 公共管理；125300（含会计125300/图情125500/审计125700）；
125400 旅游管理；1256 工程管理类（125601-125604）。
每年每个 family 一条。
"""
    print("calling DeepSeek web_search…")
    raw = post_responses(key, prompt)
    text = extract_text(raw)
    print("raw head:", text[:300])
    parsed = json.loads(text)
    lines = parsed.get("lines") or parsed.get("data") or []
    if not lines:
        print("FAIL: empty lines")
        return 2

    conn = pymysql.connect(
        host="127.0.0.1",
        user="root",
        password=password,
        database="gz199",
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        cur = conn.cursor()
        # 只替换本次覆盖到的 year+family；先删同年同 family 再插
        sql = """
        INSERT INTO nation_lines (
          year, family, major_codes_json,
          a_total, a_english, a_comprehensive,
          b_total, b_english, b_comprehensive,
          source_url, source_name, provider, synced_at
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'deepseek_web_search',%s)
        ON DUPLICATE KEY UPDATE
          major_codes_json=VALUES(major_codes_json),
          a_total=VALUES(a_total), a_english=VALUES(a_english), a_comprehensive=VALUES(a_comprehensive),
          b_total=VALUES(b_total), b_english=VALUES(b_english), b_comprehensive=VALUES(b_comprehensive),
          source_url=VALUES(source_url), source_name=VALUES(source_name),
          provider=VALUES(provider), synced_at=VALUES(synced_at)
        """
        today = date.today().isoformat()
        n = 0
        for row in lines:
            year = row.get("year")
            family = str(row.get("family") or "")
            if not year or not family:
                continue
            codes = row.get("majorCodes") or row.get("major_codes") or [family]
            # unique key is (year, family) in schema
            cur.execute(
                sql,
                (
                    int(year),
                    family,
                    json.dumps(codes, ensure_ascii=False),
                    row.get("aTotal") or row.get("a_total"),
                    row.get("aEnglish") or row.get("a_english"),
                    row.get("aComprehensive") or row.get("a_comprehensive"),
                    row.get("bTotal") or row.get("b_total"),
                    row.get("bEnglish") or row.get("b_english"),
                    row.get("bComprehensive") or row.get("b_comprehensive"),
                    row.get("sourceUrl") or row.get("source_url"),
                    row.get("sourceName") or row.get("source_name") or "教育部｜国家线",
                    today,
                ),
            )
            n += 1
        conn.commit()
        print(f"DONE upserted {n} nation lines")
        return 0
    except Exception as e:
        conn.rollback()
        print("FAIL", e)
        return 3
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
