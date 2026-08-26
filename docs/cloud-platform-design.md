# LUMORA 云端平台设计

## 1. 文档状态

本文记录 LUMORA 云端能力的目标设计。当前 `LUMORA_CLOUD` 已建立后端 Maven 多模块、统一
React 前端和本地部署配置骨架；登录、套餐、模型代理和计费闭环仍未实现。工程目录、模块边界
和默认端口参见同目录下的 `architecture.md`。

本阶段只确定云端技术边界，不实现 Desktop Hook 生命周期、自动化或操作系统沙箱。

## 2. 产品范围

云端平台同时支持两种模型使用方式：

- `LOCAL_BYOK`：用户在本机配置自己的模型供应商 API Key，Python Agent 直接调用供应商；
  对话、任务和用量记录保存在本机，不消耗 LUMORA 云端额度。
- `CLOUD_MANAGED`：用户登录后选择 LUMORA 提供的模型，Python Agent 经 LUMORA Model
  Gateway 二次调用模型供应商 API；云端负责权限、限流、用量统计和结算。

Desktop 登录是可选能力，登录状态与模型来源相互独立：

| Desktop 状态 | `LOCAL_BYOK` | `CLOUD_MANAGED` |
| --- | --- | --- |
| 未登录 | 可用 | 不可用 |
| 已登录 | 可用 | 套餐有效且额度充足时可用 |

用户可以跳过登录，只配置自己的模型供应商；登录后也可以继续使用 BYOK，不强制切换到云端套餐。
当前模型来源由用户在 Desktop 设置页显式选择，登录、退出或套餐状态变化不得删除另一侧配置，也不得
静默切换模型来源。若当前选择云端套餐但登录失效或权益不可用，Desktop 应提示重新登录、前往网页
控制台处理套餐，或由用户手动切换到自定义供应商。

云端模型只代理第三方模型供应商 API，不自行部署或托管大模型。

首版提供两种售卖机制：

- 包月套餐：按月获得权益，并在套餐有效期内按周生成额度周期；本周额度耗尽后等待下一周刷新。
- 用量付费：用户钱包预先获得余额，平台按照实际模型用量扣费。

项目不接入第三方支付。钱包充值由管理员手动发放，但仍必须经过幂等接口和不可变账本，
不能直接修改数据库余额。

Desktop 只实现原生登录、退出、登录状态恢复、当前套餐、套餐额度、刷新时间和用量查询，不实现
钱包充值、套餐购买、续费、升级、取消或其他资金/订阅变更。所有用户侧交易与套餐管理统一放在
网页用户控制台。Desktop 中的“购买”“续费”“管理套餐”等入口只通过 Electron Main 调用系统
默认浏览器打开受信任的控制台 URL，不使用 Electron 内置浏览器。浏览器与 Desktop 使用独立登录
会话，不传递或复用 Desktop 登录态；用户进入控制台后在浏览器中独立登录。

## 3. 总体拓扑

```text
LUMORA Desktop（登录、套餐概览、用量与模型选择）──────┐
Python Agent（CLOUD_MANAGED） ────────────────────────┼──→ Spring Cloud Gateway
默认浏览器 ─→ Nginx（用户控制台/管理页面、/api 代理）─┘          ├── user-service
                                                               ├── billing-service
                                                               ├── model-catalog-service
                                                               └── model-gateway-service
                                                                        └── WebClient ──→ 模型供应商 API

LUMORA Desktop ──→ Local Core ──→ Python Agent
                                      └── LOCAL_BYOK ────────────────→ 模型供应商 API

服务注册与配置：Nacos
服务间控制调用：OpenFeign
热点状态：Redis
业务事实与账本：MySQL
```

Desktop 的登录、套餐概览、用量和模型来源页面随客户端打包，不由 Nginx 托管，可直接调用 Gateway；
网页端由 Nginx 托管静态 HTML，并把 `/api` 反向代理到同一个 Gateway。两个入口复用相同的领域服务
和用户身份体系，但权限能力不同：Desktop 使用登录、退出、只读权益/用量查询和云端模型调用接口；
网页用户控制台负责购买、续费、充值和套餐管理等写操作，不维护第二套后端业务逻辑。

