# WealthFlow 双击启动包

这是已构建好的 Windows 安装包。只需要 Docker Desktop，不需要 Git、Java、Node、Python，也不需要下载项目源码。

1. 安装并打开 Docker Desktop，等待引擎启动，使用 Linux containers 模式。
2. 将整个 ZIP **全部解压**到固定目录（例如 `D:\Apps\WealthFlow`），不要在压缩包内直接运行。
3. 双击 **`start.cmd`**。首次会自动导入镜像、生成本机密钥、启动服务，随后打开浏览器。

访问地址：**http://127.0.0.1:18000**。首次导入需要几分钟，之后仍然双击 `start.cmd` 即可。安装包内的应用镜像无需联网下载；模型、财经数据等在线功能仍需要网络。此包标注的 CPU 架构必须与 Docker 引擎一致，一般 Intel/AMD Windows 电脑使用 amd64 包。

停止：双击 **`stop.cmd`**，或在 Docker Desktop 中停止 wealthflow 应用。停止后数据仍保留。下次双击 `start.cmd` 启动；容器创建后也可从 Docker Desktop 启动。

如果启动失败，窗口会保留错误提示。确认 Docker Desktop 已启动、使用 Linux containers，并检查 18000 端口是否被其他程序占用。可在解压目录打开 PowerShell，运行以下命令更换端口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deploy\bundle.ps1 -Port 18001
```

数据库保存在 Docker 的 `wealthflow_wealthflow_data` 卷；本机生成的加密密钥保存在此目录的 `.wealthflow/docker/master-key.txt`。保留这两者，不要删除数据卷或覆盖密钥。复制安装包给其他人时，应发送原始 ZIP，避免带上自己启动后生成的密钥。启动包不含你的个人数据，其他人首次启动会得到空数据库。

换电脑请在旧应用“设置 → 完整备份与换机恢复”中导出 JSON，在新电脑启动后恢复，再重新填写模型 API Key。不要把安装包当作数据备份。

更新前先导出完整 JSON 备份、双击 `stop.cmd`。把新版 ZIP 解压到另一个目录，将旧目录的 `.wealthflow` 文件夹复制到新版目录（包含原主密钥），然后双击新版 `start.cmd`。同一电脑默认复用原数据卷；不要同时运行两个版本。不要把旧版的 `compose.yaml`、`images.tar` 或 `release.json` 覆盖到新版。
