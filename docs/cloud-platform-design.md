# LUMORA 云端平台设计

## 1. 文档状态

最后同步：2026-09-02。

本文记录 LUMORA 云端能力的目标设计。当前 `LUMORA_CLOUD` 已建立后端 Maven 多模块、统一
React 前端和本地部署配置。User Service、Gateway 身份链路以及网页注册、登录、刷新和退出已经完成；
Billing Service 的套餐、订阅周额度和模型请求额度状态机已经实现；Model Catalog Service 的供应商、
模型草稿、发布版本、启停、版本历史和缓存读取也已实现；管理端已经接入上述模型目录能力。Model
Gateway 的统一内部协议、三种上游协议适配、额度预占、并发控制、Usage 结算与失败补偿首版已经完成；套餐订单、开发环境
测试支付和购买订阅发放闭环也已实现；管理总览已经接入 User、Billing 与 Model Catalog 各自维护的
真实运营统计。用户/角色/会话管理、网关脱敏诊断、多币种钱包、MOCK 充值、管理员余额调整和钱包购买套餐
也已经实现；Desktop 可选登录、套餐只读信息、套餐模型同步和官方模型调用首版已经接入。真实第三方支付渠道仍待实现。工程目录、模块边界和默认端口参见同目录下的
`architecture.md`。

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

当前阶段不接入第三方支付，仅在显式开启的开发环境提供不会产生真实扣款的 MOCK 支付。钱包充值后续
由管理员手动发放，但仍必须经过幂等接口和不可变账本，
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

数据库结构同样遵循服务所有权：`user-service`、`billing-service` 和 `model-catalog-service` 分别
维护 `lumora_user`、`lumora_billing` 和 `lumora_model_catalog`。建表及后续结构变更放在所属服务的
`src/main/resources/db/migration/` 中，以 Flyway 的 `V<版本>__<说明>.sql` 脚本随服务版本演进。
当前没有持久化职责的 Gateway 和 `model-gateway-service` 不创建业务表，也不维护其他服务的迁移。

`deploy/mysql/init/` 是部署引导目录，仅在 MySQL 数据目录为空时创建数据库并配置基础权限；它不是
业务表版本管理机制。仓库根目录若以后增加 `database/`，只用于数据库说明、结构快照和人工运维
脚本，自动迁移仍以各服务的 Flyway 目录为准。

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

当前认证实现采用以下边界：

```text
浏览器 / Desktop ──Access Token──→ Gateway 验签与撤销检查
                                     └──可信 X-Lumora-* 身份头──→ 业务服务

业务服务 A ──OpenFeign：Access Token + Request ID + 可信用户上下文──→ 业务服务 B
```

- Access Token 是 15 分钟的 HS256 JWT，包含用户、设备会话、设备、客户端类型和角色声明。
- Refresh Token 对应最长 30 天的设备会话，只保存 SHA-256 哈希并执行一次性轮换；旧 Token 重放会
  撤销整条会话链。网页端通过 HttpOnly、SameSite Cookie 保存，Desktop 后续由 Electron Main 写入
  操作系统受保护存储。
- Gateway 先删除客户端自行提交的 `X-Lumora-*` 身份头，再根据已验签 JWT 注入可信上下文；退出会话
  通过 Redis 撤销标记立即拦截尚未自然过期的 Access Token。
- OpenFeign 不复制全部请求头，只白名单传递 `Authorization`、Request ID 和内部确认过的用户上下文；
  Cookie 与 Refresh Token 不进入服务间调用。

Model Gateway 不应通过 OpenFeign 在每次模型请求中同步查询完整用户资料。Access Token
应支持本地验签；需要强制注销或封禁时再结合 Redis Session/撤销状态判断。

Desktop 云端凭据应由 Electron Main 持有并写入操作系统受保护存储，不进入 Renderer 的
`localStorage`。Renderer 只通过白名单 IPC 获取脱敏后的账户状态；Python Agent 仅在当前
`CLOUD_MANAGED` 请求中接收必要的短期 Access Token，不长期保存 Refresh Token。

