# Java Quest 源码交付与公司电脑本地运行

**需要切换本地进程/Docker 真实判题：见 [判题模式](JUDGE-MODES.md)。**

**不安装 Docker：请直接使用 [本地学习模式](NO-DOCKER.md)，只需 JDK/Maven/Node，内置 H2，无需执行下文的 Docker 启动步骤。**

此项目在本机运行，不依赖 ChatGPT、chatgpt.site、云预览或平台 Git 代理。源码包中的 Maven/npm 使用正常公共依赖仓库；下载源码附件的渠道与程序运行的网络依赖是两件事。

## 1. 包含的源码

- frontend：Vue 3、TypeScript、Vite、Pinia、Vue Router、Monaco，含 package-lock.json。
- backend：Java 21、Spring Boot 3.5.7、Maven，含 Flyway SQL 初始化迁移、10 关课程 JSON、API 测试。
- judge：独立 Python 标准库服务，通过 Docker 隔离编译与执行 Java；无需 pip 安装第三方包。
- compose.yml：MySQL 8.4 及持久化命名卷。
- scripts：PowerShell/Bash 环境加载、真实判题黑盒测试。
- docs：产品、架构、数据库、API、原验证记录和本说明。

不含 .env、云环境密码、账号数据、.git、node_modules、Maven 缓存、构建产物或 Docker 镜像。本包是完整阶段 1 源码，**不是离线免安装软件包**。没有默认登录账号，需要本地注册。

## 2. 运行环境

| 组件 | 要求 | 用途 |
|---|---|---|
| 操作系统 | Windows 10/11 + WSL2，或 Linux | Windows 使用 PowerShell；Docker 必须为 Linux containers |
| Java | JDK 21，java 和 javac 都在 PATH | 编译/运行主 API；只安装 JRE 不够 |
| Maven | 3.9+ | 后端构建；本次验证使用 3.9.11 |
| Node.js / npm | Node 22.12+ 或 24 LTS，自带 npm | 前端安装、开发与打包 |
| Python | 3.11+ | 独立判题协调器；Windows 命令为 python |
| Docker | Docker Desktop / Engine，Compose v2 | MySQL 和真实 Java 沙盒，必须能运行 Linux 容器 |
| MySQL | 8.4，由 compose 启动 | 不需要额外手装 MySQL |
| 浏览器 | 当前版 Chrome / Edge | Monaco 编辑器与课堂 |

建议电脑至少 8GB 内存、给 Docker 分配至少 4GB 内存并保留镜像空间。该配置是建议，不是本次做过的最低配置测试。

此前完整功能验证在 Linux 完成；Windows 提供了相应脚本与命令，尚未在 Windows 实机验证。不要把本地开发版直接公开部署为生产多租户服务。

在 PowerShell 检查前置工具：

```powershell
java -version
javac -version
mvn -version
node --version
npm --version
python --version
docker version
docker compose version
```

Docker version 必须同时有 Client 和 Server；只有客户端不能判题。

## 3. 首次安装（Windows PowerShell）

解压后进入含 README.md / compose.yml 的 Java-Quest 目录。例如：

```powershell
cd C:\dev\Java-Quest
Copy-Item .env.example .env
notepad .env
```

仅第一次复制 .env；若已配置，不要覆盖它。把 .env 中 DB_PASSWORD、MYSQL_ROOT_PASSWORD、JUDGE_TOKEN 三个 replace-with-* 占位值替换为三个不同的长随机值，JUDGE_TOKEN 至少 32 字符。此处是你本地新建的数据库密码与内部服务密钥，不需要 OpenAI 或 GitHub token。保留 DB_URL 两侧的单引号。

先启动 Docker Desktop，选择 Linux containers，再执行：

