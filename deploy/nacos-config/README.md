# Lumora Nacos Config

本目录中的文件就是开发环境需要发布到 Nacos 配置中心的完整内容。当前约定：

- Namespace：`public`
- Group：`LUMORA_CLOUD`
- 配置格式：服务配置使用 `YAML`，Sentinel 规则使用 `JSON`
- Data ID：与文件名完全一致

需要发布以下六个服务配置 Data ID：

| Data ID | 用途 | 使用方 |
| --- | --- | --- |
| `lumora-common-dev.yaml` | Redis、RabbitMQ、Sentinel、MyBatis 与 JWT 公共非敏感参数 | 全部服务 |
| `lumora-cloud-gateway-dev.yaml` | 网关路由与日志 | Cloud Gateway |
| `lumora-user-service-dev.yaml` | 用户库、Flyway、Token 生命周期与 Cookie | User Service |
| `lumora-billing-service-dev.yaml` | 计费库、订单/钱包测试支付、订单过期与预占释放任务 | Billing Service |
| `lumora-model-catalog-service-dev.yaml` | 模型目录库、Flyway、发布缓存与日志 | Model Catalog Service |
| `lumora-model-gateway-service-dev.yaml` | Provider 连接池、超时、缓存、并发、请求租约、脱敏诊断与恢复任务 | Model Gateway Service |

还需要发布以下 Sentinel 规则 Data ID；每个 Java 服务各有一份流控规则和一份熔断规则：

| Data ID 模式 | 格式 | Rule Type | 用途 |
| --- | --- | --- | --- |
| `<spring.application.name>-sentinel-flow-dev.json` | `JSON` | `flow` | HTTP 入口 QPS 保护 |
| `<spring.application.name>-sentinel-degrade-dev.json` | `JSON` | `degrade` | OpenFeign 内部调用熔断 |

仓库目前包含五个服务对应的十个 JSON Data ID。空数组 `[]` 也需要发布，表示该服务当前没有这一类
静态规则，并保证以后可以直接通过同名 Data ID 增量维护。

在 Nacos 控制台中逐个创建配置，把对应文件正文完整粘贴进去并发布。`# Group` 注释可以保留，
不会影响 YAML 解析。应用端使用非 `optional` 的导入方式，因此缺少任意一个被该服务引用的 Data ID
时会直接启动失败，避免服务在配置不完整的情况下运行。

配置中的 `${LUMORA_*}` 是应用启动时解析的环境变量，不是要求在 Nacos 中创建的变量。真实密码、
JWT 签名密钥、内部服务令牌和 Provider 凭据主密钥继续保存在 `backend/.env`，不得把真实值粘贴进
Nacos。供应商 API Key 由管理端写入 Model Catalog 的加密凭据表。

套餐、余额、价格版本、模型列表和供应商密钥等属于业务数据或敏感数据，不使用 Nacos 管理。

`lumora-billing-service-dev.yaml` 当前为本地联调显式开启 `lumora.billing.payment.mock-enabled`。
它只模拟支付成功，不连接真实渠道；部署到任何可能面向真实用户的环境前必须关闭。修改后需要重新发布
这个 Data ID 并重启 Billing Service，Flyway 会自动执行订单与支付表迁移。

Model Gateway 和 Cloud Gateway 的调用闭环依赖当前目录中最新的
`lumora-model-gateway-service-dev.yaml` 与 `lumora-cloud-gateway-dev.yaml`。修改仓库文件后需要在
Nacos 控制台重新发布同名 Data ID。旧版环境变量 `credential_reference` 仅用于兼容已有 Provider。

Billing Service 的购买订单和充值订单过期消费者依赖 `lumora-common-dev.yaml` 中的 RabbitMQ 发布与消费重试参数。
该文件有修改时需要重新发布同名 Data ID，并重启 Billing Service，使监听容器按新参数重新创建。

## Sentinel 规则说明

`lumora-common-dev.yaml` 会按当前 `spring.application.name` 自动订阅对应的两个 Sentinel JSON Data ID，
并启用 OpenFeign Sentinel。规则加载后，HTTP 流控统一返回 `429 / REQUEST_RATE_LIMITED`，服务熔断返回
`503 / SERVICE_CIRCUIT_OPEN`；Fallback 不会伪造模型配置、额度预占或结算成功结果。

Model Gateway 到真实上游的每条动态路由仍使用 `lumora:model-route:{routeId}` 资源，由路由发布配置中的
开关和 `lumora.model-gateway.route-protection` 参数动态创建异常比例熔断规则。Nacos 刷新静态 Feign
规则后，下一次路由调用会把仍在使用的动态路由规则重新合并，避免两类规则互相覆盖。

Sentinel Dashboard 中直接新增或修改的规则只适合临时诊断，服务重启或 Nacos 推送后可能丢失；开发环境
以本目录的 JSON 和 Nacos Data ID 为持久化来源。更新规则后重新发布对应 Data ID，并重启或确认客户端
已收到刷新。低强度本地验证方式见 [`../../tests/load/README.md`](../../tests/load/README.md)。
