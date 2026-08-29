# Sentinel 镜像

这里的 `Dockerfile` 会在构建阶段从 Sentinel 官方 GitHub Release 下载固定版本的 Dashboard JAR，
并生成独立镜像 `lumora/sentinel-dashboard:<version>`。JAR 只存在于镜像层中，不会下载到或挂载自
宿主机。

为了绕开当前虚拟机无法访问 Docker Hub 的问题，构建默认复用本地已有的
`nacos/nacos-server:v3.0.3` 作为 Java 17 基础镜像；最终的 Nacos 和 Sentinel 仍然是两个独立镜像、
两个独立容器，可以通过 Compose 分别管理。
