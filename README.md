# Java Quest

在实践中学会 Java：Vue 3 + TypeScript + Monaco 互动课堂，Java 21 / Spring Boot 3 API，独立 Java 判题服务。运行不依赖 ChatGPT 或 chatgpt.site。源码通过 GitHub 的 `java-quest-mvp` 分支交付。

当前是**本地开发版本**，开放 **65 个真实练习**，包含账户、进度、XP、草稿、多文件、笔记和失败记录。课程目录有 6 阶段、28 章、106 知识点、600 个练习标题；其中 **535 项仍为规划**。每知识点按概念、边界、故障与迁移场景安排 3–9 项，未固定题数。逐条状态见 [课程总目录](docs/COURSE-ROADMAP.md)。

## 先体验每门课程

启动后打开导航中的 **课程体验**（`/samples`），六类课程各精选三个代表练习，共 18 个。可直接看讲解、编辑代码；登录并启用判题后可跳过前置要求真实运行。体验不计 XP、通关或提交历史，正式学习仍按前置关系解锁。体验与正式课程共用本关草稿。

| 课程 | 体验内容 |
|---|---|
| Java 语言与程序设计 | 空值短路、Unicode、资源关闭 |
| 集合与算法 | 词频报表、集合删除、括号栈 |
| Maven 与工程化 | JDK 编译目标、测试依赖、父子模块坐标 |
| Spring 服务端 | 构造器注入、Qualifier、字段校验 |
| 并发与 JVM | join、丢失更新、原子计数 |
| 高级工程实践 | 版本库存、有界接收、幂等响应 |

具体入口与验证边界见 [课程体验说明](docs/COURSE-SAMPLES.md)。

## 环境与首次准备

必需：**完整 JDK 21、Maven 3.9+、Node.js 22.12+ 或 24、Chrome/Edge**。使用 process 模式不需要 Docker、MySQL 或 Python；H2 文件数据库随 API 依赖提供。首次构建需要可访问公司批准的 Maven/npm 仓库，依赖下载后学习无需 ChatGPT 网络。

在仓库根目录执行，Windows PowerShell 和 Bash 均适用：

```bash
git switch java-quest-mvp
git pull --ff-only
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

Maven 会运行真实 javac/java 测试并生成两个 jar。每次拉取更新后重新构建，并停止旧服务再启动；否则网页可能仍显示旧课程。**Linux 云环境已验证，Windows PowerShell 尚未实机验证。**

## Bash / Codespaces / Linux 启动

终端 1（仓库根目录）：

```bash
bash scripts/start-learning.sh process
```

终端 2（同一仓库根目录）：

```bash
npm run dev --prefix frontend
```

本机浏览器访问 `http://localhost:5173`。Codespaces 从“端口”面板打开 **5173**；Bash 启动器会采用该 Codespace 对应的精确前端 origin，Vite 允许对应转发主机。保留端口的私有访问设置。不要在 Bash 中执行 `.\scripts\start-learning.ps1`，这是 PowerShell 语法。

## Windows PowerShell 启动

终端 1：

```powershell
.\scripts\start-learning.ps1 -Mode process
```

终端 2：

```powershell
npm run dev --prefix frontend
```

如果公司禁止脚本，按 [判题模式指南](docs/JUDGE-MODES.md) 手动启动 Java，不修改组织执行策略。

## 可切换的真实判题

| 模式 | 用法与能力 |
|---|---|
| process | 本地独立 JVM 中真实编译/运行，只用于自己编写并信任的代码。**没有文件、网络、CPU 或 PID 隔离** |
| docker | 已安装且允许 Docker 时，使用一次性非 root、无网络、只读根文件系统和资源受限容器 |
| off | 阅读、编辑、保存草稿与笔记；关闭运行和新通关 |

Bash 把末尾参数换成 `docker` 或 `off`；PowerShell 换 `-Mode docker` 或 `-Mode off`。切换前在终端 1 按 Ctrl+C，等待退出，再启动新模式并刷新网页。Docker 首次需要 `docker pull eclipse-temurin:21-jdk`。两种判题均不在 Spring API JVM 内执行学习者代码，故障时明确报错，不回退或模拟成功。

脚本生成临时内部认证 token，不需要 `.env`。同一电脑的各模式使用 Java 用户主目录 `.java-quest` 中同一 H2 数据，重启保留账户与进度；不同电脑不会自动同步。可用 `QUEST_DATA_DIR` 指定自己的数据目录。详细手动启动、端口与安全边界见 [判题模式](docs/JUDGE-MODES.md)、[本地运行](docs/LOCAL-RUN.md)。

## 教学和工程练习的实际范围

正式学习：阅读 → 修改 → 真实测试 → 首次通关保存 → 下一关。原始关卡 ID、XP 与进度保留。只有可信判题通过才写入通关；重复通过不重复加 XP。课程数据驱动，规划内容没有提交入口。

- Java 和集合题使用 javac、多组输入与边界断言。并发题使用 join/屏障等确定性同步验证不变量，避免依赖随机调度。
- 订单项目有 `src/main/java/quest/orders/` 下四个文件，逐步修改领域对象、服务和报表；整套源码实际编译。只读调用方由编辑器和 API 同时保护。
- 三个 Maven 练习实际解析和校验 POM XML，支持编辑多模块配置。**不执行 Maven 生命周期，不下载学习者依赖、不生成 Maven 构建产物。**
- 三个 Spring 练习实际执行 Spring 容器注入/Qualifier 和 Jakarta Bean Validation。可信框架依赖从已构建的判题 jar 提供。**尚无完整 Spring Boot HTTP 应用/数据库实验。**
- 库存/任务接收/幂等样例验证内存中的业务契约；不宣称提供 MySQL 事务、分布式幂等或生产任务系统。

当前主要根据可观察行为判题，不能证明源码用了指定语法，也不是防作弊系统。未实现完整高级课程、数据结构/线程动画、完整 Maven/HTTP 项目执行器、课程管理、邮件找回密码或生产强隔离多租户部署。

## 验证与开发

```bash
mvn -f backend/pom.xml verify
npm run build --prefix frontend
# 可选：作者课程回归，需要 Python 3.11+，不是学习网站的运行依赖
python scripts/verify-curriculum.py --mode process --allow-local-process
# 可选：真实服务启动后，需要 Playwright Chromium
npm run test:process --prefix frontend
```

作者回归逐关确认起始代码不能直接通过、参考实现可以通过。参考解只在测试资源中，课程 API 不返回参考解或测试。浏览器测试不模拟判题，详见 [验证记录](docs/VALIDATION.md)。

## 目录与其他部署

```text
frontend/                Vue / Vite / Pinia / Router / Monaco
backend/                 Spring API、课程 JSON、Flyway、测试
backend/.../runner/      独立 Java process/docker 判题服务
scripts/                 Bash/PowerShell 启动器与作者回归
judge/                   旧 Python Docker 协调器，兼容基础任务
compose.yml              可选 MySQL 8.4
docs/                   产品、架构、课程、API 与验证说明
```

数据库初始化为 `backend/src/main/resources/db/migration/V1__initial.sql`，由 Flyway 自动执行。已有 MySQL 8 时可按 [架构](docs/ARCHITECTURE.md) 设置 DB_URL/DB_USER/DB_PASSWORD，启动非 local profile API；独立 Java 判题 jar 不变。旧 Python 协调器不支持新 package/POM/Spring 任务，学习当前课程请使用 Java 启动器。

[产品设计](docs/PRODUCT.md) · [API](docs/API.md) · [课程总目录](docs/COURSE-ROADMAP.md) · [验证记录](docs/VALIDATION.md)
