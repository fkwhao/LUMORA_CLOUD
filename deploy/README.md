# Lumora Cloud 中间件部署

这里的 Compose 只负责开发阶段的中间件，不启动 Gateway 或任何 Java 微服务。当前约定是：

- Java 服务在开发电脑本地运行；
- MySQL、Redis、RabbitMQ、Nacos 和 Sentinel 在虚拟机 `192.168.100.132` 运行；
- 持久化数据位于部署目录下的 `data/`，例如部署到 `/opt/lumora-cloud/deploy` 后，MySQL 数据位于
  `/opt/lumora-cloud/deploy/data/mysql`。

## 应用结算恢复配置

2026-09-05 的结算恢复改动包含 Billing V9 迁移及 Model Gateway 文件日志。启动新版 Billing 完成迁移后，再启动新版 Model Gateway 和前端。恢复日志位于运行 Java 服务的主机；本目录的中间件 Compose 不会为本机 Java 服务提供该日志目录。

通过 `LUMORA_RECOVERY_JOURNAL_DIR` 指定每个网关实例独占的持久路径，升级和重启时复用。若以后把 Java 服务放进容器，应单独挂载持久卷。完整参数、恢复步骤和管理员操作见[结算恢复与对账说明](../docs/billing-recovery-and-reconciliation.md)。

## 首次部署

将整个 `deploy` 目录上传到虚拟机 `/opt/lumora-cloud`，然后进入该目录：

```bash
mkdir -p /opt/lumora-cloud
cd /opt/lumora-cloud/deploy
cp .env.example .env
```

编辑 `.env`，至少替换所有 `change-me` 和 `replace-with-...`。以下命令可用于生成 Nacos 密钥：

```bash
openssl rand -base64 48
openssl rand -hex 24
```

第一条输出用于 `NACOS_AUTH_TOKEN`；第二条可分别执行两次，用于
`NACOS_AUTH_IDENTITY_KEY` 和 `NACOS_AUTH_IDENTITY_VALUE`。`NACOS_ADMIN_PASSWORD` 会在首次启动时
自动初始化为 Nacos 管理员 `nacos` 的密码。

## Docker Hub 不可用时取得 Nacos 镜像

MySQL、Redis、RabbitMQ 和 Nacos 已设置 `pull_policy: never`，会直接复用虚拟机现有镜像，
不会主动访问 Docker Hub。Nacos 初始化器也复用 Nacos 镜像。Sentinel 会以本地 Nacos 3.0.3
镜像中的 Java 17 为基础，在虚拟机上构建为独立的 `lumora/sentinel-dashboard:1.8.9` 镜像。
第一次启动前只需让本地存在名为 `nacos/nacos-server:v3.0.3` 的镜像。

开发环境可从国内同步仓库拉取后重新打上官方镜像名：

```bash
docker pull swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v3.0.3
docker tag \
  swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nacos/nacos-server:v3.0.3 \
  nacos/nacos-server:v3.0.3
docker image inspect nacos/nacos-server:v3.0.3 --format '{{.Id}} {{.Os}}/{{.Architecture}}'
```

该同步仓库适合当前本地开发。正式环境应在能够访问 Docker Hub 的可信机器拉取官方镜像，使用
`docker save` 导出并在服务器上通过 `docker load` 导入。

虚拟机上已有名为 `nacos` 的 Nacos 2.1 容器，会占用 8848、9848 和 9849。先保留式停用并改名：

```bash
docker stop nacos
docker rename nacos nacos-v2-backup
```

确认配置并一次启动全部中间件：

```bash
docker compose config
docker compose up -d --build
docker compose ps -a
```

第一次构建 Sentinel 时会通过 GitHub 下载固定版本的 Dashboard JAR，随后缓存到
`lumora/sentinel-dashboard:1.8.9` 镜像中；后续未修改 Dockerfile 或版本时会直接复用构建缓存。
`lumora-cloud-nacos-init` 显示 `Exited (0)` 是正常的，它只负责在 Nacos 就绪后初始化管理员密码。
其余五个长期运行容器最终应显示 `healthy`。查看日志可使用：

```bash
docker compose logs -f --tail=200
```

## 发布 Nacos 配置

中间件首次启动成功后，在 Nacos 的 `public` Namespace、`LUMORA_CLOUD` Group 中发布开发环境配置。
需要创建的 Data ID、YAML 正文和敏感信息边界见 [`nacos-config/README.md`](nacos-config/README.md)。
Java 服务使用 `spring.config.import` 强制加载公共配置和自己的服务配置，因此应先发布配置，再启动
Gateway 和业务微服务。

## 开发机连接信息

| 组件 | 地址或端口 | 账号 |
| --- | --- | --- |
| MySQL | `192.168.100.132:3307` | `.env` 中的 `MYSQL_USER` |
| Redis | `192.168.100.132:6379` | 仅密码认证 |
| RabbitMQ AMQP | `192.168.100.132:5672`，VHost `/lumora` | `.env` 中的 RabbitMQ 账号 |
| RabbitMQ 管理页 | `http://192.168.100.132:15672` | 同上 |
| Nacos 服务端 | `192.168.100.132:8848` | `nacos` |
| Nacos 控制台 | `http://192.168.100.132:8080/index.html` | `nacos` |
| Sentinel 控制台 | `http://192.168.100.132:8858` | `.env` 中的 Sentinel 账号 |

把 `backend/.env.example` 中的密码替换为与部署端 `.env` 一致的值，并把这些变量配置到本地
IntelliJ IDEA 的 Run Configuration。Nacos 的 `LUMORA_NACOS_PASSWORD` 应等于部署端的
`NACOS_ADMIN_PASSWORD`。

这些端口是为了让开发电脑访问虚拟机而监听局域网地址。只应在虚拟机防火墙中允许可信局域网，
不要直接暴露到公网。

## 停止与回退

停止新中间件但保留数据：

```bash
docker compose down
```

若要临时回退旧 Nacos：

```bash
docker rename nacos-v2-backup nacos
docker start nacos
```

MySQL 初始化脚本只会在空数据目录第一次启动时执行。修改数据库名后若已有数据，不会自动重建；
不要使用 `docker compose down -v` 或直接删除 `data/` 来处理正式数据。
