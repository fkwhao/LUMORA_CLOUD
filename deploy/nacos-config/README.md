# Lumora Nacos Config

本目录中的文件就是开发环境需要发布到 Nacos 配置中心的完整内容。当前约定：

- Namespace：`public`
- Group：`LUMORA_CLOUD`
- 配置格式：`YAML`
- Data ID：与文件名完全一致

需要发布以下六个 Data ID：

| Data ID | 用途 | 使用方 |
| --- | --- | --- |
| `lumora-common-dev.yaml` | Redis、RabbitMQ、Sentinel、MyBatis 与 JWT 公共非敏感参数 | 全部服务 |
| `lumora-cloud-gateway-dev.yaml` | 网关路由与日志 | Cloud Gateway |
| `lumora-user-service-dev.yaml` | 用户库、Flyway、Token 生命周期与 Cookie | User Service |
| `lumora-billing-service-dev.yaml` | 计费库、订单/测试支付、订单过期与预占释放任务 | Billing Service |
| `lumora-model-catalog-service-dev.yaml` | 模型目录库、Flyway、发布缓存与日志 | Model Catalog Service |
| `lumora-model-gateway-service-dev.yaml` | Provider 连接池、超时、缓存、并发、请求租约与恢复任务 | Model Gateway Service |

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
