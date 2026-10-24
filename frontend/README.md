# WealthFlow 前端

安装、统一启动、备份换机和验收步骤见 [项目说明](../README.md)。

在本目录运行 `npm ci`、`npm run dev`；后端默认位于 `127.0.0.1:8080`。默认使用真实 API，仅显式设置 `VITE_USE_MOCK=true` 时使用浏览器模拟数据。

`npm run build` 执行 TypeScript 检查及生产构建，`npm test` 执行数据校验测试，`npm run lint` 执行 Oxlint。
