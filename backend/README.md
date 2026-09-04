# Lumora Cloud Backend

Java 21、Spring Boot 3.5、Spring Cloud 2025 和 Spring Cloud Alibaba 2025 的 Maven 多模块工程。

各业务微服务已经统一采用按受众划分的 Controller、按领域划分的 DTO/VO/Entity/Mapper，以及
`IService + ServiceImpl` 业务层。服务专用辅助类留在各自的 `utils`、`support`、`cache` 等包，
不把业务工具误放进全局公共模块。纯依赖注入使用 Lombok `@RequiredArgsConstructor` 与 `private final`；
带初始化逻辑或参数级注入注解的构造器保留显式实现。新增服务与目录归属以
[`后端包结构约定`](../docs/backend-package-conventions.md) 为准。

```text
backend/
├── cloud-common/               # 稳定公共能力，不共享实体或 Mapper
├── cloud-api/                  # Feign Client 与跨服务 DTO 契约
├── cloud-gateway/              # API 入口、鉴权上下文与粗粒度限流
├── user-service/               # 登录、设备会话与角色
├── billing-service/            # 套餐、周额度、预占结算与账本
├── model-catalog-service/      # 模型、价格版本与发布配置
└── model-gateway-service/      # 流式代理、权威 Usage 与结算编排
```

当前已经完成 User Service 的注册、登录、Refresh Token 轮换、退出、用户信息、角色与审计，以及
Gateway 的 JWT 校验、会话撤销检查和可信身份头注入。Billing Service 已完成套餐及版本、管理员
发放订阅、购买订单、开发环境测试支付、购买后订阅发放/顺延、七天额度桶、额度预占/结算/释放、
超额待对账、过期预占自动释放、用量记录和不可变账本，并已加入多币种钱包、充值订单与不可变余额流水。
Model Catalog Service 也已完成供应商管理、模型草稿、发布版本、启停、用户可见目录与内部解析接口。
Model Gateway 已完成 Chat Completions、OpenAI Responses 与 Anthropic Messages 调用闭环，支持
普通 JSON 与 SSE、分布式并发限制、请求幂等、额度预占、权威 Usage 结算、失败补偿和 Redis 脱敏诊断。
Cloud Gateway 与各业务服务已接入 Nacos 持久化的 Sentinel HTTP 流控；Model Gateway、Billing 的
OpenFeign 控制调用使用异常比例熔断和 fail-closed Fallback，且不会伪造配置或账务成功结果。上游模型
路由继续按动态路由 ID 独立熔断，避免一个供应商账号故障拖垮整个逻辑模型。
钱包套餐支付已经完成，真实第三方支付渠道留在后续迭代。

Billing 对外接口按权限分为：

- `/api/app/billing/plans|overview`：已登录用户及 Desktop 的套餐与当前额度查询。
- `/api/app/billing/history?scope=CURRENT_PERIOD|CURRENT_MONTH`：返回所选范围的完整用量/额度汇总，以及各自最近 100 条明细。
- `/api/app/billing/usage-chart?range=WEEK|MONTH&anchor=YYYY-MM-DD`：按 `Asia/Shanghai` 自然周或自然月返回逐日 Token 汇总。
- `/api/app/billing/orders/**`：网页用户控制台幂等创建订单、查询/取消订单、钱包支付和开发环境测试支付。
- `/api/app/billing/wallet/**`：查询余额/流水、创建充值订单、MOCK 到账和取消充值订单。
- `/api/app/billing/payment-capabilities`：返回当前环境实际启用的支付方式；生产环境不得启用 `MOCK`。
- `/api/admin/billing/plans/**`：管理员创建套餐、发布新的价格/周额度版本并查看历史版本。
- `/api/admin/billing/subscriptions`、`/subscriptions/grant`：查询最近订阅并使用幂等引用发放套餐。
- `/api/admin/billing/orders`：管理员只读查看最近 100 条购买订单。
- `/api/admin/billing/statistics`：精确统计有效订阅、待支付订单、本月分币种收入和今日权威 Usage。
- `/api/admin/billing/wallets/**`：查询用户钱包并使用幂等调整单执行带原因的余额增减。
- `/api/admin/users/**`：查询用户、维护角色/状态和撤销会话，不跨服务复制用户数据。
- `/api/admin/users/statistics`：精确统计用户状态、本月新增用户和未过期活跃会话。
- `/api/admin/model-gateway/diagnostics`：读取 Redis 中最近 24 小时的分桶统计和最多 100 条脱敏请求明细。
- `/internal/billing/reservations/**`：只允许 Model Gateway 使用内部服务凭据调用的预占、结算、释放
  与待对账接口；公共 DTO 与 Feign Client 位于 `cloud-api`。

Model Catalog 对外接口按权限分为：