当前个人开发阶段统一使用 HTTP，不配置 HTTPS、TLS 证书或双向 TLS，避免证书环境影响本地联调；
应用层的登录、Access Token、权限校验和服务接口边界仍然保留。HTTPS/TLS 终止属于后续公网部署项，
届时由 Nginx 或云负载均衡承担。Spring Cloud Gateway 负责统一 API 入口、路由、登录凭据的初步校验、
粗粒度限流和请求上下文传递，不拥有套餐、钱包或模型配置数据。

## 4. 工程组织

云端后端采用一个代码仓库、一个 IDEA 工程和 Maven 多模块结构。每个业务服务是独立的
Spring Boot 应用，拥有独立启动配置和部署单元：

```text
LUMORA_CLOUD/
├── backend/
│   ├── pom.xml
│   ├── cloud-common/          # 最小公共错误、Tracing 和安全契约
│   ├── cloud-api/             # OpenFeign 接口与跨服务 DTO
│   ├── cloud-gateway/         # Spring Cloud Gateway
│   ├── user-service/
│   ├── billing-service/
│   ├── model-catalog-service/
│   └── model-gateway-service/
├── frontend/                  # 网页用户控制台与管理员页面
├── deploy/                    # Nginx 与本地基础设施配置
└── docs/
```

不得通过 `cloud-common` 共享数据库实体或 Mapper。跨服务只共享稳定 DTO 和接口契约，
各服务不能直接写入其他服务拥有的表。

## 5. 服务职责

### 5.1 Cloud Gateway

- 通过 Nacos 发现服务并加载路由。
- 校验 Access Token 的基本有效性并传递可信用户上下文。
- 区分公开、用户、管理员和内部接口。
- 执行 IP、用户和接口级的粗粒度限流。
- 将模型流式响应原样传回客户端，不执行余额结算。

### 5.2 User Service

- 用户注册、登录、退出和设备会话。
- 用户状态、角色和管理员权限。
- Redis Session 与短期登录状态。
- Refresh Token 的轮换、撤销和登录审计。

Model Gateway 不应通过 OpenFeign 在每次模型请求中同步查询完整用户资料。Access Token
应支持本地验签；需要强制注销或封禁时再结合 Redis Session/撤销状态判断。

Desktop 云端凭据应由 Electron Main 持有并写入操作系统受保护存储，不进入 Renderer 的
`localStorage`。Renderer 只通过白名单 IPC 获取脱敏后的账户状态；Python Agent 仅在当前
`CLOUD_MANAGED` 请求中接收必要的短期 Access Token，不长期保存 Refresh Token。

### 5.3 Billing Service

- 套餐、套餐版本、用户权益和周额度桶。
- 钱包、管理员手动充值和余额流水。
- 模型请求额度预占、实际结算和多余预占释放。
- 用量记录、价格快照、账单查询和定时对账。

钱包与用量扣减首版不拆成两个微服务。一次结算通常需要同时改变额度、余额、预占状态和
账本，过早拆分会立即引入跨服务金额事务。未来接入真实支付渠道时，再评估独立
`payment-service`。

### 5.4 Model Catalog Service

- 模型供应商、Provider Key 和上游地址。
- 模型目录、协议类型、上下文窗口、最大输出和能力开关。
- 成本价、销售价、价格版本和套餐可见范围。
- 模型配置的草稿、发布、停用和版本查询。

Provider Key 只保存在服务端 Secret Manager 或受保护配置中，不能返回 Desktop、浏览器或
Python Agent。

### 5.5 Model Gateway Service

- 校验云端模型、套餐权益和调用权限。
- 执行模型、用户和设备级限流与并发控制。
- 通过 OpenFeign 调用 Billing Service 的 `reserve`、`settle` 和 `release`。
- 使用 WebClient 调用供应商 API 并处理 SSE/流式响应。
- 解析供应商返回的权威 TokenUsage，并生成唯一用量事件。
- 缓存已发布模型配置，不在每次请求中同步调用 Model Catalog Service。

OpenFeign 与 WebClient 不互相替代：OpenFeign 用于内部短时控制调用；WebClient 用于连接
模型供应商并持续消费流式 HTTP 响应。

### 5.6 Python Agent 与流式协议边界

云端首版不设置 `agent-service`。Agent 循环、上下文管理、工具调用、压缩和本地 Token 聚合仍由
Desktop 内的 Python Agent 执行；Spring Cloud Gateway 只把模型请求路由到 Model Gateway Service。

