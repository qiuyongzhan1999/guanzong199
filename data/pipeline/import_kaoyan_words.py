# -*- coding: utf-8 -*-
"""导入有道考神考研词书（3 册 NDJSON）到 gz199.word 表。幂等：先清空再全量导入。"""
import json, zipfile, os, pymysql

BASE = r"D:\123\gz199\data\pipeline"
ZIPS = [
    (os.path.join(BASE, "KaoYan_1.zip"), "KaoYan_1.json", "kaoyan1", "ky1"),
    (os.path.join(BASE, "KaoYan_2.zip"), "KaoYan_2.json", "kaoyan2", "ky2"),
    (os.path.join(BASE, "KaoYan_3.zip"), "KaoYan_3.json", "kaoyan3", "ky3"),
]

def read_ndjson(zip_path, inner, folder):
    dest = os.path.join(BASE, folder)
    if not os.path.exists(os.path.join(dest, inner)):
        os.makedirs(dest, exist_ok=True)
        with zipfile.ZipFile(zip_path) as z:
            z.extractall(dest)
    rows = []
    with open(os.path.join(dest, inner), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows

def parse(obj, book):
    """提取 word/phonetic/meaning/pos/sentence"""
    word = (obj.get("headWord") or "").strip()
    rank = obj.get("wordRank") or 0
    content = ((obj.get("content") or {}).get("word") or {}).get("content") or {}
    uk = content.get("ukphone") or ""
    us = content.get("usphone") or ""
    trans = content.get("trans") or []
    parts = []
    poss = []
    for t in trans:
        cn = (t.get("tranCn") or "").strip()
        pos = (t.get("pos") or "").strip()
        if cn:
            parts.append(cn)
        if pos:
            poss.append(pos)
    meaning = "；".join(parts)
    pos = "/".join(poss)
    sentences = ((content.get("sentence") or {}).get("sentences")) or []
    sen_en = sen_cn = ""
    if sentences:
        sen_en = (sentences[0].get("sContent") or "").strip()
        sen_cn = (sentences[0].get("sCn") or "").strip()
    return (word, uk, us, meaning, pos, sen_en, sen_cn, rank)

def main():
    conn = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456",
                           database="gz199", charset="utf8mb4")
    cur = conn.cursor()
    cur.execute("DELETE FROM word")
    conn.commit()
    total = 0
    skipped = 0
    for zip_path, inner, folder, book in ZIPS:
        rows = read_ndjson(zip_path, inner, folder)
        batch = []
        for obj in rows:
            parsed = parse(obj, None)
            word = parsed[0]
            if not word:
                continue
            batch.append(parsed)
        # 批量插入（word 唯一键冲突则跳过）
        sql = ("INSERT INTO word (word, phonetic, phonetic_us, meaning_cn, pos, "
               "sentence_en, sentence_cn, book, word_rank, source) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'kaoyan')")
        n = 0
        for p in batch:
            try:
                cur.execute(sql, (p[0], p[1], p[2], p[3], p[4], p[5], p[6], book, p[7]))
                n += 1
            except Exception as e:
                print("err:", p[0], e)
        total += n
        print(f"{inner} -> {book}: 入库 {n} 行")
    conn.commit()
    cur.execute("SELECT book, COUNT(*) FROM word GROUP BY book ORDER BY book")
    for r in cur.fetchall():
        print("词书", r[0], ":", r[1], "词")
    conn.close()

if __name__ == "__main__":
    main()