- `/api/app/catalog/models`：登录用户与 Desktop 可选择的已发布模型，不返回上游地址或密钥引用。
- `/api/admin/catalog/providers|models/**`：管理员维护供应商、加密凭据、草稿、放弃草稿、发布版本和启停状态。
- `/api/admin/catalog/statistics`：精确统计供应商、模型定义、当前用户可见模型、草稿和历史版本。
- `/internal/catalog/models/**`：只允许 Model Gateway 解析完整上游路由与当次锁定的计价版本。
- `/internal/catalog/credentials/**`：只允许 Model Gateway 短时读取解密后的托管凭据，响应禁止缓存。

模型计价使用“未缓存输入、缓存命中输入、输出（含推理）”三个基础维度。缓存创建输入是可选维度，
未配置时按 0 处理；推理 Token 仍保留在 Usage 明细中，但与 Desktop 一致归入供应商输出总量并按输出
费率结算。上游成本和套餐额度分别拥有一套可选的“默认值 + 多条覆盖规则”：两边可以使用不同的 IANA
时区、星期和时间段，也可以只开启其中一边；每套规则内部不允许重叠，没有命中时分别使用默认成本和
默认额度倍率。成本规则只改变平台采购成本，额度规则只改变用户套餐 Credits。

用户套餐统一以平台额度结算：各 Token 维度先按模型的每百万额度费率折算并求和，再与单次最低额度
取较大值，最后乘请求开始时命中的时段倍率。请求时刻、倍率和规则名称随 Billing 预占持久化，保证
最大额度预占、权威 Usage 结算、流式跨时段调用和后续对账始终使用同一快照。

Model Gateway 对外提供：

- `POST /api/app/model/v1/chat/completions`：登录用户使用 Lumora 套餐调用已发布模型。
- `POST /api/app/model/v1/responses`：以 OpenAI Responses 协议调用已发布模型。
- `POST /api/app/model/v1/messages`：以 Anthropic Messages 协议调用已发布模型。
- 客户端必须为每次逻辑调用提供稳定的 `X-Lumora-Client-Request-Id`；重试复用同一个值。
- Model Gateway 使用请求租约阻止同一请求并发访问供应商，使用 Redis Lua 信号量限制用户、可选逻辑模型、
  供应商账号和单路由并发，并对账号/路由 RPM、TPM 做分布式准入。
- 请求先解析发布版本并预占最大额度，供应商终态 Usage 用于实际结算；拒绝类错误释放额度，未知结果
  进入待对账并由恢复任务幂等重试。
- 新供应商的 API Key 由 Model Catalog 使用 AES-256-GCM 加密保存，数据库和管理端只暴露掩码、指纹与
  审计元数据；Model Gateway 通过内部接口按引用读取。旧 `credential_reference` 环境变量方式仅作为
  已有 Provider 的兼容回退。

模型目录是读多写少链路：普通请求读取 Redis 发布快照，MySQL 保存最终事实；管理端写入使用事务、
模型行锁和 revision 乐观并发控制。数据库唯一约束保证每个模型最多一个草稿和一个已发布版本，
发布事务提交后才递增缓存 generation 并删除旧快照，避免读取未提交或过期配置。

## 数据库迁移约定

每个拥有数据库的微服务负责维护自己的表结构，并使用以下 Flyway 目录：

```text
user-service/src/main/resources/db/migration/           # lumora_user
billing-service/src/main/resources/db/migration/        # lumora_billing
model-catalog-service/src/main/resources/db/migration/  # lumora_model_catalog
```

脚本采用 `V1__init_schema.sql`、`V2__add_xxx.sql` 等版本化命名。Gateway、
`model-gateway-service` 和公共模块不拥有数据库，不得集中维护或直接修改其他服务的数据表。
`deploy/mysql/init/` 只负责空数据目录第一次启动时创建数据库和授权，不用于后续表结构升级。

Billing 的周额度周期从订阅生效时刻开始连续计算，每七天一个周期；最后一个周期在订阅结束时截断，
不按自然周划分。额度金额统一使用 `DECIMAL(20,6)`，MySQL 是最终事实来源。

购买订单使用 `(user_id, idempotency_key)` 唯一约束避免网络重试重复下单，并在下单时冻结套餐版本、
名称、金额和币种。支付确认锁定订单行，在同一事务内记录支付尝试、创建一次 `PURCHASE` 订阅并把订单
推进到 `FULFILLED`；同一用户并发续费由 Billing Account 行锁串行化，后续订阅从现有最晚结束时间开始。
待支付订单默认 30 分钟过期。钱包支付在同一事务内完成条件扣款、余额流水、支付记录、订阅发放和订单完成。
当前 `MOCK` 方式只供本地联调，真实渠道后续通过支付适配器与服务端回调
复用同一确认状态机，不能由浏览器直接声明支付成功。

订单事务提交后，Billing Service 会向 RabbitMQ 持久化延迟队列发送一条带剩余 TTL 的过期消息；消息
到期后通过死信交换机进入消费队列，消费者只对“仍为待支付且确已到期”的订单执行条件更新。消费失败
由 Spring AMQP 最多重试 3 次，重试耗尽后进入 `lumora.billing.order.expiry.failed.q`，供人工检查和补偿。
原有 MySQL 定时扫描继续保留为权威兜底，因此 RabbitMQ 临时不可用或消息丢失不会留下永久待支付订单，
重复消息也不会把已支付或已取消订单改成过期状态。充值订单采用相同的 RabbitMQ 及时触发与 MySQL 扫描兜底策略。

