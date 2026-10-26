# 可切换判题：公司电脑用进程，个人电脑用 Docker

Docker 不是“真实编译和判题”的必要条件；它提供的是运行环境的权限与资源隔离。Java 可以在独立进程里编译和运行，再由课程测试判定结果。**独立进程不等于安全沙盒**：本地代码拥有当前系统用户的文件和网络权限。

| 模式 | 实际能力 | 限制与适用范围 |
|---|---|---|
| off | 阅读、编辑、草稿和笔记，关闭判题 | 不执行代码，不授予新通关/XP |
| process | 独立 Java 判题服务 → javac 子进程 → java 子进程 → 真实判题/通关 | 只运行自己编写并信任的练习代码；不是安全隔离；受公司进程执行策略约束 |
| docker | 独立 Java 判题服务 → 一次性 Docker 容器 → 真实判题/通关 | 非 root、无网络、只读根文件系统、CPU/内存/PID 等容器限制；需已安装且允许使用 Docker |
| remote | API 连接已存在的兼容判题服务 | 本机无需 Docker；需要可用服务、真实密钥及公司批准的网络访问；本次未提供公网服务 |

两个可执行 Java jar 都由同一个 Maven 项目构建，**学习者代码不在 Spring Boot API JVM 中运行**。公司电脑的 process 模式无需安装 Python、Docker 或 MySQL；数据库仍为内嵌 H2。保留原 Python Docker 协调器用于兼容，但新启动脚本不依赖它。

## Windows PowerShell：首次准备

先确认公司允许运行 JDK、javac/java 子进程和本地 HTTP 服务。如果组织策略禁止这些能力，应保持 off 或使用批准的远程服务；本项目不会绕过公司限制。

需要 JDK 21、Maven 3.9+、Node.js 22.12+ 或 24。在原项目根目录：