### 5.3 Billing Service

- 套餐、套餐版本、购买订单、支付确认、用户权益和周额度桶。
- 钱包、管理员手动充值和余额流水。
- 模型请求额度预占、实际结算和多余预占释放。
- 用量记录、价格快照、账单查询和定时对账。

当前实现已覆盖套餐及不可变价格/周额度版本、幂等购买订单、开发环境测试支付、钱包余额支付、购买订阅发放与续费
顺延、管理端用户查找、管理员幂等发放订阅、周额度桶、原子预占/结算/释放、超额待对账、过期预占
自动释放、用量记录和不可变额度账本；钱包支持多币种账户、MOCK 充值订单、不可变余额流水、管理员幂等
正负调整及同币种套餐订单支付。管理端可以查看套餐历史版本、最近订阅、订单及用户钱包。真实支付渠道
仍属于后续阶段，浏览器不能直接声明真实到账。

钱包与用量扣减首版不拆成两个微服务。一次结算通常需要同时改变额度、余额、预占状态和
账本，过早拆分会立即引入跨服务金额事务。未来接入真实支付渠道时，再评估独立
`payment-service`。

### 5.4 Model Catalog Service

- 模型供应商、Provider Key 和上游地址。
- 模型目录、协议类型、上下文窗口、最大输出和能力开关。
- 成本价、销售价、价格版本和套餐可见范围。
- 模型配置的草稿、发布、停用和版本查询。

Provider Key 只保存在服务端 Secret Manager 或加密凭据表中，不能返回 Desktop、浏览器或
Python Agent。管理端只允许写入和轮换，后续查询仅返回掩码、指纹、版本和轮换时间。

当前实现由 Model Catalog 使用 AES-256-GCM 和随机 nonce 加密 Provider Key，平台级 32 字节主密钥
通过 `LUMORA_CREDENTIAL_MASTER_KEY` 注入且不进入 Nacos。数据库中的 `credential_reference` 只定位
密文记录；Model Gateway 使用内部服务身份短时读取解密结果，内部响应设置 `no-store`。凭据创建、
轮换和从旧环境变量引用迁移都会写入不含明文的审计记录。模型配置
采用“一个可编辑草稿 + 不可变发布历史”：发布时在同一事务中锁定模型主记录、归档旧发布版本并发布
新版本；revision 用于识别管理端过期写入，生成列唯一索引从数据库层保证每个模型最多一个草稿和一个
发布版本。管理员可以关闭编辑并保留草稿，也可以显式放弃草稿；放弃操作使用草稿 revision 做并发校验，
删除草稿及其时段规则但不修改现有线上版本。如果模型从未发布且只有初始草稿，放弃时同时删除模型定义，
避免留下无版本空壳。普通用户与 Model Gateway 读取 Redis 发布快照，MySQL 是最终事实；写事务提交后才
更新缓存 generation 并驱逐旧快照。

模型定义代表 Desktop 可见的“逻辑模型”，模型版本下再保存一组不可变的上游路由快照。一个逻辑模型
可以绑定多个供应商账号；每个供应商账号独立保存 Base URL、协议、加密 API Key 与账号级并发/RPM/TPM，
每条路由再保存上游模型 ID、优先级、权重、路由级并发/RPM/TPM、故障转移、熔断和独立采购成本。
同一个供应商账号创建多把 Key 不视为新增账号容量；需要扩容时应创建供应商实际授予了独立限额的账号，
再把这些账号作为同一逻辑模型的不同路由。模型草稿初次创建时自动生成默认路由；从线上版本创建草稿时
完整复制路由和各路由成本时段，发布前至少保留一条有效路由。

模型价格版本采用三个基础 Token 维度：未缓存输入、缓存命中输入、输出（包含推理 Token）。缓存创建
输入是可选维度，只在供应商明确返回并收费时配置；未配置统一归一为 0。`reasoning_tokens` 继续作为
Usage 可观测明细保存，但不能配置独立单价，结算时与普通输出一起按输出费率计算。

模型配置中的供应商成本和用户套餐额度是两套不同量纲：