```powershell
docker pull eclipse-temurin:21-jdk
docker compose up -d --wait
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

数据库由 compose 创建，表结构由 API 启动时自动执行 backend/src/main/resources/db/migration/V1__initial.sql。不要重复手工导入同一脚本。Maven verify 的自动化测试不需要真实数据库；真实联调需要下一节的全部服务。

## 4. 分别启动三个终端

每个终端都先进入同一个源码根目录。保持三个进程运行。

### 终端 1：独立判题服务

```powershell
cd C:\dev\Java-Quest
. .\scripts\dev-env.ps1
python judge/server.py
```

### 终端 2：Spring Boot API

```powershell
cd C:\dev\Java-Quest
. .\scripts\dev-env.ps1
java -jar backend/target/java-quest-api-0.1.0.jar
```

也可以用 mvn -f backend/pom.xml spring-boot:run。代码修改后先重新构建 jar。前两个终端必须加载相同 .env，否则 API 与判题服务无法认证。

### 终端 3：Vue 前端

```powershell
cd C:\dev\Java-Quest
npm run dev --prefix frontend
```

浏览器打开 http://localhost:5173 。注册账户、进入第一关，将输出改为 Hello, Java Quest!，点击运行并验证；真实测试通过后才保存进度并解锁下一关。

| 地址/端口 | 功能 |
|---|---|
| http://localhost:5173 | Vue 网站 |
| http://localhost:8080/api/health | API 与数据库检查 |
| http://localhost:8080/swagger-ui.html | API 文档 |
| http://127.0.0.1:8091/health | 判题协调器与镜像检查 |
| 127.0.0.1:3306 | MySQL，仅本机 |

## 5. 验证与停止

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://127.0.0.1:8091/health
python scripts/smoke.py
npm run build --prefix frontend
python -m unittest discover -s judge -v
```

smoke.py 会注册唯一测试用户并产生真实提交，覆盖 10 关、错误输出、编译失败、运行异常、超时、输出限制与多文件。它并非一个只检查端口的演示脚本。

三个终端分别 Ctrl+C 停止服务；docker compose stop 停数据库并保留卷。不要用 docker compose down -v，除非明确要删除所有学习数据。更换 .env 密码不会自动修改已存在 MySQL 卷内的账户密码。

## 6. 公司网络需要允许什么

正常构建主要需要：

| 来源 | 典型域名 |
|---|---|
| Maven Central | repo.maven.apache.org |
| npm | registry.npmjs.org |
| Docker Hub 认证、清单、镜像层 | auth.docker.io、registry-1.docker.io，以及 Docker 返回的镜像 CDN 域名，例如 production.cloudfront.docker.com |

Docker CDN 地址随地区和服务变化；这不是穷尽清单，应由公司 IT 按实际失败请求配置。Playwright 浏览器下载是可选测试依赖，日常学习网站运行不需要它。镜像与 npm/Maven 依赖下载完成后，本地课堂运行不需要访问 ChatGPT。

若公司也限制以上仓库，使用公司批准的 Maven/npm/Docker 镜像仓库或代理，按公司要求安装可信 CA。源码没有夹带云平台的代理配置或凭据。不要关闭 TLS 校验，不要直接复制陌生镜像地址。Docker 无法运行时，真实 Java 判题不可用，程序会报错，不会偷偷退回宿主执行或模拟成功。

需要完全离线安装时，还必须另外准备依赖缓存或已构建产物，以及 docker save 导出的 MySQL/JDK 镜像；仅有本源码 ZIP 不足以完成首次离线构建。应按公司规定由 IT 导入这些材料。

若 PowerShell 组织策略禁止执行脚本，可按公司规定在终端设置 .env 中对应的进程环境变量，再分别启动两个服务；不要绕过组织策略。

## 7. 本版范围

已实现：10 个基础输出练习、真实隔离 Java 判题、账号/进度/经验、草稿/多文件、分级提示、笔记/失败记录和深浅主题。

输出判题不验证必须使用某一种语法。Maven/Spring Boot 课程沙盒、多线程和数据结构可视化、综合项目判题尚未实现；这些是后续迭代，不包含在本源码包的已完成承诺中。
