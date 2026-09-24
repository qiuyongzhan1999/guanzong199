# 乐学猫小程序爬虫文档

## 一、API 基础信息

**Base URL:** `https://www.lexuemiao.com/api/app/campus/`

**AppID:** `wx5d228fe6009c0510`

---

## 二、接口列表

### 1. 学校列表接口

```
GET /list?page=X&limit=50&major_id=X&ts=时间戳&AppID=wx5d228fe6009c0510&wechatId=14
```

**参数说明：**
- `page`: 页码，从1开始
- `limit`: 每页数量，建议50
- `major_id`: 专业ID（见下表）
- `ts`: 时间戳（毫秒）
- `AppID`: 固定值
- `wechatId`: 固定值14

### 2. 学校详情接口

```
GET /college-detail?college_id=X&major_id=X&ts=时间戳&AppID=wx5d228fe6009c0510&wechatId=14
```

**参数说明：**
- `college_id`: 学校ID（从列表接口获取）
- `major_id`: 专业ID

---

## 三、专业ID映射

| major_id | 专业名称 | major_code | 说明 |
|---|---|---|---|
| 28 | MPAcc 会计 | 125300 | |
| 29 | MAud 审计 | 125700 | |
| 30 | MLis 图书情报 | 125500 | |
| 31 | MBA 工商管理 | 125100 | |
| 32 | MPA 公共管理 | 125200 | |
| 33 | MEM 工程管理 | 125600 | |
| 34 | 项目管理 MEM | 125600 | 合并到125600 |
| 35 | MTA 旅游管理 | 125400 | |

---

## 四、请求 Headers（必须带全）

```
Host: www.lexuemiao.com
appid: wx5d228fe6009c0510
authorization: Bearer <token>
xweb_xhr: 1
wechatid: 14
devicetype: XCX
deviceid: 6728AA6F-40D7-5DDD-5E96-E5FC62F4277A
studymode: 1（全日制） / 2（非全日制）
user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 MicroMessenger/7.0.20.1781
content-type: application/json
majorid: <major_id>
accept: */*
referer: https://servicewechat.com/wx5d228fe6009c0510/3/page-frame.html
```

**关键参数说明：**
- `studymode`: 1=全日制，2=非全日制
- `deviceid`: 随机UUID即可

---

## 五、JWT Token

**当前token：**
```
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vd3d3LmxleHVlbWlhby5jb20vYXBpL2FwcC93ZUNoYXQveGN4TG9naW4iLCJpYXQiOjE3OTAwMzYzODksImV4cCI6MTc5MjYyODM4OSwibmJmIjoxNzkwMDM2Mzg5LCJqdGkiOiJtRFNKbHFqeGVEZGlIQUQwIiwic3ViIjo4MjU2OTcsInBydiI6IjlmMWZlOWUwZGZmYmU0NDQyZGM3ODMxMDc1MWY1OTFjZjRkMTQwMjAiLCJyb2xlIjoidXNlciJ9.A0JNya-hHOKJnsyvjbMMsAanDzUmIp5kwC5ng71ah9A
```

**过期时间：** `1792626389`（约2026年底）

**token过期后怎么办：**
1. 用Charles抓PC微信小程序的包
2. 打开乐学猫小程序
3. 随便点一个学校详情
4. 在Charles里找`college-detail`请求
5. 复制`authorization` header里的token

---

## 六、数据库连接

```
主机: 127.0.0.1
端口: 3306
用户名: root
密码: qyz123456
数据库: gz199
```

**mysql.exe路径：**
```
C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe
```

---

## 七、数据库表结构

### school_year_stats（核心表）
| 字段 | 说明 |
|---|---|
| pack_id | 数据包ID |
| school_code | 学校代码（5位） |
| major_code | 专业代码 |
| study_mode | fulltime/parttime |
| year | 年份（2024/2025/2026） |
| reexam_min_score | 复试线 |
| min_score | 拟录取最低分 |
| max_score | 拟录取最高分 |
| reexam_count | 进复试人数 |
| admit_count | 录取人数 |
| data_confidence | confirmed=真实数据 |
| admission_source_name | 数据来源（乐学猫） |
| nation_a_total | A类国家线总分 |
| nation_a_english | A类国家线英语 |
| nation_a_comprehensive | A类国家线管综 |

**唯一键：** `(school_code, major_code, study_mode, year)`

### schools（学校表）
| 字段 | 说明 |
|---|---|
| code | 学校代码（5位） |
| name | 学校名称 |
| province | 省份 |
| is_self_line | 是否自划线 |

---

## 八、已爬数据统计（2026-09-22）

### 全日制
| 专业 | 学校数 | 数据条数 |
|---|---|---|
| MPAcc 会计 | 299所 | 1033条 |
| MBA 工商管理 | 119所 | 357条 |
| MTA 旅游管理 | 117所 | 351条 |
| MPA 公共管理 | 95所 | 285条 |
| MLis 图书情报 | 86所 | 258条 |
| MEM 工程管理 | 48所 | 227条 |
| MAud 审计 | 65所 | 195条 |
| **合计** | **829所** | **2574条** |

### 非全日制
| 专业 | 学校数 | 数据条数 |
|---|---|---|
| MBA 工商管理 | 284所 | |
| MPA 公共管理 | 276所 | |
| MEM 工程管理 | 159所 | |
| MPAcc 会计 | 135所 | |
| MTA 旅游管理 | 91所 | |
| MLis 图书情报 | 21所 | |
| 项目管理MEM | 23所 | |
| MAud 审计 | 13所 | |
| **合计** | **1002所** | **2965条** |

**总计：5539条真实数据**

---

## 九、爬取脚本列表（D:\123\gz199\data\pipeline\）

| 脚本 | 说明 |
|---|---|
| 51_crawl_all_lexuemiao.py | 爬MPAcc学校列表 |
| 53_import_lexuemiao.py | MPAcc数据入库 |
| 54_clear_fallback.py | 清掉国家线兜底数据 |
| 57_import_maud.py | MAud审计爬取+入库 |
| 58_crawl_mlis.py | MLis图书情报爬取+入库 |
| 59_crawl_mba.py | MBA工商管理爬取+入库 |
| 60_crawl_mpa.py | MPA公共管理爬取+入库 |
| 61_crawl_mem.py | MEM工程管理爬取+入库 |
| 62_crawl_mem_pm.py | 项目管理MEM爬取+入库 |
| 63_crawl_mta.py | MTA旅游管理爬取+入库 |
| 64_crawl_parttime.py | 所有专业非全日制批量爬取 |

---

## 十、后端项目信息

- **项目根目录：** `D:\123\gz199`
- **后端：** Spring Boot 3.3.4, Java 17, 端口8080
- **前端：** uni-app（微信小程序）
- **前端详情页：** `pages/schools/detail.vue`
- **前端列表页：** `pages/schools/index.vue`

---

## 十一、注意事项

1. **请求间隔**：建议每个请求间隔0.2-0.3秒，避免被封
2. **token过期**：如果返回code=201，说明token过期，需要重新抓包
3. **学校名匹配**：有些学校名称可能和数据库里的不完全一致，用LIKE模糊匹配
4. **数据来源**：全部标记为`乐学猫`，`data_confidence='confirmed'`
5. **不要存假数据**：没有真实数据就空着，不要填国家线兜底