```powershell
git switch java-quest-mvp
git pull --ff-only
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

verify 会真实运行受控的 javac/java 测试程序；若公司连子进程也禁止，测试会失败。只想关闭判题并阅读课程时，可构建 `mvn -f backend/pom.xml -DskipTests package` 再启动 off；该命令仅打包，不能当作测试已通过。

每次更新后重新构建。构建会生成：

- `backend/target/java-quest-api-0.1.0.jar`：API 与 H2。
- `backend/target/java-quest-api-0.1.0-judge.jar`：独立判题服务。

## 公司电脑：本地真实判题

终端 1（源码根目录）：

```powershell
.\scripts\start-learning.ps1 -Mode process
```

脚本明确启用受信任本地代码模式，生成临时随机内部 token，启动独立判题 JVM 与 API JVM。token 不显示、不写入文件、不包含在命令行参数中；学习者 JVM 的环境变量会清理 API/数据库/代理/Java 注入选项，但这不能保护主机免受恶意代码攻击。

终端 2（同一源码根目录）：

```powershell
npm run dev --prefix frontend
```

浏览器打开 http://localhost:5173，刷新页面以加载当前模式。顶部会明确显示“本地进程判题，没有文件、网络或进程隔离”。注册后修改 Hello World，真实通过才授予 30 XP 并开放下一关。

## 自己电脑：Docker 判题

仅当已经安装并允许使用 Docker 时，先启动 Docker。首次拉取镜像：

```powershell
docker pull eclipse-temurin:21-jdk
```

终端 1：

```powershell
.\scripts\start-learning.ps1 -Mode docker
```

终端 2：

```powershell
npm run dev --prefix frontend
```

此启动方式只对判题使用 Docker，账户数据库仍使用同一份 H2，**不用启动 MySQL 容器**。原来的 MySQL/compose 部署仍然可用，但属于另一种数据库配置。

## 切换与数据保留

在启动脚本的终端按 Ctrl+C，等待其拥有的 API/判题子进程退出，再用另一个 `-Mode` 启动；前端刷新即可。不能在旧实例占用端口时再启动第二套。网页不允许提交者自己更换执行器，模式由本机启动配置控制。

```powershell
.\scripts\start-learning.ps1 -Mode off
.\scripts\start-learning.ps1 -Mode process
.\scripts\start-learning.ps1 -Mode docker
```

以上是三种备选命令，不是连续启动三个实例。

同一台电脑中，各模式共用 Java 用户主目录的 `.java-quest/javaquest.mv.db`，账户、草稿、笔记和通关不会因模式切换而清空。也可在启动前设置 `QUEST_DATA_DIR` 指定同一个目录。

**公司电脑和个人电脑是两台机器时，本地 H2 不会自动同步。** 本次没有实现跨设备同步；如需共享进度，需要额外部署共享 API/数据库，或在允许的情况下关闭服务后按规定备份自己的学习数据。不要擅自传输公司数据。

## 若公司禁止 PowerShell 脚本

不要修改/绕过组织执行策略。在允许直接运行 Java 的前提下，可手动启动两个 Java 服务：

终端 A：在本机安全设置一个随机 `JUDGE_TOKEN`（至少 32 字符，不能使用固定示例，也不要把它发到聊天），然后：

```powershell
java -jar backend/target/java-quest-api-0.1.0-judge.jar --mode=process --allow-local-process
```

终端 B：安全设置与 A 相同的 `JUDGE_TOKEN`，再设置：

```powershell
$env:JUDGE_ENABLED='true'
$env:JUDGE_MODE='process'
$env:JUDGE_URL='http://127.0.0.1:8091'
java -jar backend/target/java-quest-api-0.1.0.jar --spring.profiles.active=local
```

终端 C 启动 `npm run dev --prefix frontend`。切换到 Docker 时，A 去掉 `--allow-local-process`、改 `--mode=docker`，B 改 `JUDGE_MODE='docker'`，然后重启两服务并刷新前端。不要在服务尚运行时只改环境变量。

Linux/macOS 可用 `bash scripts/start-learning.sh process`（或 docker/off）；脚本还使用 bash/curl/od/tr 等系统工具，前端同样另开终端运行。服务默认端口为 API 8080、判题 8091。若自定义前端端口，同时设置 FRONTEND_ORIGIN 为其精确 origin；可用 API_PROXY_TARGET 指定 Vite 的后端地址。

## 边界与验证机制

- 默认 off；必须明确选择 process。独立判题服务的 process 模式还要求 `--allow-local-process`；API 必须携带匹配的 X-Judge-Mode，模式不一致在执行前返回 409。Docker 故障不会回退为 process。
- 进程模式：每次测试独立临时目录，编译 15 秒、运行 3 秒，输出合计上限 64KiB，Java heap 96MiB、metaspace 64MiB。超时/输出超限终止已知进程和后代；这是善意程序的预算控制，不能保证捕获恶意脱离的子进程。
- 进程模式没有操作系统级 CPU、总内存、文件、网络或 PID 隔离。`-Xmx` 不是整个进程的硬内存上限；ActiveProcessorCount 不是 CPU 配额。不能运行陌生代码，不能开放给互联网用户；Java SecurityManager 也不能被当作可靠替代。
- Docker 模式：每测试新容器，无宿主目录/凭据/socket 挂载；非 root、network none、cap-drop ALL、no-new-privileges、1 CPU、256MiB、64 PIDs、64MiB tmpfs。容器创建/复制/清理另有超时，API 等待预算 420 秒；正常编译 15 秒、执行 3 秒。Docker 共享内核，不等于生产强多租户隔离。
- 两种模式都支持多文件、真实 stdout/stderr、编译错误、运行错误、时间/输出超限与输出比较。判题器和课程测试仍以当前已开放的 65 个练习为范围，不能保证源码用了指定语法。
- 已提供 POM 语义校验、真实 Spring 组件/验证器样例；完整 Maven 构建、Spring Boot HTTP 项目和多线程可视化仍未实现，边界见 [课程体验](COURSE-SAMPLES.md)。
- 可选的 Python 黑盒脚本 `python scripts/smoke.py` 会依据 capabilities 选择检查：process 只检查环境过滤，不声称网络/文件隔离；docker 才执行隔离断言；off 会直接拒绝测试。Python 不是本地学习或 Java 判题的运行依赖。
- `npm run test:process --prefix frontend` 使用真实服务验证本地判题的完整学习闭环；`test:local` 用于 off。运行前先以相应模式启动服务，并准备 Playwright Chromium。
- 本次验证平台为 Linux；Windows 脚本设计兼容 PowerShell 5.1+，但没有 Windows 实机测试。若出现“Access denied/CreateProcess”等组织策略错误，停止本地判题并联系 IT，不采用权限绕过方案。

远程隔离服务、虚拟机或 gVisor 等也是替代方案，但需要已有基础设施或额外权限；本次只实现 process/docker/off 的本机切换，不提供这些服务。

## Spring 组件实验的资源预算

三个 spring-component 课程使用构建时已随 judge jar 打包的可信框架依赖，无需另行安装 Maven 或联网下载学习者依赖。process 中仍是独立 JVM；Docker 中把这些可信 jar 流式复制到临时容器，不挂载宿主目录。

普通题仍为 256MiB 容器 / 64MiB tmpfs / 96MiB heap / 64MiB metaspace / 3 秒运行。Spring 组件题为 384MiB 容器 / 128MiB tmpfs / 128MiB heap / 96MiB metaspace / 8 秒运行；编译仍为 15 秒、1 CPU、64 PIDs、64KiB 输出。框架依赖复制限时 30 秒，容器最长 90 秒；单文件大小上限 16MiB，以容纳可信框架 jar。process 的 JVM 预算不构成操作系统级隔离。

POM 校验使用 JDK 自带 XML 解析器，不启动 Maven；旧 Python 协调器不支持这些新任务。请使用本页的 Java 启动脚本。

Codespaces 使用 Bash：`bash scripts/start-learning.sh process`。脚本自动设置对应转发地址的精确 FRONTEND_ORIGIN；Vite 只增加当前 Codespace 的转发主机。自定义前端端口时两个终端设置同一 FRONTEND_PORT；其他地址仍需显式设置精确 FRONTEND_ORIGIN。
