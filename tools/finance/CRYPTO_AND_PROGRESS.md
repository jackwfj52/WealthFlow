# 币安行情与聊天实时进度

## 币安公开现货行情

`query_crypto_market` 使用币安专门提供的公开市场域名 `https://data-api.binance.vision`，只调用 `GET /api/v3/ticker/24hr` 和 `GET /api/v3/klines`。不需要密钥，不连接账户、订单、合约或交易功能，模型不能传入自定义域名或接口路径。

```json
{"kind":"tool","tool":"query_crypto_market","arguments":{"symbols":["BTCUSDT","ETHUSDT","ZECUSDT"],"days":7}}
```

- `symbols`：1—5 个不重复的大写 USDT 现货交易对；默认 BTCUSDT、ETHUSDT、BNBUSDT。宽泛问题须说明这些币不代表整个市场。
- `days`：1—30，默认 7。后端根据最近已完成的 UTC 日 K 线计算首日开盘至末日收盘涨跌，不含当前未完成日；不是“截至现在滚动 N 天收益”。
- `changePercent24h` 来自币安的滚动 24 小时窗口；窗口起止、查询时间分别返回。USDT 不等同于美元。
- 后端校验数据时效、完整日间隔、价格格式与可用历史，不填充缺失价格。交易对无效、限流、网络或地区不可用时明确报错，不绕过访问限制。
- 回答使用 `[行情:工具返回的id]` 引用；`cryptoMarkets` 随聊天响应保存。前端“币安行情依据”展示价格、滚动 24 小时数据、区间口径和币安来源链接。
- 用户禁止联网时后端拒绝行情调用。新闻和网页只补充背景，不能代替行情数值。

官方依据：[公开市场域名](https://developers.binance.com/en/docs/products/spot/faqs/market_data_only)、[现货行情接口与日K线](https://developers.binance.com/en/docs/catalog/core-trading-spot-trading/api/rest-api/market)。

## 实时工具进度

新接口：`POST /api/v1/agent/conversations/{id}/messages/stream`，请求体仍为 `{providerId,message}`，响应为 SSE。原同步发送接口继续可用。

事件：

- `progress`：`{id,tool,label,status,detail,at}`；同一个步骤以同一个 id 从 `running` 更新为 `completed` 或 `failed`。
- `heartbeat`：每 10 秒发送连接时间戳，只表示连接仍在，不能当成工具已完成或伪造进展。
- `result`：已落库的 `AgentConversationMessage`，包含最终回复、来源、行情及草案/执行状态。
- `error`：安全的 `{code,message}` 错误，不发送原始模型回复、系统提示词、密钥或完整工具参数。

前端显示当前阶段、累计耗时、完成步数和工具执行列表。回答完成后可展开处理过程；工具失败时保留错误状态。断流不会自动重发，避免重复付费或执行写操作；后台继续保存已授权请求的结果，用户刷新会话核对。

请求线程最多并行 4 个、排队 16 个，超过时返回 503。连接上限 15 分钟；应用关闭时清理工作线程和心跳。Nginx 已关闭代理缓冲，保留长请求超时。

## 验证

离线 Java 测试：`CryptoMarketServiceTest`、`BinanceMarketGatewayTest`、`CryptoAgentProgressTest`、`AgentConversationStreamTest`。

显式 `-Dwealthflow.live.crypto=true` 可运行 `CryptoMarketLiveAcceptanceTest`，仅查询公开行情，不读取账户、不调用付费模型，报告保存到 `target/live-crypto-acceptance.json`。

前端 `npm test` 验证 UTF-8 分片和 SSE 事件解析；`frontend/tests/agent-progress.browser.mjs` 用本地流式模拟接口验证回答前进度可见、计时、失败状态、单次发送和来源刷新保留。
