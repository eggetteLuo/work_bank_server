# 银行存取款系统接口文档

## 1. 基本信息
- 服务地址：`http://localhost:8080`
- 接口前缀：`/api`
- 数据格式：`application/json`

## 2. 启动前置步骤
1. 先在数据库执行以下 SQL：
   - `sql/01_bootstrap.sql`（建库/用户/schema 示例）
   - `src/main/resources/schema.sql`（建表、约束、视图、触发器、函数）
   - `src/main/resources/data.sql`（测试数据）
2. 启动后端：
   - `./gradlew bootRun`

## 3. 登录与鉴权说明
- 登录方式：银行卡号 + 6位密码。
- 登录接口：`POST /api/auth/login`。
- 登录成功后返回 `token`。
- 需要登录（Bearer Token）的接口：
  - `GET /api/accounts/balance`
  - `POST /api/trade/deposit`
  - `POST /api/trade/withdraw`
  - `POST /api/trade/transfer`
  - `GET /api/trade/records`
  - `GET /api/trade/statement`
- 请求头格式：
```http
Authorization: Bearer <token>
```

## 4. 快速调用流程（推荐演示顺序）
1. `GET /api/ping`：检查后端在线。
2. `POST /api/accounts/open`：开户。
3. `POST /api/auth/login`：登录拿 token。
4. `GET /api/accounts/balance`：查余额。
5. `POST /api/trade/deposit`：存款。
6. `POST /api/trade/withdraw`：取款。
7. `POST /api/trade/transfer`：转账。
8. `GET /api/trade/records`：查流水。

## 5. 接口列表

### 5.1 连通性检测
- 方法：`GET`
- 路径：`/api/ping`
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/ping'
```
- 返回示例：
```json
{"status":"ok"}
```

### 5.2 开户
- 方法：`POST`
- 路径：`/api/accounts/open`
- 请求体：
```json
{
  "userName": "王五",
  "idCard": "110101199001011234",
  "phone": "13700137000",
  "address": "南京市鼓楼区",
  "openAmount": 1000,
  "withdrawPassword": "123456",
  "depositId": 1
}
```
- 调用示例：
```bash
curl -X POST 'http://localhost:8080/api/accounts/open' \
  -H 'Content-Type: application/json' \
  -d '{"userName":"王五","idCard":"110101199001011234","phone":"13700137000","address":"南京市鼓楼区","openAmount":1000,"withdrawPassword":"123456","depositId":1}'
```
- 返回示例：
```json
{
  "userId": 3,
  "cardNo": "1010357601234567"
}
```

### 5.3 登录（银行卡号 + 密码）
- 方法：`POST`
- 路径：`/api/auth/login`
- 请求体：
```json
{
  "cardNo": "1010357601234567",
  "password": "123456"
}
```
- 调用示例：
```bash
curl -X POST 'http://localhost:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"cardNo":"1010357601234567","password":"123456"}'
```
- 返回示例：
```json
{
  "token": "b8d3b7f7a4...",
  "userId": 3,
  "cardNo": "1010357601234567"
}
```

### 5.4 余额查询（需登录）
- 方法：`GET`
- 路径：`/api/accounts/balance`
- 查询参数：`cardNo`
- 请求头：`Authorization: Bearer <token>`
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/accounts/balance?cardNo=1010357601234567' \
  -H 'Authorization: Bearer <token>'
```
- 返回示例：
```json
{
  "cardNo": "1010357601234567",
  "balance": 1000.00,
  "isLost": false
}
```

### 5.5 存款（需登录）
- 方法：`POST`
- 路径：`/api/trade/deposit`
- 请求头：`Authorization: Bearer <token>`
- 请求体：
```json
{
  "cardNo": "1010357601234567",
  "amount": 200,
  "remark": "柜台存款"
}
```
- 调用示例：
```bash
curl -X POST 'http://localhost:8080/api/trade/deposit' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{"cardNo":"1010357601234567","amount":200,"remark":"柜台存款"}'
```

### 5.6 取款（需登录）
- 方法：`POST`
- 路径：`/api/trade/withdraw`
- 请求头：`Authorization: Bearer <token>`
- 请求体：
```json
{
  "cardNo": "1010357601234567",
  "amount": 100,
  "remark": "ATM取款"
}
```
- 调用示例：
```bash
curl -X POST 'http://localhost:8080/api/trade/withdraw' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{"cardNo":"1010357601234567","amount":100,"remark":"ATM取款"}'
```

### 5.7 转账（需登录）
- 方法：`POST`
- 路径：`/api/trade/transfer`
- 请求头：`Authorization: Bearer <token>`
- 请求体：
```json
{
  "fromCardNo": "1010357601234567",
  "toCardNo": "1010357607654321",
  "amount": 50,
  "remark": "转账测试"
}
```
- 调用示例：
```bash
curl -X POST 'http://localhost:8080/api/trade/transfer' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -d '{"fromCardNo":"1010357601234567","toCardNo":"1010357607654321","amount":50,"remark":"转账测试"}'
```

### 5.8 流水分页查询（需登录）
- 方法：`GET`
- 路径：`/api/trade/records`
- 查询参数：
  - `cardNo`：卡号
  - `page`：页码（默认1）
  - `size`：每页条数（默认10，最大100）
- 请求头：`Authorization: Bearer <token>`
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/trade/records?cardNo=1010357601234567&page=1&size=10' \
  -H 'Authorization: Bearer <token>'
```

### 5.9 对账单查询（需登录）
- 方法：`GET`
- 路径：`/api/trade/statement`
- 查询参数：
  - `cardNo`
  - `start`：ISO时间，如 `2026-06-01T00:00:00+08:00`
  - `end`：ISO时间，如 `2026-06-30T23:59:59+08:00`
- 请求头：`Authorization: Bearer <token>`
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/trade/statement?cardNo=1010357601234567&start=2026-06-01T00:00:00%2B08:00&end=2026-06-30T23:59:59%2B08:00' \
  -H 'Authorization: Bearer <token>'
```

### 5.10 交易统计（公开接口）
- 方法：`GET`
- 路径：`/api/stats/trade`
- 查询参数：`start`、`end`（ISO时间）
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/stats/trade?start=2026-06-01T00:00:00%2B08:00&end=2026-06-30T23:59:59%2B08:00'
```

### 5.11 未发生交易账户查询（公开接口）
- 方法：`GET`
- 路径：`/api/stats/no-trade-accounts`
- 调用示例：
```bash
curl -X GET 'http://localhost:8080/api/stats/no-trade-accounts'
```

## 6. 常见错误提示
- `未登录，请先登录`
- `登录已失效，请重新登录`
- `无权操作该银行卡`
- `银行卡号或密码错误`
- `身份证格式不正确`
- `手机号格式不正确`
- `取款密码必须为6位数字`
- `卡号不存在`
- `余额不足`
- `挂失账户不可转账`

