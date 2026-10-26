# Java Quest

公司电脑运行与依赖网络说明见 [本地运行指南](docs/LOCAL-RUN.md)。运行本项目不需要访问 chatgpt.site。

在实践中学会 Java。Vue 3 + TypeScript + Monaco 互动课堂，Spring Boot 3 / Java 21 API，MySQL 8，独立 Java 判题服务（本地进程 / Docker）。

当前为**本地开发版本**：42 个递进练习和多文件工程项目，注册登录、进度与经验、草稿、多文件、笔记和失败提交记录。提交按所选模式经独立进程或容器真实编译/执行；判题不可用会报错，不会模拟通关。当前是输出判题，不能证明源码使用了指定语法，也不提供防作弊保证。完整方案见 [产品设计](docs/PRODUCT.md)、[架构与接口](docs/ARCHITECTURE.md)、[API 使用说明](docs/API.md) 和 [验证记录](docs/VALIDATION.md)。

## 目录

```text
frontend/     Vue + Vite + Pinia + Vue Router + Monaco
backend/      Spring Boot REST API、课程 JSON、Flyway 迁移、测试
backend/.../runner/ 独立 Java 判题服务，支持 process/docker
judge/        原 Python Docker 协调器（兼容保留）
scripts/      环境加载与黑盒验证
compose.yml   MySQL 8.4（命名卷持久化）
docs/         产品、架构、实施计划与验证证据
```

## 课程结构与多文件工程练习

课程现按「阶段 → 章节 → 知识点 → 递进练习」组织，目录覆盖 Java 语言、集合与算法、Maven 工程化、Spring 服务端、并发/JVM 和高级工程实践。完整细目及逐条开放状态见 [课程总目录](docs/COURSE-ROADMAP.md)。当前 42 个可运行练习，106 个知识点下共 318 个练习标题；其余 276 个是规划，不是已交付课程。

每个已开放知识点包含模仿/补全、定位/修复、独立实现或项目验收。新练习按显式前置关系解锁，原 10 关 ID、分数与进度保留。推荐路线优先走完当前知识点；旧关保留原前置关系，避免已学习用户被强制回退。

订单结算项目实际使用 `src/main/java/quest/orders/` 的 4 个 Java 文件，按 3 个需求步骤修改领域对象、价格服务和报表。Main/Application 调用方可设为只读，由前后端共同保护；判题按服务器指定入口编译、运行整个源码项目。当前构建器是 javac，Maven 生命周期、Spring API 项目和完整高级课程仍待交付。

作者可选验证（需要 Python，仅为开发检查，不是运行依赖）：`python scripts/verify-curriculum.py --mode process --allow-local-process`。它逐关执行作者参考实现并确认起始代码不能直接通关。参考实现仅在测试资源中，课程 API 不返回测试或参考解。

## 可切换的真实判题（无需 Python）

公司电脑可明确选择本地独立 Java 进程判题；个人电脑可选择 Docker 容器判题。进程模式只用于自己信任的代码，没有文件/网络/CPU/PID 隔离。两模式都是真编译、真测试，学习者代码不在 Spring API JVM 内执行。

```powershell
npm ci --prefix frontend
mvn -f backend/pom.xml verify
# 终端 1，以下二选一：
.\scripts\start-learning.ps1 -Mode process
# 或已安装 Docker 时：.\scripts\start-learning.ps1 -Mode docker
# 终端 2：
npm run dev --prefix frontend
```

切换前在终端 1 Ctrl+C 停止，再以另一模式启动，前端刷新。两种模式在同一台电脑共用 H2 数据；不同电脑不会自动同步。默认 `-Mode off` 不执行代码。完整说明与无需运行 PowerShell 脚本的手动启动方式见 [判题模式切换指南](docs/JUDGE-MODES.md)。首次 Docker 模式需 `docker pull eclipse-temurin:21-jdk`；这些命令不使用 MySQL 容器或 Python。

## 不安装 Docker：只读学习模式

