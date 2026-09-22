# 刷题题库说明

- **生成脚本：** `tools/gen-practice-bank.cjs`
- **批量文件：** `data/practice/*-bulk-*.json`
- **规模：** 管综 3000 + 英语二 2000（模板参数化自编，非历年真题照搬）
- **灌库：** 后端 `POST /api/practice/reseed`（会清空旧题后重灌 bulk）

重新生成：

```bash
node tools/gen-practice-bank.cjs
curl -X POST http://127.0.0.1:8080/api/practice/reseed
```
