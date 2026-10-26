# 不安装 Docker：本地学习模式

此模式需要 **JDK 21、Maven 3.9+、Node.js 22.12+ 或 24**，不需要安装 Docker、MySQL、Python，也不需要创建 .env 或准备判题密钥。H2 数据库随后端依赖下载，无需单独安装。

可以使用：课程与讲解、Monaco 编辑、多文件、草稿保存、注册登录、学习笔记和历史记录页面。

**未接入隔离判题服务时，不能在线执行代码、自动判题或新增通关/经验。** 页面明确显示「判题未启用」，API 也会拒绝提交。此模式不在本机或 Spring Boot 进程里执行学习者代码，也不会制造通过结果。

## Windows PowerShell：只启动两个终端

如果已经克隆项目，在原项目根目录拉取更新，不要重复克隆：

```powershell
git switch java-quest-mvp
git pull --ff-only
```

首次获取：

```powershell
git clone --branch java-quest-mvp --single-branch https://github.com/maple2ha4/Java-Quest.git
cd Java-Quest
```

安装依赖并构建：

```powershell
java -version
javac -version
mvn -version
node --version
npm ci --prefix frontend
mvn -f backend/pom.xml verify
```

打开两个新的 PowerShell 终端，均进入项目根目录；**不执行 Docker 命令、不运行 judge/server.py、不加载 scripts/dev-env.ps1**。

终端 1：

```powershell
java -jar backend/target/java-quest-api-0.1.0.jar --spring.profiles.active=local
```

终端 2：

```powershell
npm run dev --prefix frontend
```

浏览器打开 http://localhost:5173，注册账户后即可阅读、编辑并保存草稿和笔记。API 本地监听 127.0.0.1:8080。

也可在项目根目录直接启动开发 API：

```powershell
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

两种后端命令任选一种，不要同时运行。Java 主目录固定用于数据存储，因此两种命令会访问同一份 H2 数据，不依赖当前工作目录。

## 数据保存在哪里

默认是当前 Java 用户主目录的 `.java-quest/javaquest.mv.db`。Windows 通常为 `C:\Users\你的用户名\.java-quest\javaquest.mv.db`。关闭前后端后再次启动，账户、会话、草稿和笔记仍在。

可在启动 API 的终端指定数据目录，例如：

```powershell
$env:QUEST_DATA_DIR='D:/JavaQuestData'
java -jar backend/target/java-quest-api-0.1.0.jar --spring.profiles.active=local
```

H2 与原 MySQL 是两个独立数据库；切换模式不会自动迁移原有学习数据。不要同时用两个 API 进程打开同一份 H2 文件。备份前先正常关闭 API，再复制数据库文件。不要将数据库文件提交到 Git。此模式不开放 H2 网页控制台。

## 判题是否可以不在本机装 Docker

可以连接**已有的、可信的独立隔离判题服务**。其接口需要符合 `judge/server.py` 的 POST /judge 协议：X-Judge-Token 认证，接收 files/tests，返回真实 status/passed/tests/totalTests，并在远端提供隔离与资源限制。不能把任意在线编译器网址当作兼容服务。

只有已具备这种服务时，在启动 API 的终端设置：

```powershell
$env:JUDGE_ENABLED='true'
# JUDGE_URL 设置成已有服务的基础地址，JUDGE_TOKEN 设置成它的真实密钥（至少 32 字符）。
# 请在本机安全配置，不要把密钥发送到聊天。
java -jar backend/target/java-quest-api-0.1.0.jar --spring.profiles.active=local
```

这只是支持已有服务的配置入口，本项目没有替你部署或提供公网判题服务。若暂时没有，保持 local 模式的默认禁用状态即可。

如果之前在终端设置过 JUDGE_ENABLED=true，要恢复纯本地学习模式，请在新终端运行或先执行：

```powershell
$env:JUDGE_ENABLED='false'
```

## 检查和测试

```powershell
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:8080/api/capabilities
# 本地默认 capabilities 返回 judgeEnabled=false；表示配置禁用，不是模拟判题成功。
npm run build --prefix frontend
mvn -f backend/pom.xml verify
```

浏览器本地模式测试（可选，需事先安装 Playwright Chromium；API/前端都启动后）：`npm run test:local --prefix frontend`。原 `test:e2e` 和 `scripts/smoke.py` 要求真实隔离判题服务，不能用它们声称纯本地模式可以通关。

首次安装仍需 Maven/npm 依赖仓库访问。Windows 命令已提供；功能验证平台为 Linux，Windows 实机验证仍未完成。