只需 JDK 21、Maven 3.9+、Node.js 22.12+ 或 24。H2 文件数据库随依赖安装，无需 MySQL、Python 或 .env。更新 `java-quest-mvp` 分支后：

```powershell
npm ci --prefix frontend
mvn -f backend/pom.xml verify
# 终端 1
java -jar backend/target/java-quest-api-0.1.0.jar --spring.profiles.active=local
# 终端 2（同一项目根目录）
npm run dev --prefix frontend
```

访问 `http://localhost:5173`。可以注册、阅读课程、编辑和保存草稿/笔记；未接入隔离判题时不能在线运行或通关，界面与 API 均明确禁用。数据保存在 Java 用户主目录的 `.java-quest` 文件夹，重启后保留；不会自动导入 MySQL 数据。完整说明见 [无 Docker 启动指南](docs/NO-DOCKER.md)。

以下原有环境要求及启动方式适用于完整 Docker 判题模式。

## 前置条件

- JDK 21（`java -version` 与 `javac -version` 都需可用），Maven 3.9+
- Node.js 22.12+ 或 24，Python 3.11+
- Docker Desktop（Windows 启用 WSL2 / Linux containers）或 Linux Docker Engine，Docker Compose v2
- 首次可访问 Maven Central、npm registry 和 Docker Hub。普通开发不需要第三方 API key。

判题服务持有 Docker 管理权限，**仅在可信本地机器运行**。API 不挂载 Docker socket。不要把本项目或判题协调器直接暴露给不受信任的互联网用户；生产部署方案在阶段 4，普通 Docker 共享宿主内核不等于强多租户隔离。

## Windows PowerShell 启动

在仓库根目录执行：

```powershell
Copy-Item .env.example .env
# 用编辑器将 .env 中三个 replace-with-* 值换成不同的长随机值。
# JUDGE_TOKEN 至少 32 字符。不要把 .env 加入 Git。
# 推荐用密码管理器生成。然后启动 Docker Desktop。
docker pull eclipse-temurin:21-jdk
docker compose up -d --wait
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

保留三个终端，每个终端工作目录都是**仓库根目录**：

```powershell
# 终端 1：独立判题协调器（仅监听 127.0.0.1:8091）
. .\scripts\dev-env.ps1
python judge/server.py
```

```powershell
# 终端 2：API（MySQL 自动执行 Flyway 初始化迁移）
. .\scripts\dev-env.ps1
mvn -f backend/pom.xml spring-boot:run
```

```powershell
# 终端 3：前端
npm run dev --prefix frontend
```

浏览器打开 `http://localhost:5173`，注册账户，进入第一关。把输出修改为 `Hello, Java Quest!`，点击运行；全部测试通过后才会计入进度并开放下一关。首关原始代码应判为输出不符。

```powershell
# 活性和接口文档
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://127.0.0.1:8091/health
# 浏览器访问 http://localhost:8080/swagger-ui.html
# 黑盒测试会在数据库新增唯一 smoke-* 用户与真实提交。
python scripts/smoke.py
npm run build --prefix frontend
python -m unittest discover -s judge -v
```

PowerShell 脚本受本机执行策略管理；若组织策略禁止 dot-source，可在终端通过 `$env:DB_PASSWORD='...'` 等手动设置同名变量，勿在聊天中粘贴凭据。首次 Docker/MySQL 启动可能需数十秒。

## Linux / macOS

```bash
cp .env.example .env   # 编辑随机密码和 JUDGE_TOKEN
# 每个需要变量的终端都执行：
source scripts/dev-env.sh
docker pull eclipse-temurin:21-jdk
docker compose up -d --wait
npm ci --prefix frontend
mvn -f backend/pom.xml verify
# 分别在三个终端运行
python3 judge/server.py
mvn -f backend/pom.xml spring-boot:run
npm run dev --prefix frontend
```

测试命令与 Windows 相同，Python 可使用 `python3`。

## 浏览器端测试

先启动 MySQL、判题、API 与前端，然后执行：

