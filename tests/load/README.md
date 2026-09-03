# 本地保护与压测

本目录只用于开发环境验证，不会连接真实模型供应商。`mock_provider.py` 在本机模拟三种上游协议及
权威 Usage；`load_test.py` 通过 Cloud Gateway 登录并调用 LUMORA Internal Protocol，最后核对用量、
额度桶与不可变流水。

## 1. 准备隔离的测试模型

先启动本机 Mock Provider：

```powershell
python -B tests/load/mock_provider.py
```

然后在管理端创建仅供测试的 Provider 和模型：

- Provider API 格式：`OpenAI Compatible`
- API Base URL：`http://127.0.0.1:19090/v1`
- API Key：可填写无真实权限的测试值，例如 `local-mock-key`
- 逻辑模型编码：`lumora-load-mock`
- 上游模型 ID：`mock-ok`
- 将模型发布；创建一个包含该模型的隔离测试套餐版本，并给专用测试账号发放订阅

套餐版本和已有订阅都是不可变快照，给现有套餐发布新版本不会改变已经购买的订阅。因此建议新建专用
测试账号和测试套餐，不要复用普通用户的正式测试套餐。

不要把测试账号密码、真实 API Key 或 Access Token 写进本目录、命令历史或 Nacos。Mock Provider
只监听 `127.0.0.1`；Java 服务在本机运行时可以访问，局域网其他机器不能访问。

可将上游模型 ID 改为以下场景来验证故障处理：

| 上游模型 ID | 行为 |
| --- | --- |
| `mock-ok` | 返回 200 和固定权威 Usage |
| `mock-slow-1250` | 延迟 1.25 秒后成功 |
| `mock-400`、`mock-401`、`mock-403` | 模拟明确拒绝，预占应释放 |
| `mock-408`、`mock-429`、`mock-500`、`mock-503` | 模拟可故障转移的上游异常 |
| `mock-timeout` | 默认延迟 15 秒，用于超时/熔断验证 |

测试故障转移时，给同一个逻辑模型配置两条路由：第一条使用错误场景，第二条使用 `mock-ok`，并确保
第一条启用了故障转移。不要把所有路由都设为 5xx/超时，除非正在专门验证“待对账”恢复流程。

## 2. 运行低强度本地压测

启动 Cloud 的五个 Java 服务并发布 `deploy/nacos-config` 中的 YAML/JSON 后执行：

```powershell
$env:LUMORA_LOAD_EMAIL = "<测试账号邮箱>"
$env:LUMORA_LOAD_PASSWORD = "<仅当前终端使用的测试密码>"
python -B tests/load/load_test.py --requests 30 --concurrency 3
Remove-Item Env:LUMORA_LOAD_EMAIL,Env:LUMORA_LOAD_PASSWORD
```

脚本默认拒绝对名称不含 `mock` 的模型压测，默认只发 30 个请求、并发为 3。输出包括吞吐、P50/P95/P99、
HTTP 状态、错误码、实际路由分布，以及以下账务一致性检查：

- 成功请求数等于新增 Usage 数；
- Usage 扣减等于 `SETTLE` 流水扣减；
- Usage 扣减等于额度桶已用增量；
- 测试结束后没有新增预占残留。

`/api/app/billing/history` 当前只返回最近 100 条流水，因此开启一致性检查时最多允许 40 个请求。更高
请求量需要加 `--skip-billing-check`，并另外通过管理端或数据库做聚合核对。

## 3. 验证 Sentinel

默认 `lumora.model-gateway.concurrency.per-user=3` 会先限制单账号并发，所以普通测试不应故意打满。
需要验证 Model Gateway 的 QPS 规则时，可在隔离测试环境临时提高该值，再提高 `--concurrency`；验证完成
后恢复为 3。命中流控应返回 HTTP 429 和 `REQUEST_RATE_LIMITED`，命中熔断应返回 HTTP 503 及明确错误码。

Sentinel 控制台适合观察实时资源与临时诊断，Nacos JSON 才是开发环境规则来源。不要只在控制台修改后
就认为规则已经持久化。

## 4. Mock 自检

```powershell
python -B -m unittest discover -s tests/load -p "test_*.py"
```