管理端运营统计继续遵循数据所有权：User、Billing 与 Model Catalog 分别聚合自己的 MySQL 数据，前端
并行读取并处理局部失败，不增加跨库查询。日/月统计边界统一采用 `Asia/Shanghai`；订单收入按币种
分组，不能把 CNY、USD 等金额直接相加。面向全局时间范围的统计列具有独立索引，避免复用仅适合
单用户历史查询的联合索引造成全表扫描。

用户计费查询将“汇总口径”和“明细展示”分离。当前额度周期最多七天，额度汇总直接读取权威
`quota_bucket`，用量通过 `(user_id, occurred_at)` 索引做有界聚合；本月汇总和周/月 Token 图表读取
`billing_quota_daily_summary`、`billing_usage_daily_summary`，最多扫描一个自然月的日记录。两张日汇总表
随原始流水/Usage 在同一事务内幂等更新，Flyway V8 会为已有记录完成一次回填；原始账本和 Usage 仍是
最终可审计事实。两种范围的明细都只读取最近 100 条，前端不得用这 100 条重新计算汇总。

网关诊断把每次调用累计到 Redis 的 5 分钟时间桶。最近 24 小时总请求、成功/失败/取消、运行中、
平均耗时和近似 P95 固定读取约 289 个桶，不受明细数量限制。完成态明细只写入一个最多 100 条的 Redis
List，空闲 24 小时后整体过期，不再创建逐请求 Key 或永久时间索引；运行中的请求只计入上方分桶统计。
成功率口径为 `成功 / (成功 + 失败)`，运行中和客户端取消不进入分母。Model Gateway 启动后会异步清理
旧版 `diagnostics:record:*` 与 `diagnostics:index`，这些诊断数据不属于业务审计事实。

## 开发环境配置

开发环境使用 `application-dev.yml`：Gateway 和各业务服务在本机启动，MySQL、Redis、Nacos、
Sentinel 和 RabbitMQ 连接虚拟机 `192.168.100.132`。本地 YAML 只保留 Nacos 连接引导配置，公共参数
和服务级参数从 Nacos Config 的 `LUMORA_CLOUD` Group 加载；需要发布的 Data ID 和完整内容位于
[`deploy/nacos-config`](../deploy/nacos-config/README.md)。启动服务时设置 `SPRING_PROFILES_ACTIVE=dev`。

`backend/.env.example` 是变量模板。各服务会可选读取当前工作目录的 `.env` 和上一级目录的 `.env`，
因此本机从 `backend/` 或单个模块目录启动都可以使用 `backend/.env`；真实文件已被 Git 忽略，不能
提交。Nacos 中的配置只引用这些变量，不保存真实密码。也可以在 IntelliJ IDEA Run Configuration
中直接设置同名环境变量覆盖它。未来把 Java 服务
部署到虚拟机时，再使用 `/etc/lumora-cloud/lumora-cloud.env`，由 systemd 的 `EnvironmentFile` 加载。

Sentinel 的五组流控/熔断 JSON 也需要发布到同一 Group，Dashboard 修改不作为持久化来源。完整 Data ID
清单见 [`deploy/nacos-config`](../deploy/nacos-config/README.md)，本地 Mock Provider 与低强度压测步骤见
[`tests/load`](../tests/load/README.md)。

`LUMORA_CREDENTIAL_MASTER_KEY` 是 Model Catalog 的平台级凭据主密钥，必须是随机 32 字节的 Base64，
需要稳定保存并安全备份。它只用于加密供应商 API Key，不写入 Nacos；未经密钥迁移不能直接更换。

## 认证边界

- Web 和 Desktop 都调用 `/api/app/auth/**`，但各自创建独立设备会话。
- Access Token 为短期 JWT；Web Refresh Token 只写入 HttpOnly Cookie，Desktop 接入时通过响应体交给
  Electron Main 的系统安全存储。
- Refresh Token 仅以 SHA-256 哈希入库，每次刷新都会轮换；重复使用旧 Token 会撤销整个设备会话。
- Gateway 会删除客户端提供的 `X-Lumora-*` 身份头，验签后重新注入用户、会话、设备、角色和请求 ID。
- OpenFeign 只透传 Access Token、请求 ID 和经过内部密钥确认的用户上下文，不透传 Cookie 或
  Refresh Token。

Compose 会在全新 MySQL 数据目录首次启动时创建以下数据库，并授权给专用的 `lumora` 应用账号：

- `lumora_user`
- `lumora_billing`
- `lumora_model_catalog`

RabbitMQ 使用专用 Virtual Host `/lumora`。Nacos 3 首次启动时由 Compose 初始化管理员 `nacos`，
本地环境变量 `LUMORA_NACOS_PASSWORD` 应与部署端的 `NACOS_ADMIN_PASSWORD` 保持一致。完整启动方式见
[`deploy/README.md`](../deploy/README.md)。
