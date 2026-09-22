# -*- coding: utf-8 -*-
"""54_clear_fallback.py：清掉国家线兜底数据，只保留乐学猫confirmed"""
import os,pymysql
c=pymysql.connect(host="127.0.0.1",user="root",password=os.environ["DB_PASSWORD"],database="gz199")
cur=c.cursor()

# 清掉MPAcc的国家线兜底数据
cur.execute("""DELETE FROM school_year_stats
  WHERE major_code='125300' AND data_confidence='national_line_fallback'""")
print(f"清掉MPAcc兜底线: {cur.rowcount}条")

cur.execute("""DELETE FROM admission_stats
  WHERE major_code='125300' AND source_name NOT LIKE '%乐学猫%' AND source_name NOT LIKE '%众凯%'""")
print(f"清掉admission_stats旧数据: {cur.rowcount}条")

c.commit()

# 复核
cur.execute("""SELECT COUNT(*) FROM school_year_stats
  WHERE major_code='125300' AND data_confidence='confirmed'""")
print(f"\nMPAcc真实数据: {cur.fetchone()[0]}条")
cur.execute("""SELECT COUNT(*) FROM school_year_stats
  WHERE major_code='125300'""")
print(f"MPAcc总行数: {cur.fetchone()[0]}条")
c.close()
