# Lumora Cloud 后端包结构约定

最后同步：2026-09-03。

本文定义 Lumora Cloud 各 Java 微服务内部的统一包结构。目标是让业务边界、调用方向和文件归属能够
直接从目录中判断，避免所有 Controller、DTO、Mapper 和辅助类堆在同一层。新服务默认遵循本文；
已有服务新增代码也应按本文归位。

## 1. 标准结构

```text
com.lumora.cloud.<service>/
├── <Service>Application.java
├── controller/
│   ├── app/                    # 普通用户、网页控制台和 Desktop 接口
│   ├── admin/                  # 运营管理接口
│   └── internal/               # 仅微服务间调用的接口
├── domain/
│   ├── dto/<feature>/          # 写入参数和命令对象
│   ├── vo/<feature>/           # 对外响应对象
│   ├── entity/<feature>/       # 本服务拥有的数据库实体
│   ├── enums/                  # 领域状态和类型
│   ├── model/                  # 仅服务内部流转的领域值对象
│   └── projection/<feature>/   # Mapper 聚合或只读投影
├── mapper/<feature>/           # MyBatis Mapper，只负责本服务数据库访问
├── service/                    # 业务服务接口 I<Xxx>Service
│   └── impl/                   # 业务服务实现 <Xxx>ServiceImpl
├── support/                    # 有领域语义、但不是独立业务入口的协作组件
├── utils/                      # 本服务专用、无状态或近似纯函数的辅助类
├── cache/                      # 缓存读取、失效和一致性封装
├── messaging/<feature>/        # 消息发布、监听和消息模型
├── job/<feature>/              # 定时扫描与补偿任务
├── audit/                      # 审计写入能力
├── listener/                   # Spring 事件监听器
├── config/                     # Spring 配置和配置属性
├── security/                   # 本服务的权限与内部请求校验
└── error/                      # 服务级异常与异常响应
```

目录按真实职责创建，不要求每个服务机械地拥有所有目录。某个领域只有少量文件且不会造成混淆时，
可以先保持一层；当同类文件出现多个明确子领域时，再按 `<feature>` 分组，避免同时出现“一个目录几十个
文件”和“为了对称制造大量单文件目录”两种极端。

## 2. Service 接口规则

真正承载用例、事务或稳定业务能力的服务使用：

```text
service/IOrderService.java
service/impl/OrderServiceImpl.java
```

Controller、监听器以及其他跨用例调用方依赖 `IOrderService`，不直接依赖实现类。实现类使用
`@Service`，接口不添加 Spring 注解。公开方法在实现类中使用 `@Override`。

以下类型不为了形式额外创建接口：

- DTO 转换、金额计算、哈希、编解码等无状态辅助类；
- 缓存、Mapper、限流器、路由选择器等职责单一的基础组件；
- 只服务于一个业务实现、没有可替换业务契约的内部协作组件；
- 定时任务、消息监听器和事件对象。

这些组件分别进入 `utils`、`support`、`cache`、`routing`、`job` 或 `messaging`。这样既保留
`IService + ServiceImpl` 的可读性，也不会为每个小工具生成一套空接口。

## 3. 各层职责与依赖方向

```text
controller
    ↓
service interface
    ↓
service/impl
    ├── domain
    ├── mapper
    ├── support / utils / cache
    └── cloud-api 中的跨服务契约
```

- Controller 只做身份边界、参数校验、HTTP 状态和业务服务调用，不直接使用 Mapper 或数据库实体。
- ServiceImpl 负责事务、幂等、状态机和多个协作组件的编排。
- Mapper 只处理本服务数据库，不承载权限、状态机或跨服务调用。
- `domain/entity` 不依赖 Controller、Service 或 Spring Web 类型。
- `domain/dto` 与 `domain/vo` 一个公共类型一个文件，不再使用包含大量嵌套 record 的总合同类。
- `support` 可以有领域语义并依赖 Mapper；`utils` 应尽量无状态，不负责事务和数据库访问。
- 业务服务不得反向依赖 Controller 或 VO 所在的 Web 包；跨层返回对象统一位于 `domain/vo`。

