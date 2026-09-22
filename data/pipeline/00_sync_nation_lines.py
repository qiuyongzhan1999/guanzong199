# -*- coding: utf-8 -*-
"""
全自动同步国家线 → data/nation_lines.json
从教育部研招网公告页解析管理类专硕分数。
"""
from __future__ import annotations

import argparse
import json
import re
import time
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "nation_lines.json"
CACHE = Path(__file__).resolve().parent / "cache" / "nation"
UA = "Mozilla/5.0 (compatible; gz199-pipeline/0.1; +auto-sync)"

KNOWN_PAGES = {
    2026: "https://yz.chsi.com.cn/kyzx/kp/202602/20260228/2293449093.html",
    2025: "https://yz.chsi.com.cn/kyzx/kp/202502/20250224/2293352975.html",
    2024: "https://yz.chsi.com.cn/kyzx/kp/202403/20240312/2293269492.html",
}


def fetch(url: str, sleep: float = 0.5) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    key = re.sub(r"[^\w.-]+", "_", url)[-160:]
    path = CACHE / f"{key}.html"
    if path.exists() and path.stat().st_size > 500:
        return path.read_text(encoding="utf-8", errors="ignore")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        raw = resp.read()
    text = raw.decode("utf-8", "ignore")
    for enc in ("utf-8", "gb18030", "gbk"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    path.write_text(text, encoding="utf-8")
    time.sleep(sleep)
    return text


def parse_mgmt_rows(html: str) -> list[dict]:
    rows = []
    for m in re.finditer(r"(?is)<tr[^>]*>(.*?)</tr>", html):
        cell = m.group(1)
        if not re.search(r"工商管理|公共管理|会计|旅游管理|图书情报|工程管理|审计", cell):
            continue
        label_m = re.search(
            r"(工商管理|公共管理|会计|旅游管理|图书情报|工程管理|审计)[^<]{0,100}",
            cell,
        )
        if not label_m:
            continue
        label = re.sub(r"\s+", "", label_m.group(0))
        nums = [int(x) for x in re.findall(r">\s*(\d{2,3})\s*<", cell)]
        if len(nums) < 6:
            plain = re.sub(r"<[^>]+>", " ", cell)
            nums = [int(x) for x in re.findall(r"\b(\d{2,3})\b", plain)]
        if len(nums) < 6:
            continue
        a_total, a_en, a_comp, b_total, b_en, b_comp = nums[:6]
        if not (130 <= a_total <= 220 and 130 <= b_total <= 220):
            continue
        rows.append(
            {
                "label": label,
                "aTotal": a_total,
                "aEnglish": a_en,
                "aComprehensive": a_comp,
                "bTotal": b_total,
                "bEnglish": b_en,
                "bComprehensive": b_comp,
            }
        )
    return rows


def map_row(raw: dict, year: int, source_url: str) -> dict | None:
    label = raw["label"]
    if "工商管理" in label and "旅游管理" in label:
        codes, family = ["125100", "125400"], "125100"
    elif "工商管理" in label:
        codes, family = ["125100"], "125100"
    elif "公共管理" in label:
        codes, family = ["125200"], "125200"
    elif "会计" in label and "图书情报" in label and "审计" in label:
        codes, family = ["125300", "125500", "125700"], "125300"
    elif "会计" in label and "审计" in label:
        codes, family = ["125300", "125700"], "125300"
    elif "会计" in label:
        codes, family = ["125300"], "125300"
    elif "旅游管理" in label:
        codes, family = ["125400"], "125400"
    elif "图书情报" in label:
        codes, family = ["125500"], "125500"
    elif "工程管理" in label:
        codes, family = ["125601", "125602", "125603", "125604"], "125600"
    elif "审计" in label:
        codes, family = ["125700"], "125700"
    else:
        return None
    return {
        "year": year,
        "family": family,
        "majorCodes": codes,
        "aTotal": raw["aTotal"],
        "aEnglish": raw["aEnglish"],
        "aComprehensive": raw["aComprehensive"],
        "bTotal": raw["bTotal"],
        "bEnglish": raw["bEnglish"],
        "bComprehensive": raw["bComprehensive"],
        "sourceUrl": source_url,
        "sourceName": f"教育部｜{year}年国家线",
        "syncedAt": date.today().isoformat(),
    }


def discover_url(year: int) -> str | None:
    if year in KNOWN_PAGES:
        return KNOWN_PAGES[year]
    for url in (
        f"https://yz.chsi.com.cn/kyzx/zt/lnfsx{year}.shtml",
        f"https://yz.chsi.com.cn/kyzx/zt/lnfsx{year - 1}.shtml",
    ):
        try:
            html = fetch(url, sleep=0.4)
            if "工商管理" not in html and "管理学" not in html:
                continue
            m = re.search(
                rf'href="(https://yz\.chsi\.com\.cn/kyzx/kp/[^"]+)"[^>]*>[^<]*{year}[^<]*基本要求',
                html,
            )
            if m:
                return m.group(1)
            m2 = re.search(r'href="(https://yz\.chsi\.com\.cn/kyzx/kp/[^"]+)"', html)
            if m2:
                return m2.group(1)
        except Exception:
            continue
    return None


def sync_year(year: int) -> list[dict]:
    url = discover_url(year)
    if not url:
        print(f"  {year}: 未找到公告 URL")
        return []
    print(f"  {year}: {url}")
    html = fetch(url)
    raw_rows = parse_mgmt_rows(html)
    if not raw_rows:
        print(f"  {year}: 解析 0 行")
        return []
    merged = {}
    for raw in raw_rows:
        row = map_row(raw, year, url)
        if not row:
            continue
        key = (row["year"], row["family"])
        prev = merged.get(key)
        if prev and len(prev["majorCodes"]) > len(row["majorCodes"]):
            continue
        merged[key] = row
    print(f"  {year}: {len(merged)} families")
    return list(merged.values())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--years", default="")
    parser.add_argument("--from-year", type=int, default=2022)
    parser.add_argument("--to-year", type=int, default=0)
    args = parser.parse_args()
    to_year = args.to_year or (date.today().year + 1)
    years = (
        [int(x) for x in args.years.split(",") if x.strip()]
        if args.years
        else list(range(args.from_year, to_year + 1))
    )
    print("sync years:", years)
    by_key = {}
    if OUT.exists():
        for row in json.loads(OUT.read_text(encoding="utf-8")):
            by_key[(row["year"], row["family"])] = row
    for year in years:
        try:
            for row in sync_year(year):
                by_key[(row["year"], row["family"])] = row
        except Exception as exc:  # noqa: BLE001
            print(f"  {year}: fail {exc}")
    out = list(by_key.values())
    out.sort(key=lambda x: (-x["year"], x["family"]))
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {OUT} rows={len(out)} years={sorted({r['year'] for r in out})}")


if __name__ == "__main__":
    main()
