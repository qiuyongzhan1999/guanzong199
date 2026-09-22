"""Smoke test: DeepSeek Chat Completions（当前业务路径）.

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


def post(path: str, body: dict, key: str, timeout: int = 120) -> tuple[dict, int]:
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


def main() -> int:
    key = (os.environ.get("DEEPSEEK_API_KEY") or "").strip()
    if not key:
        print("FAIL: set DEEPSEEK_API_KEY first")
        return 1

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
        return 0
    except urllib.error.HTTPError as e:
        print("FAIL chat HTTP", e.code, e.read().decode("utf-8", errors="replace"))
        return 2
    except Exception as e:
        print("FAIL chat", e)
        return 3


if __name__ == "__main__":
    sys.exit(main())