Model Gateway 应优先暴露与 Python Agent 现有 Provider 适配器兼容的请求和 SSE 协议，不在首版
创造另一套私有流式协议。Python Agent 只新增轻量的 `LUMORA_MANAGED` 模型来源：使用云端 Base URL、
登录 Access Token 和云端模型目录，同时复用现有协议的文本、推理、Tool Call、Finish Reason、错误和
TokenUsage 解析逻辑。

供应商流式响应经过 Model Gateway 时不是无法观察的字节转发。Model Gateway 必须解析计费字段，必要时
完成协议标准化，再向 Agent 继续传递等价的流式事件和最终 Usage：

```text
供应商 SSE → Model Gateway
                 ├──→ Billing Service：服务端权威 Usage，用于结算和账本
                 └──→ Python Agent：透传/标准化 Usage，用于本地统计和界面展示
```

两条 Usage 路径可以来自同一供应商终态事件，但信任边界不同：Billing 只接受 Model Gateway 生成的
幂等用量事件，不能使用 Python Agent 回传的数据扣费。Agent 已有 Token 统计继续用于本地展示、上下文
管理和诊断，不承担云端账单事实来源。

## 6. 前端与管理端边界

Admin 不是独立业务微服务。管理能力由各数据所有者提供：

```text
/api/app/**       普通用户与 Desktop 接口
/api/admin/**     管理员接口
/api/internal/**  OpenFeign 内部接口，只允许服务身份访问
```

典型归属如下：

- 用户查询、封禁和角色管理：User Service。
- 套餐、钱包调整、账本和用量查询：Billing Service。
- 模型配置、价格发布和启停：Model Catalog Service。
- 网关运行指标和调用诊断：Model Gateway Service。

前端可以由以下入口共同复用这些接口：

- Desktop 内的登录、只读套餐/额度/用量页面和模型来源选择。
- 网页用户控制台中的套餐购买、续费、充值、套餐管理和账单查询。
- 仅管理员可见的管理页面。

用户控制台和管理员页面可以放在同一个 React 工程中，通过角色控制导航与路由。只有跨服务
Dashboard 聚合明显复杂时，才增加只做查询聚合的 `admin-bff`；它不拥有业务数据，也不执行
充值、扣费或模型配置规则。

Desktop 打开用户控制台时，Renderer 只能向 Electron Main 请求打开预先配置且通过 HTTPS
域名/路径白名单校验的 URL，再由 Main 使用系统默认浏览器打开。不得让 Renderer 直接打开任意
外部地址，也不得把 Desktop Access Token、Refresh Token 或 Session 参数附加到控制台 URL。

## 7. 计费闭环

### 7.1 包月套餐

- 用户权益记录套餐版本、起止时间和状态。
- 每个周周期创建独立 `quota_bucket`，记录 `granted`、`reserved` 和 `consumed`。
- 周额度通过创建新桶刷新，不覆盖历史用量。
- 必须以数据库唯一约束保证同一权益、同一周期只创建一个额度桶；Redis 锁只用于降低并发，
  不能代替数据库幂等约束。

周周期究竟采用自然周还是从权益生效时间起每七天计算，在实现前仍需确定。

### 7.2 用量付费

- 管理员通过受权限保护的充值接口增加余额。
- 充值事务同时执行余额更新和充值账本插入。
- 扣费必须采用原子条件更新或带版本更新，不能先查询余额再单独扣减。
- 套餐和钱包同时存在时默认先使用套餐；套餐耗尽后是否自动扣钱包必须由用户显式开启。

### 7.3 模型请求结算

```text
Model Gateway
  → Billing.reserve(request_id, model, maximum_cost)
  → 调用模型供应商并流式返回
  → 获得供应商最终 TokenUsage
  → Billing.settle(request_id, usage, pricing_version)
  → 释放多余预占并写入用量、余额/额度流水

调用未开始或明确未产生费用
  → Billing.release(request_id)

供应商是否计费无法确认
  → 标记 UNKNOWN/PENDING_RECONCILIATION，不得直接当作免费请求
```

余额检查和扣减不能拆成“先 Feign 查询、再 Feign 扣款”两个无关联操作。`reserve` 必须是
Billing Service 内部的原子业务动作。