- 供应商成本使用模型版本选择的 `cost_currency`，费率单位为“所选币种 / 百万 Token”，例如
  `USD / 百万 Token` 或 `CNY / 百万 Token`。时段成本覆盖必须沿用同一币种，一个模型价格版本内不允许
  混合币种，也不在请求结算时自动换汇。
- 用户套餐额度费率单位固定为 `Credits / 百万 Token`，单次最低额度单位为 `Credits`。Credit 不是金额、
  不可提现；运营定价锚点统一为 `1 Credit = ¥0.05` 名义价值，即 `20 Credits = ¥1`。

时段计价不再使用一套混合规则，而是拆成两套完全独立、均可选的覆盖策略：

- 上游成本时段策略位于“默认上游成本”配置下，保存自己的 IANA 时区和最多 32 条成本规则。每条规则
  包含名称、星期集合、开始/结束时间，以及该时段完整的供应商成本费率；没有命中规则时使用模型版本的
  默认上游成本。它只服务于平台采购成本核算，不改变用户套餐 Credits。
- 套餐额度时段策略位于“默认套餐额度费率”配置下，保存自己的 IANA 时区、未命中规则的默认倍率和
  最多 32 条额度规则。每条规则包含名称、星期集合、开始/结束时间和额度倍率；没有命中规则时使用默认
  倍率。它只影响用户套餐额度扣减，不改变供应商成本。

两套策略可以单独开启、单独关闭，也可以使用完全不同的星期、时间段和时区；不得假设同一次请求在
两边命中同名或同一条规则。每套策略内部的规则不能重叠，但成本规则与额度规则之间允许重叠。同一天
可以配置多段，例如工作日 `09:00–12:00` 和 `14:00–18:00`；开始时间晚于结束时间表示从所选开始日
跨到次日，例如星期五 `22:00–06:00` 会覆盖星期六凌晨。开始时间包含、结束时间不包含。

套餐的统一结算维度是平台额度，不是原始 Token 总量，也不是供应商采购金额。一次请求先按模型发布
版本中的用户侧额度费率折算基础额度：

```text
基础额度 =
  未缓存输入 Token / 1,000,000 × 未缓存输入额度费率
  + 缓存命中 Token / 1,000,000 × 缓存命中额度费率
  + 缓存创建 Token / 1,000,000 × 缓存创建额度费率
  +（普通输出 Token + 推理 Token）/ 1,000,000 × 输出额度费率

最终扣除额度 = max(基础额度, 单次最低额度) × 请求开始时命中的时段倍率
```

计算全程使用十进制定点数，最后以 `DECIMAL(20,6)` 向上取整一次。当前套餐扣减会在请求开始时锁定
额度策略的命中结果；上游成本配置与独立时段策略随模型发布版本进入内部快照，为后续供应商实际成本
落账、利润分析和账单对账提供不可变计价依据。

### 5.5 Model Gateway Service

- 校验云端模型、套餐权益和调用权限。
- 执行模型、用户和设备级限流与并发控制。
- 通过 OpenFeign 调用 Billing Service 的 `reserve`、`settle` 和 `release`。
- 使用 WebClient 调用供应商 API 并处理 SSE/流式响应。
- 解析供应商返回的权威 TokenUsage，并生成唯一用量事件。
- 缓存已发布模型配置，不在每次请求中同步调用 Model Catalog Service。

Model Gateway 在统一 Cloud Base URL 下提供 `/chat/completions`、`/responses` 和 `/messages`
三个入口，分别兼容 OpenAI Chat Completions、OpenAI Responses 与 Anthropic Messages，均支持普通
JSON 和 SSE。客户端必须携带稳定的 `X-Lumora-Client-Request-Id`，重试时复用同一个值。一次请求
按以下状态流转：

```text
Gateway 可信身份 → 请求租约 → Catalog 发布快照 → Billing 最大额度预占
  → 路由选取/四层并发与 RPM/TPM 准入 → Provider WebClient → 权威 Usage 解析
  → Billing 实际结算 → 释放租约与并发许可
```

