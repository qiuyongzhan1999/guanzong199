"""Smoke test: DeepSeek Responses API + web_search（智能搜索）.

Usage (PowerShell):
  $env:DEEPSEEK_API_KEY = "sk-..."
  python server/scripts/test_deepseek.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

BASE = "https://api.deepseek.com"
MODEL = "deepseek-v4-pro"


def post(path: str, body: dict, key: str, timeout: int = 120) -> dict:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {key}",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8")), resp.status


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
    if not key:
        print("FAIL: set DEEPSEEK_API_KEY first")
        return 1

    # 1) 普通 chat 探活
    try:
        chat, status = post(
            "/chat/completions",
            {
                "model": MODEL,
                "messages": [
                    {"role": "system", "content": "Reply with a short JSON object only."},
                    {"role": "user", "content": 'Return JSON like {"ok": true, "service": "deepseek"}'},
                ],
                "stream": False,
                "response_format": {"type": "json_object"},
                "thinking": {"type": "disabled"},
            },
            key,
            60,
        )
        content = chat["choices"][0]["message"]["content"]
        print("OK chat", status, content)
    except urllib.error.HTTPError as e:
        print("FAIL chat HTTP", e.code, e.read().decode("utf-8", errors="replace"))
        return 2
    except Exception as e:
        print("FAIL chat", e)
        return 3

    # 2) Responses + web_search（对应网页「智能搜索」）
    try:
        resp, status = post(
            "/responses",
            {
                "model": MODEL,
                "instructions": "用联网搜索查公开信息，只输出 JSON：{\"ok\":true,\"source\":\"...\",\"note\":\"...\"}",
                "input": "搜索中国研究生招生信息网，确认管理类联考国家线页面是否存在，给出官网 URL。",
                "stream": False,
                "max_output_tokens": 2048,
                "reasoning": {"effort": "none"},
                # 不用 json_object：易空 content；用 prompt 约束 JSON
                "text": {"format": {"type": "text"}},
                "tools": [{"type": "web_search"}],
                "tool_choice": "auto",
            },
            key,
            180,
        )
        text = extract_text(resp)
        print("OK responses+web_search", status)
        print("content", text[:500])
        has_search = any(
            (item or {}).get("type") == "web_search_call"
            for item in (resp.get("output") or [])
        )
        print("web_search_call", has_search)
        return 0
    except urllib.error.HTTPError as e:
        print("FAIL responses HTTP", e.code)
        print(e.read().decode("utf-8", errors="replace"))
        return 4
    except Exception as e:
        print("FAIL responses", e)
        return 5


if __name__ == "__main__":
    sys.exit(main())
