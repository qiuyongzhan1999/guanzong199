# 管综院校库爬虫

数据源以 **院校库同构站** 为主（有结构化分数/录取表）：

| site | 地址 | 专业 |
|------|------|------|
| mpacc | http://yuanxiao.mpacc.cc | 125300 会计 |
| maud | http://maud.mpacc.cc | 125700 审计 |
| mpa | http://mpa.mpacc.cc | 125200 公共管理 |
| mba | http://yuanxiao.mbanews.net | 125100 工商管理 |
| mem | http://yuanxiao.mxmem.com | 125600 工程管理 |
| mlis | http://mlis.mpacc.cc | 125500 图书情报 |
| mta | http://mta.mpacc.cc | 125400 旅游管理 |

年份 = 入学年 = 复试年（与 `nation_lines.year` 一致）。

## 命令

```powershell
$env:DB_PASSWORD = '你的密码'
cd D:\123\gz199\data\pipeline

# 用缓存重解析并入库（快）
python 13_run_yxk_all.py

# 强制重新下载列表+详情再入库（慢，约数小时）
python 13_run_yxk_all.py --refresh --delay 0.8

# 单站
python 10_fetch_yxk.py --site mpacc --refresh-detail
python 11_import_yxk.py --site mpacc
```

## 关于 eduei.com

https://www.eduei.com 是在职考研资讯站，`/schoolmpacc/` 是省份文章列表，**没有**院校×年份的复试线/拟录取表，不适合入库。探测报告见 `out/eduei/probe_report.md`。
