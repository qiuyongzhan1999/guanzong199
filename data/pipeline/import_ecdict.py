# -*- coding: utf-8 -*-
"""导入 ECDICT（77万词，取带考试 tag 的 1.5 万词）到 gz199.word 表。
表结构：word/phonetic/phonetic_us/meaning_cn/pos/sentence_en/sentence_cn/book/word_rank/source/tag/word_freq
分类：book='ecdict' 统一；tag 存完整考试标签串；word_rank=frq(COCA词频，越小越高频)；word_freq=bnc。
幂等：先清空再导入。"""
import csv, io, pymysql

CSV_PATH = r"D:\123\gz199\data\pipeline\ecdict.csv"

def parse_int(s):
    try:
        return int(float(s))
    except (ValueError, TypeError):
        return 999999

def main():
    conn = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456",
                           database="gz199", charset="utf8mb4")
    cur = conn.cursor()
    # 加列（幂等）
    for ddl in [
        "ALTER TABLE word ADD COLUMN tag VARCHAR(64) DEFAULT '' AFTER source",
        "ALTER TABLE word ADD COLUMN word_freq INT DEFAULT 999999 AFTER tag",
    ]:
        try:
            cur.execute(ddl)
            print("DDL OK:", ddl.split("(")[0])
        except pymysql.err.OperationalError as e:
            print("DDL skip(可能已存在):", e)

    cur.execute("DELETE FROM word")
    conn.commit()

    sql = ("INSERT INTO word (word, phonetic, phonetic_us, meaning_cn, pos, "
           "sentence_en, sentence_cn, book, word_rank, source, tag, word_freq) "
           "VALUES (%s,%s,'',%s,%s,'','','ecdict',%s,'ecdict',%s,%s)")
    total = 0
    batch = []
    with io.open(CSV_PATH, encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        for row in reader:
            if len(row) < 8:
                continue
            word = (row[0] or "").strip()
            tag = (row[7] or "").strip()
            if not word or not tag:
                continue  # 只导入带考试标签的词
            phonetic = (row[1] or "").strip()
            translation = (row[3] or "").strip()
            pos = (row[4] or "").strip()
            frq = parse_int(row[9]) if len(row) > 9 else 0
            bnc = parse_int(row[8]) if len(row) > 8 else 0
            rank = frq if frq > 0 else (bnc if bnc > 0 else 999999)
            batch.append((word, phonetic, translation, pos, rank, tag, bnc))
            if len(batch) >= 500:
                cur.executemany(sql, batch)
                total += len(batch)
                batch = []
    if batch:
        cur.executemany(sql, batch)
        total += len(batch)
    conn.commit()
    cur.execute("SELECT COUNT(*) FROM word")
    print("导入总词数:", cur.fetchone()[0])
    for t in ["ky", "cet4", "cet6", "gk", "ielts", "toefl", "gre"]:
        cur.execute("SELECT COUNT(*) FROM word WHERE tag LIKE %s", (f"%{t}%",))
        print(f"  {t}: {cur.fetchone()[0]}")
    conn.close()

if __name__ == "__main__":
    main()