每个请求至少具有以下唯一标识：

- `client_request_id`：客户端/Agent 的稳定幂等键。
- `request_id`：Model Gateway 的模型请求 ID。
- `reservation_id`：Billing 的预占 ID。
- `usage_id`：权威用量事件 ID。
- `ledger_reference_id`：账本业务引用。

结算使用请求创建时锁定的价格版本，不能读取后来被管理员修改的当前价格。

## 8. Redis 与 MySQL 的边界

Redis 负责：

- Session、验证码和短期撤销状态。
- IP、用户、模型维度的限流。
- 活跃并发计数。
- 短期幂等结果和模型配置缓存。
- 额度热点快照与短期预占加速。
- 必要的分布式锁。

MySQL 负责：

- 用户、角色和耐久会话审计。
- 套餐版本、权益和周额度周期。
- 钱包余额、预占、用量和不可变账本。
- 模型目录、价格版本和配置发布记录。

Redis 不是金额和额度的最终事实来源。Redis 与 MySQL 出现差异时，以可审计的 MySQL 记录和
对账流程恢复。首版不引入跨服务分布式事务；通过状态机、唯一约束、幂等接口、补偿任务和
定时对账闭环。

## 9. 本地记录与云端记录

Desktop 领域模型预留以下类型：

- `LOCAL_CHAT`：聊天记录保存在本地。
- `CLOUD_CHAT`：登录后可选的云端聊天，后续需要跨设备同步时再实现。
- `LOCAL_TASK`：项目任务、工具日志、文件变更、审批和 Git Changes 始终保存在本地。

未登录时仍允许创建本地聊天和本地任务，并可使用 `LOCAL_BYOK`；只有 `CLOUD_MANAGED` 不可用。
登录不会自动上传已有会话，也不会自动把当前模型来源从 BYOK 切换为云端套餐。

本地保存任务不代表模型流量一定不经过云端：`LOCAL_TASK` 选择 `CLOUD_MANAGED` 模型时，
请求仍通过 Model Gateway，但云端只持久化计费与诊断所需的最小元数据，不保存本地任务历史、
工具日志或项目文件。选择 `LOCAL_BYOK` 时继续由 Agent 直接调用供应商。

是否实现 `CLOUD_CHAT` 属于独立产品能力，不是登录、套餐和模型计费的前置条件。真正实现时再
增加 `conversation-service`，并单独设计同步、删除、附件、离线缓存和隐私边界。

## 10. 主要技术债与风险

- Redis 预占快照与 MySQL 账本的一致性和崩溃恢复。
- `reserve`、`settle`、`release`、充值和周刷新全链路幂等。
- SSE 断开、超时或供应商未返回最终 Usage 时的待对账状态。
- 缓存中的模型配置与已发布配置版本一致性。
- 模型输入、输出、推理、缓存读写及额外工具费用的统一计量定义。
- Nacos、Redis、Billing Service 或上游供应商不可用时的超时、熔断和降级。
- Cloud Gateway、Feign 和模型调用之间的 Trace ID 传递。
- 管理员接口的 RBAC、审计和高风险操作二次确认。
- Provider Key、JWT 签名密钥和内部服务凭据的轮换与隔离。
- Desktop 凭据安全存储、外部控制台 URL 白名单和浏览器独立登录会话边界。
- 云端模式下提示内容会经过 LUMORA 服务，需要明确隐私说明和日志脱敏策略。

## 11. 实施顺序

1. 工程基线（已完成）：建立 Maven 多模块、统一 React 前端、Nacos 与 Cloud Gateway 骨架。
2. 实现 User Service、登录会话、角色和三类 API 边界。
3. 实现 Billing Service 的套餐、周额度、钱包、账本和幂等状态机。
4. 实现 Model Catalog Service 和模型配置发布版本。
5. 实现 Model Gateway 的预占、WebClient 流式代理、Usage 结算和故障补偿。
6. 将 `frontend` 接入真实 API，完善网页用户控制台与管理员页面，在网页端承载购买、续费、充值和套餐管理。
7. 将 Desktop 接入可选登录、只读套餐/额度/用量、外部控制台入口和
   `LOCAL_BYOK/CLOUD_MANAGED` 模型来源切换。
8. 完成负载与故障验证后，再评估消息队列、独立 Payment Service 或 Cloud Chat。
