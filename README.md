# WealthFlow

本地个人资产管理应用：资产分类与快照、具体持仓及行业标签、趋势分析、投资知识库、AI 助手。前端为 React / Ant Design，后端为 Java 17 / Spring Boot，数据存储在 SQLite；财经讯息工具使用 Python / AKShare。

## Windows 启动

安装 JDK 17 或以上、Node.js 22.12 或以上、Python 3.11 或以上（推荐 3.12），以及 Git。设置 `JAVA_HOME` 为 JDK 目录。项目自带 Maven Wrapper，无需另装 Maven。

在项目根目录运行 PowerShell：

```powershell
# 首次安装前端依赖及财经讯息虚拟环境，随后启动
./start.ps1 -Install -Python 'C:/你的Python目录/python.exe'

# 日常启动
./start.ps1

# 仅检查环境与端口，不启动服务、不修改数据库
./start.ps1 -CheckOnly
```

`-Install` 需要联网下载 npm、Python 依赖；首次构建时 Maven 也需要联网。省略 `-Python` 时会优先尝试 `py -3.12`，其次尝试 PATH 中的 `python`。已存在的 `.venv-finance` 会复用；换机后应重新建立虚拟环境。

默认访问 `http://127.0.0.1:5173`，后端端口为 `8080`。脚本检查依赖、构建前后端，等待后端就绪后启动前端。日志在 `.wealthflow/run/`。保持终端打开，按 Ctrl+C 会关闭本次启动的两个子进程。

端口占用会明确报错，**不会结束已有进程**。也可更换端口：

```powershell
./start.ps1 -BackendPort 8081 -FrontendPort 5174
```

启动脚本将服务绑定到本机回环地址。当前产品按单人本地使用设计，没有登录和多用户隔离，请勿直接暴露到公网。

## 环境与数据位置

| 内容 | 位置 |
| --- | --- |
| 默认数据库 | 项目根目录 `wealthflow.db` |
| 财经讯息 Python | `.venv-finance/Scripts/python.exe` |
| Python 依赖 | `tools/finance/requirements.txt` |
| 前端配置及偏好 | 当前浏览器 localStorage |
| 模型配置 | SQLite；API Key 使用 Windows DPAPI 加密，与 Windows 用户绑定 |

可以用 `-DatabasePath 'D:/数据/wealthflow.db'` 指定其他数据库位置（父目录须已存在）。`WEALTHFLOW_NEWS_PYTHON` 环境变量可覆盖财经工具的 Python 路径。

开发时也可分开启动：

```powershell
./mvnw.cmd spring-boot:run
```

```powershell
cd frontend
npm.cmd run dev
```

前端默认代理至 `127.0.0.1:8080`；单独更换后端端口时，设置 `WEALTHFLOW_API_TARGET`。财经工具的接口与限制见 [财经讯息说明](tools/finance/README.md)，持仓工具见 [持仓分析工具](持仓分析工具.md)。

## 完整备份与换机

1. 旧机器打开“设置 → 完整备份与换机恢复 → 导出完整备份”。备份包括分类、快照、具体持仓、知识文档与版本、文档分块、聊天内容及引用依据、历史操作记录、模型连接配置和当前浏览器界面设置。
2. 在新机器克隆仓库，安装依赖并运行 `start.ps1`。不要复制 `node_modules` 或 `.venv-finance`。
3. 先备份新机器上需要保留的数据，再通过“恢复完整备份”导入。完整恢复为覆盖模式；文件错误或数据库约束失败时整体回滚。恢复期间停止聊天和编辑，并关闭其他页面。
4. 恢复成功后按提示刷新，在“模型设置”重新填写 API Key。需要语义搜索时，配置并重新启用 Embedding；关键词检索会立即恢复。

备份不含 API Key、Embedding 向量缓存及运行环境。待确认/执行中的历史草案会取消，AI 权限回到审核模式，以免恢复历史时重新执行写入。备份仍包含个人财务与聊天内容，请妥善保管。完整 JSON 备份目前支持 50 MB、10 万条记录以内。

原“资产数据备份与恢复”入口仍支持旧版 v1/v2/v3 文件，可合并或覆盖资产数据。它不包含知识库、聊天或设置。

紧急原始数据库备份：先正常关闭后端，再复制 `wealthflow.db`。运行时不要仅复制主文件而忽略 SQLite WAL。同一 Windows 用户可使用原库中的 DPAPI 密钥；换机推荐完整 JSON 备份并重新填 Key。

Git 同步仅传代码，数据库、密钥、备份、虚拟环境和依赖目录均不会随 Git 上传。换机需要另行传递完整备份。

## 检查与验收

```powershell
# 默认测试使用临时数据库；真实模型验收默认跳过
./mvnw.cmd test
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run lint

# 启动完整链路，使用独立空数据库，验证前端代理后自动停止本次进程
./start.ps1 -SmokeTest -BackendPort 18080 -FrontendPort 15173
```

真实模型验收会调用已保存的服务商，产生正常 API 费用。它仅以只读模式读取模型连接配置，实际发送的是临时库中的虚构持仓，不会发送或修改个人资产、文档和聊天记录：

```powershell
./mvnw.cmd '-Dtest=LivePortfolioAcceptanceTest' '-Dwealthflow.live=true' '-Dwealthflow.live.provider=你的服务商ID' test
```

服务商 ID 可在模型设置中查看。省略 provider 参数时使用首个有 Key 的配置。不同数据库可指定 `-Dwealthflow.live.database=D:/数据/wealthflow.db`。需在能解密配置的原 Windows 用户下执行。

结果保存在 `target/live-portfolio-acceptance.json`，覆盖科技仓位 60%、5%（带旧仓位历史干扰）、未登记持仓、120 天前快照、闲钱不足五种情况。自动检查工具调用、持仓依据引用及禁止写入；同时人工核对回复是否分别说明集中风险、未知敞口、数据时效和现金约束。自动检查通过不等于投资建议正确。

浏览器回归（本机 Edge，且 Node 可解析 Playwright 时）：

```powershell
cd frontend
node tests/full-backup.browser.mjs
node tests/portfolio.browser.mjs
```

浏览器测试使用模拟 API，不更改实际数据。
