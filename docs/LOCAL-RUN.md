# 本地源码与运行环境

源码从 GitHub 的 `java-quest-mvp` 分支获取，不依赖 ChatGPT 附件、chatgpt.site 或云预览。没有默认账号，请启动后注册。

## 必需环境

| 工具 | 版本 | 用途 |
|---|---|---|
| JDK | 21，java/javac 都可用 | API、独立判题和学习者代码 |
| Maven | 3.9+ | 构建两个 Java jar |
| Node.js / npm | 22.12+ 或 24 | 前端依赖、开发和打包 |
| Chrome / Edge | 当前版 | Monaco 编辑器 |

本地 process 模式无需安装 Docker、MySQL 或 Python。H2 随 Maven 依赖提供。Docker 是可选隔离执行器；Python 仅用于可选作者/黑盒检查。没有关闭 TLS、绕过公司策略或自动安装系统软件的启动步骤。

```bash
java -version
javac -version
mvn -version
node --version
npm --version
git switch java-quest-mvp
git pull --ff-only
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

在仓库根目录执行。构建产生 `backend/target/java-quest-api-0.1.0.jar` 和 `java-quest-api-0.1.0-judge.jar`。更新源码后应重新构建并重启旧服务。

## 启动

Bash / Codespaces / Linux 终端 1：

```bash
bash scripts/start-learning.sh process
```

Windows PowerShell 终端 1：

```powershell
.\scripts\start-learning.ps1 -Mode process
```

两者选当前 Shell 对应的一种。终端 2：

```bash
npm run dev --prefix frontend
```

本机访问 `http://localhost:5173`；Codespaces 从端口面板打开 5173。导航 **课程体验** 可先看六类课程的 18 个实际样例，再从课程地图按顺序学习。体验不写 XP/通关；正式关卡真实通过后保存。

Codespaces 的 Bash 脚本自动根据 CODESPACE_NAME 和转发域名配置精确 FRONTEND_ORIGIN，Vite 接受相应主机。没有放开所有 CORS origin 或 Vite host。自定义端口时两个终端设置同一 FRONTEND_PORT；自定义非 Codespaces origin 则设置精确 FRONTEND_ORIGIN。后端地址可用 API_PROXY_TARGET 配置。使用平台已有端口转发，不必将 API 或判题端口公开。

process 模式只运行自己编写并信任的代码，**没有文件/网络/操作系统资源隔离**。默认 off 可阅读和保存但不能运行。个人电脑已有 Docker 时，可改用 docker；首次拉取 `eclipse-temurin:21-jdk`。切换方式、手动启动和企业限制见 [JUDGE-MODES.md](JUDGE-MODES.md)。

## 数据与网络

数据默认位于 Java 用户主目录 `.java-quest`，同一电脑各模式共享；QUEST_DATA_DIR 可修改位置。停止服务不会删除学习记录，不同电脑不会自动同步。本地启动无需 .env、数据库密码或用户提供内部判题 token。

首次构建需要公司允许的 Maven Central（repo.maven.apache.org）和 npm（registry.npmjs.org），或批准的内部镜像/代理。Docker 模式首次还需 Docker Hub 和镜像 CDN。若需要可信 CA，按公司要求配置；不要关闭 TLS 校验。依赖下载后，本地课堂不需访问 ChatGPT。仅有源码并不等于离线免安装包。

本地启动在 Linux 验证，Windows 提供 PowerShell 脚本但未实机验证。不要把本地开发版直接公开为不可信多租户服务。
