# WealthFlow

WealthFlow 是一个在自己电脑上运行的个人资产管理工具，通过浏览器使用。你可以记录现金、基金等资产，按日期保存资产快照，查看资产配置与变化趋势，也可以整理投资笔记，让 AI 结合已记录的持仓和知识库辅助分析。

**第一次使用？推荐下载 Windows 双击启动包：安装 Docker Desktop → 解压启动包 → 双击 `start.cmd`。无需 Git、Java、Node 或 Python。**

[下载 Windows 启动包（Intel / AMD，约 271 MB）](https://github.com/jackwfj52/WealthFlow/releases/download/v2026.10.25-ai-assistant/WealthFlow-windows-amd64-20261025-022433-707e88.zip) · [查看发布版本](https://github.com/jackwfj52/WealthFlow/releases)

## 快速开始

以下步骤适用于 **Windows + Intel / AMD 处理器（amd64 / x64）**。目前的双击启动包不适用于 ARM Windows 或 macOS；其他平台可参考 [Docker 源码部署](DOCKER.md#开发者从源码构建与启动)。

### 1. 安装并打开 Docker Desktop

从 [Docker 官方安装页面](https://docs.docker.com/desktop/setup/install/windows-install/)下载安装 Docker Desktop。按安装向导完成配置；如提示安装或更新 WSL、重启电脑，请先完成这些步骤。

安装后**打开 Docker Desktop，等待引擎启动完成**，使用 **Linux containers** 模式。后面每次运行 WealthFlow，也需要先确保 Docker Desktop 已启动。

### 2. 下载并完整解压启动包

点击上方的下载链接，或在 [Releases](https://github.com/jackwfj52/WealthFlow/releases) 的 **Assets** 中下载名称以 `WealthFlow-windows-amd64-` 开头的 ZIP。

**请选择启动包，不要下载 `Source code (zip)` 或 `Source code (tar.gz)`**：它们是开发用的源码，不包含运行镜像。

将 ZIP **全部解压**到固定目录，例如 `D:\Apps\WealthFlow`，打开其中包含 `start.cmd` 的文件夹。不要在压缩包预览窗口中直接双击启动。

### 3. 双击启动

双击 **`start.cmd`**。首次运行会自动导入镜像、准备本机存储并启动服务，可能需要几分钟，请等待窗口提示启动完成。

成功后会自动打开浏览器，地址为 **[http://127.0.0.1:18000](http://127.0.0.1:18000)**。如果浏览器没有自动打开，手动访问这个地址即可。

看到 WealthFlow 的“资产总览”页面，就可以开始使用了。首次打开没有资产数据是正常的，需要自行录入或恢复已有备份。

## 首次打开后，先做什么？

1. 在 **分类管理** 中添加自己的资产类别，例如现金、基金、股票。
2. 在 **资产快照** 中记录某一天各类别的金额，再到 **资产总览 / 趋势分析** 查看配置和变化；需要时在 **具体持仓** 中补充标的、类内占比与行业。
3. 想使用 AI 时，打开 **设置 → AI 模型提供商**，填写服务商的 Base URL、模型名称和 API Key，再进入 **AI 助手**。也可以先在 **投资知识库** 中添加自己的策略和笔记。

资产记录与趋势查看不要求配置模型。AI 需要你自己的模型服务，调用可能产生服务商费用；使用时会向所配置的服务发送完成分析所需的信息。

## 日常启动与停止

| 想做什么 | 操作 |
| --- | --- |
| 再次打开应用 | 启动 Docker Desktop，然后双击 `start.cmd` |
| 重新打开网页 | 浏览器访问 [http://127.0.0.1:18000](http://127.0.0.1:18000) |
| 停止应用 | 双击 `stop.cmd`，或在 Docker Desktop 中停止 `wealthflow` |

关闭浏览器不会停止服务。使用 `stop.cmd` 停止后，已录入的数据仍会保留。

## 常见问题

| 遇到的问题 | 如何处理 |
| --- | --- |
| 提示 Docker 未安装或未就绪 | 确认已安装并打开 Docker Desktop，等待引擎启动完成后重试；刚安装后仍找不到 Docker 时，重新打开启动窗口 |
| 提示需要 Linux containers | 将 Docker Desktop 切换为 Linux containers 后重试 |
| 找不到 `start.cmd`，或提示缺少 `images.tar` | 确认下载的是启动 ZIP，并已完整解压；不要使用 GitHub 的 Source code 压缩包 |
| 浏览器打不开页面 | 等待启动窗口显示 `WealthFlow is ready` 后再访问；如果窗口显示错误，请先处理该错误 |
| 提示镜像损坏或校验失败 | 重新下载并解压启动包 |
| 能打开页面，但 AI 或财经讯息不可用 | 这些功能需要外网；检查网络、模型地址、API Key 和服务商额度 |

**提示 18000 端口被占用？** 在包含 `start.cmd` 的目录中打开 PowerShell，运行以下命令改用 18001：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\bundle.ps1 -Port 18001
```

随后访问 [http://127.0.0.1:18001](http://127.0.0.1:18001)。以后使用这个端口时仍运行同一命令；双击 `start.cmd` 会使用默认的 18000。

如果仍无法启动，在同一目录运行 `docker compose logs --tail 100` 查看日志。反馈问题时请附上启动窗口的具体错误信息，并留意日志中的个人内容。

## 数据、备份与换电脑

Docker 版的资产、知识库和聊天记录保存在本机 Docker 数据卷中；模型 API Key 加密保存。解压目录下自动生成的 `.wealthflow/docker/master-key.txt` 用于解密 Key，请保留该文件，日常停止时也不要删除 Docker 数据卷。

**备份和迁移请使用应用内的完整备份：**

1. 在旧应用打开 **设置 → 完整备份与换机恢复 → 导出完整备份**，保存 JSON 文件。
2. 在新电脑按上面的步骤启动 WealthFlow。
3. 在同一设置入口选择 **恢复完整备份**。恢复会覆盖新电脑当前的数据；如已有内容，先另行备份，恢复期间停止聊天和编辑并关闭其他页面。
4. 恢复后按提示刷新，并在 **设置 → AI 模型提供商** 重新填写 API Key。需要语义搜索时，重新配置并启用 Embedding。

完整备份包括资产分类、快照、具体持仓、知识文档与版本、聊天及引用、操作记录、模型连接配置和浏览器界面设置；不含 API Key、Embedding 向量缓存及运行环境。恢复时，未完成的历史草案会取消，AI 权限回到审核模式。备份包含个人财务与聊天内容，请妥善保管；目前支持 50 MB、10 万条记录以内。

原 **资产数据备份与恢复** 入口用于旧版 v1/v2/v3 资产备份，不包含知识库、聊天和设置。迁移整个应用时请选择 **完整备份与换机恢复**。

**更新版本：** 先导出完整备份并运行 `stop.cmd`。将新版启动包解压到另一个目录，把旧目录的 `.wealthflow` 文件夹复制过去，再运行新版 `start.cmd`；同一电脑默认复用原数据卷。保留原主密钥，不要同时运行两个版本。更多细节见 [启动包使用说明](deploy/BUNDLE-README.md)。

当前按单人本地使用设计，没有登录与多用户隔离，请勿直接暴露到公网。

## 开发与源码部署

<details>
<summary>展开：从源码构建、Windows 本机开发、打包与测试</summary>

前端为 React / Ant Design，后端为 Java 17 / Spring Boot，数据存储在 SQLite；财经讯息工具使用 Python / AKShare。

### Docker 源码构建

先安装并启动 Docker Desktop，使用 Linux containers 模式。通过 Git 获取源码后进入项目目录（也可下载并解压源码 ZIP）：

```powershell
git clone https://github.com/jackwfj52/WealthFlow.git
cd WealthFlow
```

在项目根目录执行：

```powershell
# 仅首次需要：生成容器加密主密钥；重复运行不会更换已有密钥
./deploy/init-secrets.ps1

# 构建并等待前后端健康后启动
docker compose up -d --build --wait
```

打开 **http://127.0.0.1:18000**。首次启动需要联网下载镜像、Java/npm/Python 依赖；宿主机无需安装 Java、Node 或 Python。后续可用 `docker compose up -d --wait` 启动，更新代码后再加 `--build`。

Linux/macOS 从源码构建时先运行 `sh deploy/init-secrets.sh`（初始化需 OpenSSL），然后使用相同的 Compose 命令。

```powershell
docker compose ps             # 查看健康状态
docker compose logs -f        # 查看日志
docker compose down           # 停止服务，保留数据库卷
```

容器数据库与项目根目录的 `wealthflow.db` 相互独立。迁移现有数据时，在原应用导出完整 JSON 备份，到容器版“设置”中恢复，并重新填写 API Key；原 Windows DPAPI 密文不能直接用于 Linux 容器。

主密钥位于 `.wealthflow/docker/master-key.txt`，已被 Git 和 Docker 构建上下文排除。保留该文件与数据库卷的对应关系，丢失后无法解密已保存的模型 Key。

### Windows 本机开发（不使用 Docker）

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

启动脚本将服务绑定到本机回环地址。

### 本机开发的环境与数据位置

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

### 生成 Windows 双击启动包

在已启动 Docker 的开发电脑上运行：

```powershell
./deploy/package-release.ps1
```

默认先构建两个镜像，再在 `.wealthflow/releases/` 生成 ZIP。包内不含本机数据库或主密钥。若已构建好当前要发布的镜像，可加 `-SkipBuild`；该选项不检查源码是否更新，需自行确认版本。包按镜像的 CPU 架构生成。

普通用户使用上方 Releases 中已发布的启动包即可；维护者的发布、架构与验收说明见 [Docker 部署说明](DOCKER.md)。

### 开发环境的数据迁移

紧急原始数据库备份：先正常关闭后端，再复制 `wealthflow.db`。运行时不要仅复制主文件而忽略 SQLite WAL。同一 Windows 用户可使用原库中的 DPAPI 密钥；换机推荐完整 JSON 备份并重新填 Key。

换机后重新安装依赖，不要复制 `node_modules` 或 `.venv-finance`。Git 同步仅传代码，数据库、密钥、备份、虚拟环境和依赖目录均不会随 Git 上传。换机需要另行传递完整备份。

### 检查与验收

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

</details>
