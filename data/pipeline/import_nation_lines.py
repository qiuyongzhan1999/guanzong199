# -*- coding: utf-8 -*-
import pymysql
import json

c = pymysql.connect(host="127.0.0.1", user="root", password="qyz123456", database="gz199", charset="utf8mb4")
cur = c.cursor()

# 读nation_lines.json
with open("E:/workProject/guanzong199/data/nation_lines.json", "r", encoding="utf-8") as f:
    lines = json.load(f)

print(f"读到{len(lines)}条国家线数据")

for line in lines:
    year = line.get("year")
    family = line.get("family")
    major_codes = json.dumps(line.get("majorCodes", []))
    a_total = line.get("aTotal")
    a_english = line.get("aEnglish")
    a_comp = line.get("aComprehensive")
    b_total = line.get("bTotal")
    b_english = line.get("bEnglish")
    b_comp = line.get("bComprehensive")
    
    sql = """INSERT INTO nation_lines (year, family, major_codes_json, a_total, a_english, a_comprehensive, b_total, b_english, b_comprehensive, provider, synced_at)
             VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'import', NOW())
             ON DUPLICATE KEY UPDATE a_total=%s, a_english=%s, a_comprehensive=%s, b_total=%s, b_english=%s, b_comprehensive=%s"""
    cur.execute(sql, (year, family, major_codes, a_total, a_english, a_comp, b_total, b_english, b_comp,
                      a_total, a_english, a_comp, b_total, b_english, b_comp))

c.commit()

cur.execute("SELECT COUNT(*) FROM nation_lines")
print(f"MySQL里现在有{cur.fetchone()[0]}条国家线")

c.close()