请求租约和并发许可均保存在 Redis，使用 Lua 完成原子获取、续期和释放，支持多实例部署。Billing
预占使用确定性请求 ID；如果同一请求已预占过，Model Gateway 不会再次调用供应商。供应商明确拒绝
时释放额度；超时、连接中断、流提前结束或缺少终态 Usage 时标记待对账。Billing 暂时不可用时，
恢复命令写入 Redis，由后台任务继续执行幂等结算、释放或待对账操作。

上游选路先按 `priority` 从小到大分组，同一优先级内按 `weight` 做加权随机；账号和路由的容量值为空
表示平台不额外限制。Redis 并发控制依次执行用户并发、可选逻辑模型总并发、供应商账号总并发和单路由
并发；账号级 key 对该账号的所有模型路由共享，路由级 key 只作用于当前绑定。RPM/TPM 使用分布式分钟
窗口，TPM 在调用前按请求内容体量和允许的最大输出 Token 做保守准入，避免大量长请求同时击穿上游。

每条路由以 `lumora:model-route:{routeId}` 动态注册为 Sentinel Resource，按异常比例熔断。路由并发已满、
RPM/TPM 已满、熔断、鉴权失败、429、超时、连接失败或 5xx 时，如果当前路由允许故障转移且响应尚未
交给客户端，Gateway 会尝试下一候选路由；400、413、422 等请求自身错误不跨路由重试。SSE 一旦开始
返回后不再切换路由，避免重复内容和重复计费。管理端诊断记录实际命中的账号和路由，但仍不记录 Prompt、
响应正文或密钥。

服务间保护与动态路由保护相互独立：Cloud Gateway 和业务服务的 HTTP URL 使用 Nacos `flow` JSON
执行入口 QPS 流控；Model Gateway → Catalog/Billing、Billing → Catalog 的 OpenFeign 方法使用 Nacos
`degrade` JSON 执行异常比例熔断。Feign Fallback 只负责把 Sentinel 阻断转换为明确的 503 领域错误，
原始下游 HTTP 业务错误继续按原语义处理，绝不返回伪造的模型配置、额度预占、结算或释放结果。Billing
预占调用被保护时在调用上游前失败；已产生的结算/释放命令仍由 Redis 恢复队列幂等重试。

Sentinel 的全局 `DegradeRuleManager` 同时承载 Nacos 静态 Feign 规则和按路由动态生成的规则。Nacos
刷新可能替换内存规则集合，因此 Model Gateway 在路由进入前校验并重新合并当前动态规则，确保配置
中心刷新不会无意关闭上游路由熔断。静态规则以 Nacos 为事实来源，Dashboard 修改仅用于临时诊断。

Cloud 支持 `OPENAI_COMPATIBLE`、`RESPONSES` 和 `ANTHROPIC` Provider。管理端创建 Provider 时直接
提交 API Key；API Key 只以密文进入凭据表，不进入模型版本、Redis 发布快照、Nacos、日志或下游响应。
旧环境变量引用仅作为已有 Provider 的兼容回退。凭据轮换保持引用稳定，不要求重新发布模型；Model
Gateway 遇到上游 401/403 会清除短时凭据缓存并重新读取后重试一次。模型计价至少配置
一种正向 Token 单价或最小请求额度，预占上限按上下文窗口、最大输出和最高相关单价保守计算，实际
扣减以供应商终态 Usage 为准。Model Gateway 在请求开始时只使用套餐额度策略自己的时区匹配一次额度
规则，并把 `pricing_at`、额度倍率和额度规则名称作为预占快照写入 Billing；预占和最终结算使用同一
倍率。流式响应即使跨越边界也不重新匹配，退款、冲正和待对账都以原预占快照为准。上游成本策略拥有
独立时间表，不参与套餐额度预占或扣减。

OpenFeign 与 WebClient 不互相替代：OpenFeign 用于内部短时控制调用；WebClient 用于连接
模型供应商并持续消费流式 HTTP 响应。

### 5.6 Python Agent 与流式协议边界

