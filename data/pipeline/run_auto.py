# -*- coding: utf-8 -*-
"""
全自动一键跑：国家线同步 → 建队列 → 拉链接 → 抽简章 → 直接入库。

用法：
  python run_auto.py --school-codes 10034
  python run_auto.py --limit-schools 30 --years 2026,2025
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def run(cmd: list[str]):
    print(">", " ".join(cmd))
    subprocess.check_call(cmd, cwd=str(HERE))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--school-codes", default="")
    parser.add_argument("--limit-schools", type=int, default=0)
    parser.add_argument("--years", default="2026,2025,2024")
    parser.add_argument("--limit-jobs", type=int, default=80)
    parser.add_argument("--sleep", type=float, default=0.6)
    parser.add_argument("--skip-nation", action="store_true")
    args = parser.parse_args()
    py = sys.executable

    if not args.skip_nation:
        run([py, "00_sync_nation_lines.py"])

    build = [py, "01_build_queue.py", "--years", args.years]
    if args.school_codes:
        build += ["--school-codes", args.school_codes]
    if args.limit_schools:
        build += ["--limit-schools", str(args.limit_schools)]
    run(build)

    limit = args.limit_schools or (1 if args.school_codes and "," not in args.school_codes else 30)
    run([py, "02_fetch_chsi_links.py", "--limit", str(limit), "--sleep", str(args.sleep)])

    extract = [
        py,
        "03_extract_drafts.py",
        "--limit-jobs",
        str(args.limit_jobs),
        "--sleep",
        str(args.sleep),
    ]
    if args.school_codes:
        extract += ["--school-codes", args.school_codes]
    run(extract)

    run([py, "04_promote.py", "--auto"])
    run([py, "05_status.py"])


if __name__ == "__main__":
    main()
