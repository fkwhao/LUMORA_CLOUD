# Lumora Cloud 工程架构

最后同步：2026-09-01。

## 1. 工程边界

```text
LUMORA Desktop ──登录/权益查询/Managed 模型调用──→ Lumora Cloud Gateway
       │
       └──购买/续费/管理套餐──→ 系统默认浏览器──→ Lumora Cloud Frontend

Lumora Cloud Frontend
  ├── /console/** ──→ /api/app/**
  └── /admin/**   ──→ /api/admin/**
```

Desktop 与网页控制台使用独立登录会话。网页 URL 不携带 Desktop Access Token、Refresh Token
或 Session。未登录的 Desktop 仍可以使用本地 BYOK。

## 2. 后端模块

后端采用与参考工程一致的平级 Maven 模块组织：`cloud-common`、`cloud-api`、Gateway 和每个
业务 Service 都由 `backend/pom.xml` 直接聚合，不额外套 `libs/` 或 `services/` 容器目录。

| 模块 | 职责 | 默认端口 |
| --- | --- | --- |
| `cloud-gateway` | 路由、凭据初验、上下文传递和粗粒度限流 | 46100 |
| `user-service` | 注册、登录、设备会话、角色与审计 | 46101 |
| `billing-service` | 套餐、订单、支付确认、周额度、预占、结算与不可变账本；钱包后续实现 | 46102 |
| `model-catalog-service` | 模型目录、Token 额度费率、多时段成本/额度倍率版本和配置发布 | 46103 |
| `model-gateway-service` | 供应商流式代理、权威 Usage 和计费编排 | 46104 |

`cloud-common` 只共享稳定错误和 Tracing 契约，`cloud-api` 只共享跨服务 DTO/Feign 契约；两者
不得共享数据库实体、Mapper 或领域服务。

## 3. 数据库与迁移

数据库按业务服务划分所有权，服务只能迁移和写入自己拥有的数据库：

| 服务 | 数据库 | 迁移目录 |
| --- | --- | --- |
| `user-service` | `lumora_user` | `src/main/resources/db/migration/` |
| `billing-service` | `lumora_billing` | `src/main/resources/db/migration/` |
| `model-catalog-service` | `lumora_model_catalog` | `src/main/resources/db/migration/` |

业务表创建、索引调整和数据结构升级将通过各服务自己的 Flyway 版本化脚本管理，例如
`V1__init_user_schema.sql`、`V2__add_user_status.sql`。`model-gateway-service`、Gateway 等当前
不拥有数据库的模块不创建迁移目录，也不能直接读写其他服务的表。

`deploy/mysql/init/` 只负责空 MySQL 数据目录首次启动时创建数据库和授予基础权限，不承担后续
表结构升级。若未来增加仓库根目录 `database/`，只存放数据库说明、结构快照或需要人工确认的
运维脚本，不能代替各服务的 Flyway 迁移。

## 4. 前端模块

首版使用一个 React 19/Vite 应用，通过路由和角色区分用户控制台与管理端。基础组件体系采用
HeroUI v3 与 Tailwind CSS v4；HeroUI 负责可访问的表单、按钮、表格、标签页、抽屉和弹层等交互
原语。首版直接采用 HeroUI 默认暗色主题、语义色和组件外观，工程内只维护业务布局、图表和少量
Lumora 标识，避免形成独立且维护成本较高的视觉组件体系。

```text
frontend/src/
├── app/                # 入口与路由
├── pages/
│   ├── auth/
│   ├── console/
│   └── admin/
├── features/           # 按 auth/billing/model/usage 等领域组织
├── components/         # 无业务归属的共享组件
├── api/                # Cloud Gateway Client 与认证会话
└── styles/
```

只有在用户控制台和管理端需要不同域名、独立发布节奏或不同团队维护时，才将 `frontend` 演进为
`apps/console-web`、`apps/admin-web` 与共享 `packages/*` 的 pnpm workspace。

## 5. 当前状态

User Service 认证闭环已经实现：Flyway 创建用户、角色、设备会话、Refresh Token 和登录审计表；
Gateway 完成 JWT 校验、Redis 会话撤销检查和可信身份头注入；网页端完成真实登录、刷新和退出。
Billing Service 已完成套餐/版本、订单、开发环境测试支付、购买订阅发放/顺延、管理员订阅发放、
订阅锚定的七天额度桶、预占/结算/释放、用量、待对账状态和不可变账本。Model Catalog Service 已完成供应商、模型草稿、不可变发布版本、启停、用户目录、
内部解析、AES-GCM Provider 凭据管理和 Redis 发布快照。两者均已通过真实 Nacos/MySQL 集成测试，Model Catalog 还验证了 Redis
缓存链路。Model Gateway 已经实现模型解析、分布式并发控制、Billing 预占、Chat Completions、
Responses 与 Anthropic Messages JSON/SSE 代理、权威 Usage 结算和待对账补偿，并通过真实
Nacos/Redis 联合测试。管理端运营总览已通过各领域只读统计接口展示真实用户、订单、收入、Usage 与
模型资源数据。钱包、真实第三方支付渠道和 Desktop 云端模型适配仍待实现。
