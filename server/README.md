# gz199 服务端（Spring Boot）

研招网**没有**开放「学费 / 国家线 / 招录」业务 API。  
本服务是我们自己的后端：院校详情只读 MySQL；智能择校按库内分数排序，DeepSeek 只写一段建议。

## 你需要准备什么（不懂后台也按这个做）

| 软件 | 版本 | 你现在的情况 | 做什么 |
|------|------|--------------|--------|
| **JDK** | **必须 17** | 本机是 Java 8，**跑不起来** | 安装 Temurin JDK 17 |
| Maven | 3.9+ | 已有 3.9.16 | 不用再装 |
| DeepSeek Key | — | 已测通 | 启动前设环境变量 |

### 安装 JDK 17（Windows）

任选一种：

**方式 A（推荐，图形安装）**

1. 打开：https://adoptium.net/zh-CN/temurin/releases/?version=17  
2. 选 Windows x64 → `.msi` → 安装  
3. 建议装到：`D:\java\jdk-17`（你已有 `D:\java\jdk-8`）

**方式 B（命令行）**

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

### 安装后验证（新开一个 PowerShell）

```powershell
# 若 java -version 仍是 1.8，先指定 17（路径按你实际安装改）
$env:JAVA_HOME = "D:\java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

java -version
# 应看到 17.x.x，不是 1.8
```

长期生效：系统环境变量里把 `JAVA_HOME` 改成 JDK 17，并把 `%JAVA_HOME%\bin` 放到 Path 最前。

---

## 启动 Java

```powershell
cd D:\123\gz199\server

$env:JAVA_HOME = "D:\java\jdk-17"   # 路径按你的安装改
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:DEEPSEEK_API_KEY = "sk-你的key"   # 只影响择校文字建议；不配也能按分数排序

mvn spring-boot:run
```

### 验证

1. 健康检查：http://127.0.0.1:8080/api/health  
   应看到 `"mysqlReady": true`。配了 Key 时 `"deepseekConfigured": true`
2. 详情只读库：  
   http://127.0.0.1:8080/api/school-detail?schoolCode=10011&year=2026&majorCode=125100&studyMode=parttime&province=北京
3. 择校：`POST /api/match`，正文示例  
   `{"majorCode":"125300","studyMode":"fulltime","score":210,"provinces":["北京"]}`

招录数字来自爬虫写入的 MySQL，详情不再联网补数。  
择校建议的提示词在 `server/src/main/resources/skills/kaoyan-match-advice.md`，改完重启生效。模型只写正文，不锁 JSON。

**Key 不要写进代码或 Git。** 任选其一：

- 环境变量 `DEEPSEEK_API_KEY`
- `server/local-secrets.ps1`（推荐，勿提交）：`$env:DEEPSEEK_API_KEY = "sk-..."`

双击根目录 `重启后端.bat` 会自动读取上述文件。

微信小程序**不要**配置 `api.deepseek.com` 白名单；只请求你们自己的 `127.0.0.1:8080` / 上线后的 HTTPS 域名。

---

## 暂时还没装 JDK 17 时

可用 Python 开发服（**不含** DeepSeek 补数）：

```bash
python server/dev_api.py
```

---

## 当前接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 探活 |
| GET | `/api/ai/ping` | Java → DeepSeek 探活 |
| GET | `/api/years` | 年份列表 |
| GET | `/api/nation-lines` | 国家线 |
| GET | `/api/programs` | 培养信息 |
| GET | `/api/admissions` | 招录 |
| GET | `/api/school-detail` | 只读 MySQL 招录，不联网 |
| POST | `/api/match` | 按估分排序，并生成一段择校建议 |

`school-detail` 参数：`schoolCode`、`year`、`majorCode`、`studyMode`、`province`。

`match` 正文：`majorCode`、`studyMode`（fulltime/parttime）、`score`（1–300）、`provinces`（省份名数组，可空表示不限）。

建议提示词：`src/main/resources/skills/kaoyan-match-advice.md`。  
MySQL：`server/sql/schema.sql`，见 `docs/13-MySQL建表说明.md`。

---

## 和小程序对接

`utils/api.js`：

```js
export const API_BASE = 'http://127.0.0.1:8080'
```

开发者工具勾选：不校验合法域名。

---

## 下一步

- MySQL 落库（见 `docs/13`）替代 JSON 文件  
- 生产 Nginx + HTTPS  
- 微信登录  