```powershell
# 仅首次安装 Chromium（有网络、按组织策略安装）
cd frontend
npx playwright install chromium
cd ..
npm run test:e2e --prefix frontend
```

浏览器测试直接调用真实后端，不注入假判题：验证注册、错误输出、编译错误、Monaco 修改、草稿刷新、真实通关、下一关、笔记、复习记录、深色模式和 390px 布局。测试新增唯一用户，截图与失败 trace 位于被忽略的 `.artifacts/`。已有 Chromium 时可设 `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`，例如云环境 `/usr/bin/chromium`。后端自动化测试使用 H2 和明确标注的判题契约替身；真实 MySQL / Docker 检查由 `scripts/smoke.py` 负责。

## 数据库与持久化

`backend/src/main/resources/db/migration/V1__initial.sql` 是数据库初始化脚本。Compose 用 MYSQL_DATABASE/MYSQL_USER 创建专用数据库和账户，API 使用 Flyway 自动迁移；不要再手工重复导入同一脚本。也可使用已有 MySQL 8：创建空库及账户，设置 DB_URL/DB_USER/DB_PASSWORD，再启动 API。数据库结构见架构文档。

MySQL 数据保留在命名卷。关闭 API、判题和前端用 Ctrl+C；`docker compose stop` 停数据库；`docker compose down` 保留数据库卷。`docker compose down -v` 会删除全部学习数据，请仅在明确要重置时使用。换了 .env 密码不会改变已有卷中的 MySQL 用户密码。

账户密码 BCrypt 存储；会话随机 token 的 SHA-256 存数据库，cookie HttpOnly / SameSite Strict。写接口需 `X-Quest-Client: 1`，CORS 仅允许 FRONTEND_ORIGIN。进度只能由后端在可信判题通过后更新；没有客户端“通关”接口。草稿本机缓存按用户与关卡区分，登录后自动保存到数据库；手动保存按钮可重试云端保存。重置需要确认。未登录草稿在登录后可选择导入，不会自动覆盖账户草稿；代码修改后会清除旧判题反馈，需重新运行。

## 判题与故障排查

- `/api/health` 验证 API 与数据库；`/health` 验证 Docker 和镜像存在，只有真实代码提交能验证完整沙盒。
- 503：确认协调器已启动、两个终端的 JUDGE_TOKEN 相同、镜像已拉取、Docker 可运行且支持资源限制。服务不会回退至宿主执行。
- 编译错误：阅读 stderr 的文件与行号；主类须为 `Main` 且位于 `Main.java`，包含标准 main 入口。
- 限制：每测试 15 秒编译 + 3 秒运行（协调器全流程 25 秒），1 CPU / 256MiB / 64 PIDs，Java heap 96MiB，输出 64KiB，8 个平面 Java 文件/32KiB 字符。无网络；不支持 package 目录或外部依赖。
- 本地源文件使用 UTF-8。前端使用 `/api` Vite 代理；跨源调用则设置精确 FRONTEND_ORIGIN，不使用 `*` credentials。
- Java 在受管代理网络无法下载 Maven 依赖时，配置 Maven `settings.xml` 的官方代理设置并将环境颁发 CA 加入专用 truststore；不得关闭 TLS 或使用 insecure 参数。本次云环境的专用工具配置在仓库外，不包含在项目内。
- Windows 上协调器使用标准 Python + Docker CLI，设计避免宿主绑定目录；**尚未在 Windows 实机验证**，具体平台状态见验证记录。

## 实际范围与后续

已实现学习闭环、层级课程地图及多文件订单项目。未开放课程在地图中逐条标记规划中。未实现：继承/多态完整专题、源码语义评分、Maven/Spring API 沙盒、并发可视化、完整课程管理、搜索、有效学习时长、邮件验证/找回密码、生产分布式队列与监控、强隔离多租户部署。API 文档由 springdoc 自动生成，不代表上述未实现能力已存在。

当前提交等待阶段 1 验收，确认后再进入阶段 2。