云端首版不设置 `agent-service`。Agent 循环、上下文管理、工具调用、压缩和本地 Token 聚合仍由
Desktop 内的 Python Agent 执行；Spring Cloud Gateway 只把模型请求路由到 Model Gateway Service。

Model Gateway 对 Desktop 套餐调用暴露版本化的 LUMORA 内部协议，而不是把任一供应商协议当作平台
协议。Python Agent 仅新增独立的 `lumora-cloud` Provider；原有 `chat-completions`、`responses` 和
`anthropic` Provider、用户 BYOK 配置及直连行为保持不变。只有用户显式选择 `CLOUD_MANAGED` 时才进入：

```text
Local Core / Python Agent
  → LUMORA Internal Protocol v1
  → Electron Main loopback 代理（注入 Cloud Access Token）
  → POST /api/app/model/v1/invoke
  → Model Gateway 按已发布模型快照选择上游协议
  → Chat Completions / Responses / Anthropic Messages
```

内部请求统一表达消息文本/图片块、工具定义、Tool Call/Tool Result、推理强度、最大输出、供应商托管
Web Search 开关和可选的
`providerState`，不携带供应商协议类型、上游模型 ID、Base URL 或 API Key。内部响应统一表达正文、
推理、工具调用和标准化 TokenUsage；流式响应使用版本化事件 `content_delta`、`reasoning_delta`、
`tool_call_delta`、`web_search_started`、`web_search_progress`、`web_search_completed`、
`web_search_failed`、`usage`、`completed`。搜索完成事件最多携带 12 条标题和 URL，供 Desktop 的
工作日志与引用界面复用。`protocolVersion` 当前固定为 `1`，未知版本必须明确拒绝，不能
静默按 OpenAI 格式解释。兼容原生协议的旧端点暂时保留给联调和迁移使用，但 Desktop 套餐链路只调用
统一入口。

托管 Web Search 是模型发布版本的显式能力，只在运营人员启用后允许 Desktop 使用。Model Gateway
当前分别将其映射为 Responses 的 `web_search` 工具和 Anthropic Messages 的
`web_search_20250305` 服务端工具；Chat Completions Compatible 没有跨供应商统一规范，因此管理端
不允许为该协议启用此能力。模型的普通工具调用能力、Desktop 本地工具与审批链路不受影响。供应商返回
的 Token Usage 仍按现有费率结算；如果供应商另收按次搜索费用，需要后续增加独立的搜索次数成本与
Credit 费率，不能伪装成 Token 消耗。

`providerState` 只承载多轮工具调用必须保留且无法无损抽象的供应商签名状态，并按协议、逻辑模型和
供应商限定作用域；它不向 Renderer 展示，也不能跨模型复用。管理端配置的供应商协议仅由 Model
Gateway 用于“内部协议 → 上游协议”适配，Desktop 不需要知道当前模型来自哪种上游协议。

供应商流式响应经过 Model Gateway 时不是无法观察的字节转发。Model Gateway 必须解析并转换供应商
事件，再向 Agent 返回统一事件和最终 Usage：

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

当前管理端使用 `/admin/users` 管理账号状态、角色和登录会话，使用 `/admin/wallets` 查询及调整用户钱包，
使用 `/admin/gateway` 查看脱敏调用诊断，使用 `/admin/billing` 管理套餐版本与订阅发放，使用 `/admin/models` 管理模型草稿、能力、
成本、套餐额度费率、发布、启停和历史版本，使用 `/admin/providers` 管理供应商连接及加密 API Key。
套餐页面调用 `/api/admin/billing/**` 和只读用户查询接口，模型页面调用 `/api/admin/catalog/**`；所有写入
继续由后端强制校验管理员角色，模型编辑还使用 revision 乐观并发条件。

`/admin` 运营总览通过 `/api/admin/users/statistics`、`/api/admin/billing/statistics` 和
`/api/admin/catalog/statistics` 并行读取各数据所有者的精确聚合结果，不跨库查询，也不使用最近记录在
浏览器端推算全量数据。统计覆盖用户与有效会话、订单与分币种收入、有效订阅、今日权威 Usage、供应商、
当前可用模型、草稿和历史版本。单个领域暂时不可用时页面保留其他领域结果；日/月边界统一为
`Asia/Shanghai`。没有可靠失败事实记录前，不展示推算出的模型调用失败率。

