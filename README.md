# Lumora Cloud

Lumora Cloud 是与 LUMORA Desktop 独立部署的云端平台工程。顶层按后端、前端和部署配置划分，
而不是把整个仓库视为单一后端工程。

```text
LUMORA_CLOUD/
├── backend/                    # Java 21 / Spring Cloud Maven 多模块工程
│   ├── cloud-common/
│   ├── cloud-api/
│   ├── cloud-gateway/
│   └── *-service/
├── frontend/                   # React/Vite 用户控制台与管理端
├── deploy/                     # Nginx 与本地基础设施配置
└── docs/                       # 整个平台的架构说明
```

## 当前可浏览内容

前端已经提供四个演示入口：

- `http://127.0.0.1:5175/login`
- `http://127.0.0.1:5175/console`
- `http://127.0.0.1:5175/console/plans`
- `http://127.0.0.1:5175/admin`

```powershell
cd frontend
pnpm install
pnpm dev
```

页面目前使用演示数据，登录提交只用于预览页面流转。后端只创建了服务边界和启动入口；旧版
单体 Demo 已废弃，业务能力将按当前微服务边界重新实现。

## 产品边界

- Desktop 登录可选；未登录可以使用 BYOK。
- 登录后可以在 Lumora 套餐模型和自定义供应商之间切换。
- Desktop 只查询套餐、额度和用量，不承载购买、续费或充值。
- 用户侧交易统一在系统默认浏览器打开的 `/console` 中完成，网页独立登录。
- `/admin` 与 `/console` 可以共享前端工程，但后端分别使用 `/api/admin/**` 和 `/api/app/**`
  执行真实权限校验。

详细资料见 [工程架构](docs/architecture.md) 和 [云端平台设计](docs/cloud-platform-design.md)。

本地启动 `deploy/docker-compose.yml` 前，将 `deploy/.env.example` 复制为 `deploy/.env` 并替换其中的
本地数据库密码。真实 `.env` 文件已加入 Git 忽略规则，不应提交到仓库。
