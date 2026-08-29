# Lumora Model Gateway Service

Model Gateway 是 Lumora 套餐模型的统一调用入口。Cloud Base URL 下提供与 Desktop Provider
适配器一致的三种原生协议入口：

```text
POST /api/app/model/v1/chat/completions
POST /api/app/model/v1/responses
POST /api/app/model/v1/messages
```

三种入口分别兼容 OpenAI Chat Completions、OpenAI Responses 和 Anthropic Messages 的普通 JSON
与 SSE。调用方需要携带登录 Access Token，并为每次逻辑调用设置稳定的
`X-Lumora-Client-Request-Id`；网络重试必须复用原值。Anthropic 入口额外接受 Desktop 原生适配器
通过 `x-api-key` 发送登录 Access Token，Model Gateway 调供应商时会替换为服务端保存的 Provider Key。

请求经过 Cloud Gateway 的可信身份校验后，Model Gateway 会依次获取 Redis 请求租约和分布式并发
许可、解析 Catalog 已发布配置、向 Billing 预占最大额度、调用供应商、解析权威 Usage 并完成实际
结算。明确拒绝会释放预占；结果不确定或缺失 Usage 时进入待对账；Billing 短暂不可用时由 Redis
恢复队列继续幂等处理。

Provider 密钥不写入 Nacos。新 Provider 由管理端提交 API Key，Model Catalog 使用 AES-256-GCM
加密保存；Model Gateway 通过服务身份读取并短时缓存，401/403 时会清除缓存并重新取 Key 后安全重试
一次。旧版环境变量 `credential_reference` 仍可作为迁移期间的兼容回退。