用户控制台的 `/console`、`/console/usage`、`/console/ledger`、`/console/plans`、`/console/orders` 与 `/console/wallet` 已分别接入实时套餐概览、
周期额度、最近用量、额度流水、已发布套餐目录和购买订单。历史接口当前最多返回最近 100 条记录，因此页面明确
按“近期记录”展示，不将客户端聚合值伪装成完整账期总量；当前额度桶的 granted、reserved、consumed
和 remaining 仍由 Billing Service 返回精确值。订单详情页仅在后端明确声明 MOCK 可用时显示测试支付，
并明确提示其不会真实扣款；钱包页面支持创建并确认 MOCK 充值订单、查看余额与不可变流水，套餐订单可以
选择钱包余额支付。

前端组件体系采用 HeroUI v3 和 Tailwind CSS v4。HeroUI 用于统一按钮、表单、表格、Tabs、Drawer、
Modal 等基础交互和可访问性行为。首版采用 HeroUI 原生默认暗色主题和组件外观，控制台信息架构、
数据图表以及少量 Lumora 标识由项目自身维护，不额外建设高度风格化的视觉组件体系。

Desktop 打开用户控制台时，Renderer 只能向 Electron Main 请求打开预先配置且通过 HTTPS
域名/路径白名单校验的 URL，再由 Main 使用系统默认浏览器打开。不得让 Renderer 直接打开任意
外部地址，也不得把 Desktop Access Token、Refresh Token 或 Session 参数附加到控制台 URL。

## 7. 计费闭环

### 7.1 包月套餐

- 用户权益记录套餐版本、起止时间和状态。
- 每个套餐版本保存不可变的模型编码集合；新版本必须至少选择一个当时已发布且启用的模型。修改套餐
  只创建新版本，不改变已有订阅可使用的模型集合。历史数据迁移为 `ALL_PUBLISHED_LEGACY` 兼容模式，
  新版本统一使用 `SELECTED`。
- 每个周周期创建独立 `quota_bucket`，记录 `granted`、`reserved` 和 `consumed`。
- 周额度通过创建新桶刷新，不覆盖历史用量。
- 必须以数据库唯一约束保证同一权益、同一周期只创建一个额度桶；Redis 锁只用于降低并发，
  不能代替数据库幂等约束。

周周期采用“从权益生效时刻起连续每七天”计算，而不是自然周。这样首个周期不会被自然周边界截短，
也不会因服务器时区变化产生歧义；最后一个周期在权益结束时刻截断。

购买与续费共用订单状态机：

```text
PENDING_PAYMENT ──支付确认──→ FULFILLED ──→ 创建且仅创建一个 PURCHASE 订阅
       ├──用户取消──→ CANCELED
       └──超过截止时间──→ EXPIRED
```

下单时冻结套餐版本、展示名称、金额和币种；`(user_id, idempotency_key)` 唯一约束负责抵御浏览器或网络
重试。支付确认锁定订单行，支付记录、订阅创建与订单完成在一个本地事务中提交；同一订单重复确认返回
已有结果。一个用户同时购买或续费时，以 Billing Account 行锁串行化订阅排期，新订阅从当前及未来
订阅的最晚结束时间开始，不覆盖现有权益。真实支付接入后只能由校验过签名和金额的服务端回调推进
订单，浏览器重定向结果不能作为到账事实。

订单超时采用“RabbitMQ 及时触发 + MySQL 扫描兜底”的双层机制：

1. 订单事务成功提交后，向持久化延迟队列发送以订单截止时间为 TTL 的消息，避免事务回滚后留下无效消息。
2. 消息到期后由死信交换机路由到订单过期消费队列；消费者通过 `order_no + PENDING_PAYMENT + expires_at`
   条件更新状态，确保重复投递、延迟投递和支付并发下仍然幂等，已支付订单不会被误取消。
