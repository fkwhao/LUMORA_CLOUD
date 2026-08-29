# Lumora Cloud Frontend

一个 React 19/Vite 应用同时承载用户控制台和角色受控的运营管理端。组件体系采用 HeroUI v3，
通过 Tailwind CSS v4 组织页面布局，并优先使用 HeroUI 原生默认主题和组件外观：

- `/login`：网页独立登录。
- `/console`：真实套餐、当前周额度、刷新时间和近期用量概览。
- `/console/usage`：最近 100 条服务端权威模型用量。
- `/console/ledger`：最近 100 条额度发放、预占、结算和释放流水。
- `/console/plans`：真实已发布套餐目录，可幂等创建购买或续费订单。
- `/console/orders`、`/console/orders/:orderNo`：订单记录、支付确认与订阅发放结果。
- `/admin`：真实用户、会话、订单、分币种收入、订阅、Usage、模型与供应商运营统计。
- `/admin/billing`：套餐创建、价格与周额度版本发布、用户查找、订阅发放和最近订单观测。
- `/admin/models`：模型创建、能力与计费配置、草稿编辑、发布、启停和版本历史。
- `/admin/providers`：模型供应商创建、API Key 加密写入与轮换。

登录、刷新登录状态与退出已经连接 Cloud Gateway；模型目录管理已经连接 Model Catalog Service，
管理端套餐、订阅发放和订单观测，用户侧套餐概览、额度、用量、流水、套餐目录与订单都已经连接 Billing Service；
运营总览并行调用三个领域统计接口，某个服务不可用时保留其余领域数据，不使用最近 100 条记录在
浏览器端推算全量统计。统计日/月边界采用 `Asia/Shanghai`，收入按币种分别展示。
开发服务器会将 `/api` 代理到本机 `46100` 端口。Web 的 access token 只保存在页面内存，refresh token
由后端写入 HttpOnly Cookie，页面刷新后通过 refresh token 轮换恢复会话。

HeroUI 提供 Card、Button、Input、Chip、ProgressBar、Table、Tabs、Drawer 和可访问性交互等基础组件。
界面使用 HeroUI 默认暗色主题、语义色、圆角和表单样式，工程自身只维护信息布局、图表以及少量 Lumora
品牌标识，不再覆盖为高度风格化的 Dashboard。HeroUI v3 在 Vite 中不需要根级 Provider，样式入口位于
`src/styles/tokens.css`。当前采用组件级 CSS 导入，只加载实际使用的组件；引入新的 HeroUI 组件时，
需要同步增加对应的 `@heroui/styles/components/*.css`。

开发环境可以在 Nacos 中显式开启 `lumora.billing.payment.mock-enabled`，订单详情页才会显示“测试支付”。
该按钮不会调用真实支付机构或产生扣款，仅用于验证订单、支付确认和订阅发放闭环；生产环境必须关闭。

```powershell
pnpm install
pnpm dev
```