## 4. 接口受众与数据对象

Controller 按调用方分组：

- `controller/app`：登录用户、网页控制台、Desktop；路径通常为 `/api/app/**`。
- `controller/admin`：管理员运营能力；路径通常为 `/api/admin/**`。
- `controller/internal`：Feign 或其他服务身份调用；路径通常为 `/internal/**`。

数据对象按用途区分：

- `dto`：创建、更新、查询条件、命令输入；可以包含 Jakarta Validation 注解。
- `vo`：接口响应；不得携带 API Key、内部密钥、数据库锁字段等不应暴露的信息。
- `model`：服务内部值对象，不作为 HTTP 合同。
- `projection`：数据库聚合结果，不直接当作外部响应返回。

`cloud-api` 是例外：它只保存跨微服务稳定使用的 Feign Client 和传输契约，并按 `billing`、`catalog`
等服务边界组织；不得把任一服务的 Entity、Mapper 或 ServiceImpl 放进去。

## 5. 服务专用 utils 与 cloud-common

`<service>/utils` 相当于该微服务自己的工具包，只能由该服务使用。例如：

- User Service 的 JWT、Refresh Token 编解码和请求元数据提取；
- Billing Service 的金额精度和额度周期计算；
- Model Catalog 的金额校验、输入归一化和凭据加解密；
- Model Gateway 的请求幂等 ID 生成。

只有满足“语义稳定、至少被两个模块真实复用、不会引入业务数据所有权”的能力，才允许上移到
`cloud-common`。不得为了减少几行重复代码，把业务规则、Entity 或 Mapper 放入公共模块。

## 6. 当前服务落地示例

- `user-service`：认证与用户管理采用 `IAuthService`、`IUserAdministrationService`；Session 缓存、
  登录审计、事件监听和 Token 工具分别归入 `cache`、`audit`、`listener`、`utils`。
- `billing-service`：套餐、订阅、订单、钱包、结算、历史和统计均使用业务接口与实现；实体、Mapper、
  消息和任务继续按 plan、order、quota、wallet 等领域分组。
- `model-catalog-service`：供应商、模型管理、发布目录和统计使用业务接口与实现；路由、时段价格、
  Provider 读取等内部协作能力进入 `support`，凭据与输入转换进入 `utils`。
- `model-gateway-service`：统一调用入口采用 `IModelGatewayService + ModelGatewayServiceImpl`；计费、
  模型缓存、内部协议、请求校验和 ID 工具分别进入 `billing`、`cache`、`protocol`、`validation`、
  `utils`，已有 `routing`、`concurrency`、`provider`、`recovery`、`diagnostics` 保持独立。

## 7. 测试结构

测试包尽量镜像生产代码：

```text
src/test/java/com/lumora/cloud/<service>/
├── service/impl/
├── utils/
├── support/
└── integration/
```

纯单元测试放在对应生产包；需要 Spring Context、MySQL、Redis 或 RabbitMQ 的跨层测试放入
`integration`，并继续使用显式环境开关，避免普通 `mvn test` 意外访问外部中间件。

## 8. 新服务检查表

新增或重构服务时至少确认：

1. Maven 模块与其他 Service 平级，由 `backend/pom.xml` 直接聚合。
2. Controller 已按 app、admin、internal 调用方分组。
3. 业务用例使用 `IService + ServiceImpl`，辅助组件没有滥建接口。
4. DTO、VO、Entity、Projection 和跨服务合同没有混放。
5. Mapper 只访问本服务数据库，Flyway 脚本位于本服务 `db/migration`。
6. 服务专用工具留在本服务 `utils`，没有把业务规则错误上移到 `cloud-common`。
7. 包路径与 `package` 声明一致，测试结构能够直接定位被测职责。
8. 完成模块测试、全后端测试、`git diff --check` 和敏感信息扫描后再提交。
