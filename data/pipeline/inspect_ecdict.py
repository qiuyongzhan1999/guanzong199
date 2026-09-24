# -*- coding: utf-8 -*-
"""校验 ECDICT CSV 表头 + 统计 tag 分布 + 总行数。"""
import csv, io, collections

path = r"D:\123\gz199\data\pipeline\ecdict.csv"
tag_cnt = collections.Counter()
total = 0
tagged = 0
with io.open(path, encoding="utf-8", newline="") as f:
    reader = csv.reader(f)
    header = next(reader)
    print("HEADER:", header)
    for row in reader:
        if len(row) < 8:
            continue
        total += 1
        tag = row[7]
        if tag:
            tagged += 1
            for t in tag.split():
                tag_cnt[t] += 1
print(f"总行数: {total}, 带考试标签: {tagged}")
print("标签分布(前25):")
for t, c in tag_cnt.most_common(25):
    print(f"  {t}: {c}")
