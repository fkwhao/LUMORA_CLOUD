# Lumora Cloud Frontend

一个 React/Vite 应用同时承载用户控制台和角色受控的运营管理端：

- `/login`：网页独立登录。
- `/console`：用户套餐、额度和用量概览。
- `/console/plans`：套餐选择与购买页面骨架。
- `/admin`：管理员运营总览。

当前页面使用演示数据，目的是确认信息架构与工程边界，尚未连接 Cloud Gateway。

```powershell
pnpm install
pnpm dev
```
