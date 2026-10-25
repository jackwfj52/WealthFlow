# 财经讯息查询工具

聊天助手通过 `query_financial_news` 调用本目录的 Python 脚本，由 AKShare 获取东方财富的近期公开新闻。该工具只读，不访问持仓、快照、账户凭据，也不执行交易。

另提供默认关闭的 [`search_web` 通用联网搜索](WEB_SEARCH.md)，启用后可直接查询公开主题介绍、官网和公告，或补充财经资料；不以财经工具返回空结果为前提。该功能需要明确同意第三方搜索服务，再设置开关；安装依赖本身不会启用搜索。

加密货币可使用 [`query_crypto_market` 币安公开行情](CRYPTO_AND_PROGRESS.md)，聊天准备回答期间通过实时事件展示工具开始、完成或失败状态。

## 安装与启动

需要 64 位 Python 3.11 或更新版本。Python 依赖安装在项目内的 `.venv-finance`，不修改全局环境。

在项目根目录执行，Windows 示例：

```powershell
py -3.12 -m venv .venv-finance
.\.venv-finance\Scripts\python.exe -m pip install -r tools/finance/requirements.txt
.\.venv-finance\Scripts\python.exe tools/finance/news.py --query=英伟达 --days=3 --limit=5
```

Linux/macOS 示例：

```sh
python3 -m venv .venv-finance
.venv-finance/bin/python -m pip install -r tools/finance/requirements.txt
.venv-finance/bin/python tools/finance/news.py --query=英伟达 --days=3 --limit=5
```

从项目根目录启动 Spring Boot。默认自动选择 `.venv-finance/Scripts/python.exe`（Windows）或 `.venv-finance/bin/python`（其他系统）。部署到其他目录时配置以下环境变量，再重启后端：

- `WEALTHFLOW_NEWS_PYTHON`：Python 可执行文件的完整路径，不是含额外参数的命令字符串。
- `WEALTHFLOW_NEWS_SCRIPT`：本项目 `tools/finance/news.py` 的完整路径。

前端刷新后，可在 AI 助手中询问“查询最近三天英伟达的新闻，并附上来源”或“看看最近的全球财经快讯”。回答中的“财经讯息来源”可展开查看标题、发布媒体、发布时间、查询时间和原文链接。来源随会话消息保存，旧会话缺少该字段时仍可读取。

## 工具协议与接口

模型工具调用：

```json
{
  "kind": "tool",
  "tool": "query_financial_news",
  "arguments": { "query": "黄金", "days": 3, "limit": 5 }
}
```

独立接口：`POST /api/v1/financial-news/query`，请求体就是上面的 `arguments`，响应使用现有 `{code,message,data}` 包装。

| 参数 | 规则 |
| --- | --- |
| query | 可省略，默认空字符串；具体代码或主题关键词，最多 80 字 |
| days | 可省略，默认 3；1—30 的整数，向前滚动的天数 |
| limit | 可省略，默认 5；1—10 的整数 |

返回内容包括 `provider`、`query`、`days`、`retrievedAt`、`timeZone`、`coverage`、`scannedCount`、`matchedCount`、`skippedInvalidCount` 和 `articles`。每条新闻有稳定的 `id`、`title`、短摘要 `summary`、`publisher`、`publishedAt`、`retrievedAt` 与 `url`。

新闻事实使用 `[资讯:新闻ID]` 引用。后端拒绝不存在的引用；查询失败或为空时，不采纳模型编造的新闻补充。资讯原文仅作为不可信资料处理，不能改变工具权限或触发数据修改。只读 AI 权限也可以查询新闻。

## 数据范围和失败行为

- 空关键词调用 `stock_info_global_em()`，非空关键词调用 `stock_news_em(symbol=query)`。不支持任意网址、任意脚本或其他 AKShare 函数名。
- 仅在接口当次有限结果中按发布时间过滤并排序、按链接去重，不进行完整历史抓取。`matchedCount` 也只针对本次扫描结果。固定版本的关键词接口实际只请求一页，因此即使 `limit=10`，也可能返回更少结果。
- 数据源未附时区的发布时间按 UTC+8（`Asia/Shanghai`）解释；与查询时间分开保留。无效时间、未来时间、缺少标题或有效 HTTP(S) 链接的条目不会被当作新闻返回。
- 新闻摘要最多 400 字；模型不能据此声称完成全网检索、验证新闻真实性或获取实时行情。没有结果不代表没有相关事件。
- 每次调用都重新获取数据，不使用过期缓存。Java 最多允许两个查询进程并发，默认总超时 25 秒，超时终止 Python 进程。
- Python 环境缺失、依赖异常、超时、接口变更或响应格式不合法时，接口返回 HTTP 502、业务码 `50012`，聊天中明确显示查询未完成。不会静默切换到虚构结果。
- 关键词会发送给东方财富；不应在关键词中放入个人资产金额、凭据或其他隐私。工具结果随后进入当前用户配置的 AI 模型上下文。

## 验证

```powershell
.\.venv-finance\Scripts\python.exe -m unittest discover -s tools/finance -p "test_*.py" -v
```

Java 测试：`FinancialNewsServiceTest`、`PythonFinancialNewsGatewayTest`、`FinancialNewsAgentTest`。使用模拟数据验证参数边界、时间过滤、进程超时、引用校验和失败处理，不依赖在线数据源。真实网络可用性可使用上面的命令单独验证。

官方资料：[AKShare 股票与新闻接口](https://akshare.akfamily.xyz/data/stock/stock.html)、[关键词新闻实现](https://github.com/akfamily/akshare/blob/main/akshare/news/news_stock.py)、[财经快讯实现](https://github.com/akfamily/akshare/blob/main/akshare/stock_feature/stock_info.py)、[Python 版本要求](https://github.com/akfamily/akshare/blob/main/pyproject.toml)。
