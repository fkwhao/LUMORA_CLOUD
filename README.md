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
├── deploy/                     # 虚拟机中间件 Compose 与 Nginx 配置
└── docs/                       # 整个平台的架构说明
```

## 当前可浏览内容

前端已经提供以下入口：

- `http://127.0.0.1:5175/login`
- `http://127.0.0.1:5175/console`
- `http://127.0.0.1:5175/console/usage`
- `http://127.0.0.1:5175/console/ledger`
- `http://127.0.0.1:5175/console/plans`
- `http://127.0.0.1:5175/console/orders`
- `http://127.0.0.1:5175/admin`
- `http://127.0.0.1:5175/admin/billing`
- `http://127.0.0.1:5175/admin/models`
- `http://127.0.0.1:5175/admin/providers`

```powershell
cd frontend
pnpm install
pnpm dev
```

网页注册、登录、会话恢复和退出已经连接 Cloud Gateway 与 User Service；用户侧套餐、周期额度、用量、
额度流水、套餐目录和订单已经连接 Billing Service；运营总览已接入 User、Billing 与 Model Catalog 的
真实领域统计。后端已经完成用户认证闭环、
Billing Service 的套餐、额度、订单与开发环境测试支付状态机，
Model Catalog Service 的供应商与模型发布，以及 Model Gateway 首版调用闭环：模型解析、额度预占、
并发控制、LUMORA Internal Protocol v1、Chat Completions / Responses / Anthropic Messages 适配、
供应商托管 Web Search、权威 Usage 结算和
失败补偿。管理端已支持套餐与价格版本、用户查找、幂等订阅发放，以及模型供应商、加密 API Key、
模型能力、成本、套餐额度费率、草稿、发布、启停和历史版本管理。钱包、MOCK 充值、钱包购买套餐及
Desktop 云端模型适配已经实现；真实第三方支付渠道仍待接入。旧版单体 Demo 已废弃。

## 产品边界

- Desktop 登录可选；未登录可以使用 BYOK。
- 登录后可以在 Lumora 套餐模型和自定义供应商之间切换。
- Desktop 只查询套餐、额度和用量，不承载购买、续费或充值。
- 用户侧交易统一在系统默认浏览器打开的 `/console` 中完成，网页独立登录。
- `/admin` 与 `/console` 可以共享前端工程，但后端分别使用 `/api/admin/**` 和 `/api/app/**`
  执行真实权限校验。

详细资料见 [工程架构](docs/architecture.md) 和 [云端平台设计](docs/cloud-platform-design.md)。

中间件部署到虚拟机 `192.168.100.132`，Java 服务开发阶段仍在本机运行。将
`deploy/.env.example` 复制为虚拟机上的 `deploy/.env` 并替换全部密码和密钥后，可以通过
`docker compose up -d --build` 一次启动。真实 `.env` 和 `deploy/data/` 已加入 Git 忽略规则，
不应提交到仓库；Sentinel JAR 会直接封装进本地镜像，不会落到宿主机目录。完整步骤见
[中间件部署说明](deploy/README.md)。

Sentinel/Nacos 规则和不访问真实供应商的本地 Mock 压测方式见
[本地保护与压测](tests/load/README.md)。