3. 消费异常最多重试 3 次，耗尽后进入独立失败队列，避免无限重入造成消费风暴。
4. 数据库定时扫描仍是最终兜底。消息发布失败不会回滚已经成功创建的订单，扫描任务最终会收敛状态；
   RabbitMQ 负责降低过期延迟，不作为订单状态的最终事实来源。

充值订单复用同一套 TTL、死信消费、失败队列和 MySQL 扫描兜底机制。当前过期消息不要求事务消息或 Outbox，
是因为数据库扫描能够完整恢复这一内部状态迁移。后续真实支付到账等需要可靠通知其他服务的业务事件，应采用本地事件表（Transactional Outbox）再异步投递，
不能直接复用这种“发送失败后由扫描收敛”的简化策略。

### 7.2 钱包与套餐支付

- 用户可以创建 MOCK 充值订单；确认到账时在一个事务内更新余额、写入不可变流水并完成充值订单。
- 管理员通过受权限保护且带幂等键的调整接口增加或扣减余额，每次调整必须记录原因和操作人。
- 扣费必须采用原子条件更新或带版本更新，不能先查询余额再单独扣减。
- 套餐订单钱包支付会在同一事务中锁定订单和钱包、条件扣减余额、写入支付及流水、发放订阅并完成订单。
- 当前钱包只支付套餐订单；模型调用仍消耗套餐 Credit。套餐耗尽后自动扣钱包属于后续独立产品规则，不能默认开启。

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
- 最近模型调用的脱敏诊断记录与时间索引；只含 Trace、用户 ID、模型/供应商、状态和耗时。

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
- 供应商峰谷成本与真实账单的自动对账，以及网络搜索等额外工具费用的统一计量定义。
- Nacos、Redis、Billing Service 或上游供应商不可用时的超时、熔断和降级。
- Cloud Gateway、Feign 和模型调用之间的 Trace ID 传递。
- 管理员接口的 RBAC、审计和高风险操作二次确认。
- Provider Key、JWT 签名密钥和内部服务凭据的轮换与隔离。
- Desktop 凭据安全存储、外部控制台 URL 白名单和浏览器独立登录会话边界。
- 云端模式下提示内容会经过 LUMORA 服务，需要明确隐私说明和日志脱敏策略。

## 11. 实施顺序

1. 工程基线（已完成）：建立 Maven 多模块、统一 React 前端、Nacos 与 Cloud Gateway 骨架。
2. User Service、登录会话、角色、Gateway 身份传递和网页登录（已完成）。
3. Billing Service 套餐、订单、开发环境测试支付、钱包充值/调整/套餐支付、周额度、账本和幂等状态机（已完成）；真实支付后续单独实现。
4. 实现 Model Catalog Service、模型配置发布版本和对应管理页面（已完成）。
5. 实现 Model Gateway 的预占、WebClient 流式代理、Usage 结算和故障补偿（首版已完成）。
6. 将 `frontend` 的套餐、用量和管理页面接入真实 API，在网页端承载购买、续费、充值和套餐管理
   （登录、会话恢复、管理端套餐版本/订阅发放/订单观测/真实运营统计、用户侧套餐/额度/用量/订单和
   开发测试支付、钱包充值和管理员钱包调整已完成；真实支付待实现）。
7. 将 Desktop 接入可选登录、只读套餐/额度/用量、外部控制台入口、套餐模型同步和
   `LOCAL_BYOK/CLOUD_MANAGED` 模型来源切换（首版已完成）。
8. 完成负载与故障验证后，再评估独立 Payment Service、Transactional Outbox 或 Cloud Chat。

本地负载与故障验证使用仓库内的回环地址 Mock Provider，不发送真实供应商请求。测试覆盖低并发成功
链路、路由分布、明确拒绝释放、429/5xx/超时故障转移、入口流控、动态路由与 Feign 熔断，以及成功
请求对应的 Usage、`SETTLE` 流水、额度桶已用增量和预占归零。详细命令与安全边界见
[`../tests/load/README.md`](../tests/load/README.md)。
