# Lumora Cloud 工程架构

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
| `billing-service` | 套餐、周额度、钱包、预占、结算与不可变账本 | 46102 |
| `model-catalog-service` | 模型目录、价格版本和配置发布 | 46103 |
| `model-gateway-service` | 供应商流式代理、权威 Usage 和计费编排 | 46104 |

`cloud-common` 只共享稳定错误和 Tracing 契约，`cloud-api` 只共享跨服务 DTO/Feign 契约；两者
不得共享数据库实体、Mapper 或领域服务。

## 3. 前端模块

首版使用一个 React 应用，通过路由和角色区分用户控制台与管理端：

```text
frontend/src/
├── app/                # 入口与路由
├── pages/
│   ├── auth/
│   ├── console/
│   └── admin/
├── features/           # 按 auth/billing/model/usage 等领域组织
├── components/         # 无业务归属的共享组件
├── api/                # 后续 Cloud Gateway Client
└── styles/
```

只有在用户控制台和管理端需要不同域名、独立发布节奏或不同团队维护时，才将 `frontend` 演进为
`apps/console-web`、`apps/admin-web` 与共享 `packages/*` 的 pnpm workspace。

## 4. 当前状态

本次只创建可浏览工程骨架，不代表认证、购买和计费逻辑已经实现。后续按 User Service、Billing
Service、Model Catalog、Model Gateway、网页真实 API、Desktop 接入的顺序推进。
