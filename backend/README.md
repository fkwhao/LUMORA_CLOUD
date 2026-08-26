# Lumora Cloud Backend

Java 21、Spring Boot 3.5、Spring Cloud 2025 和 Spring Cloud Alibaba 2025 的 Maven 多模块工程。

```text
backend/
├── cloud-common/               # 稳定公共能力，不共享实体或 Mapper
├── cloud-api/                  # Feign Client 与跨服务 DTO 契约
├── cloud-gateway/              # API 入口、鉴权上下文与粗粒度限流
├── user-service/               # 登录、设备会话与角色
├── billing-service/            # 套餐、额度、钱包、预占与账本
├── model-catalog-service/      # 模型、价格版本与发布配置
└── model-gateway-service/      # 流式代理、权威 Usage 与结算编排
```

当前只创建服务边界、启动入口和配置占位。业务表、认证和计费状态机将在后续迭代实现。
