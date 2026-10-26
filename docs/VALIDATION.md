# 阶段 1 验证记录

日期：2026-10-21。平台：当前 Linux 云环境；不是 Windows 实机或生产部署认证。所有下面的“通过”均对应实际运行，不使用静态原型或假判题代替功能。

## 完成范围

Vue 首页、课程地图、三栏互动课堂、个人中心、错题/笔记、项目规划页，深浅色与移动布局。Java 基础 10 关：输出、变量、类型、运算符、条件、循环、数组、方法、字符串、异常。每关有讲解、任务、三级提示、starter 与输出测试。Monaco 多文件、草稿自动保存/手动保存/重置；账户、会话、首次通关、XP、提交和笔记均持久化 MySQL。

独立 Python 判题协调器创建 Java 21 Docker 沙盒。容器无外部网络，非 root，只读根文件系统，限制 CPU、内存、进程、输出和时间；未在 API 主进程或宿主直接运行学习者代码。

## 结果

| 检查 | 结果 | 证据与边界 |
|---|---|---|
| Maven verify / Java 21 | 通过 | 7 项集成测试，0 失败、0 错误、0 跳过；可运行 Spring Boot jar |
| 前端 build | 通过 | vue-tsc 类型检查 + Vite 生产打包；编辑器按需加载 |
| 判题输入校验 | 通过 | Python unittest 3 项：目录穿越/重复名/体积、Main/tests 约束、输出规范化 |
| 真实 API + MySQL + Docker 黑盒 | 通过 | scripts/smoke.py，16 次真实容器提交，10 关全部通过、累计 430 XP |
| 失败反馈 | 通过 | 错误输出、编译错误、无限循环、抛异常、无限输出分别得到正确状态；失败不加进度 |
| 沙盒运行检查 | 通过 | Java 进程检查 UID=65534、无非回环网卡、无法写根路径、未获得 DB_PASSWORD |
| 多文件 | 通过 | Main.java 调用 Helper.java，在容器中共同编译执行 |
| 进度和草稿 | 通过 | 首次 +30 XP、重复 +0、注销/重新登录后保持；第二关可用、草稿/笔记可读 |
| 服务故障保护 | 通过 | API 集成测试中判题 503 或空测试伪成功均返回 503，不写通关；该项使用明确的契约测试替身 |
| 浏览器真实学习闭环 | 通过 | Playwright Chromium：未登录草稿 → UI 注册 → 明确导入草稿 → 错误输出 → 编辑编译错误 → 修正 → 草稿刷新 → 真运行 → 首次 +30 → 下一关 → 刷新 → 查看档案与笔记/错题 |
| 浏览器外观与导航 | 通过 | 第二项 Playwright：六个页面、主题保留、390px 无水平溢出、锁定关卡禁运行；两项均无 pageerror |
| OpenAPI | 通过 | /v3/api-docs 成功返回 10 个路径（同路径多个 HTTP 方法），Swagger UI 由 springdoc 提供 |
| 测试容器清理 | 通过 | 检查没有遗留 jq-* 容器 |

后端自动化测试使用 H2/MySQL 模式与明确标注的判题契约替身；它们验证 API 和事务语义。真实编译/执行、资源反馈和 MySQL 迁移另由黑盒检查验证，二者不混淆。

## 环境版本与构建证据

- Temurin JDK 21.0.12.1+1，Maven 3.9.11，Node 24.19.0，Python 3.12，Docker Engine 28.4.0 / Compose 2.40.3。
- Spring Boot 3.5.7；前端精确依赖记录在 package-lock.json（首次安装后可用 npm ci 复现）。
- eclipse-temurin:21-jdk 本次拉取 digest：`sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d`。
- mysql:8.4 本次 digest：`sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364`。
- Flyway 对 MySQL 8.4 给出版本支持范围警告，本次迁移与真实数据操作均成功；生产升级前仍需数据库兼容回归。
- 构建代理使用环境受信任 CA；未禁用 TLS 或 checksum 验证。JDK/Maven 下载已校验 SHA-256/SHA-512。
- 截图在被忽略的 `.artifacts/`：home-desktop、hello-desktop、hello-completed、hello-dark、home-mobile、lesson-mobile。

## 未实现或未验证

Windows PowerShell 已有配置与启动说明，但未做 Windows 实机测试。尚无公开部署、跨任务恢复验证、压力测试、逃逸攻击测试或强多租户沙盒认证。源码语义验证、对象专题、Maven/Spring Boot/并发课程、动态可视化和项目验收属于后续阶段；目前入口明确标记规划中。输出练习可硬编码通过，不能宣传为防作弊评测。没有有效学习时长、课程管理、邮件验证/找回密码或完整生产监控。

本次停止在阶段 1 交付，下一阶段需用户确认。
