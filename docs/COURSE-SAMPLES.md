# 先看每门课程的实际练习

启动后进入导航「课程体验」或 `/samples`。六类课程各提供三个代表样例，共 18 个；这里的精选数量不是正式知识点的题数限制。课程地图展示按学习目标配置的 3–9 项递进练习，并显示每项开放状态。

不登录可阅读和编辑。登录并启用 process/docker 后可真实运行；带 `?preview=1` 的体验跳过前置关卡，不写 XP、通关或提交历史。正式学习仍执行前置检查。体验与正式课共用本关草稿，不会另存一份答案。

## 已开放样例

| 课程 | 实际任务 | 体验路径 |
|---|---|---|
| Java 语言与程序设计 | 用短路条件保护空值 | `/learn/null-short-circuit?preview=1` |
| Java 语言与程序设计 | 区分 UTF-16 长度与字符数量 | `/learn/unicode-count?preview=1` |
| Java 语言与程序设计 | 用 try-with-resources 保证关闭 | `/learn/resource-closing?preview=1` |
| 集合、数据结构与算法 | 实现大小写无关的词频报表 | `/learn/word-report?preview=1` |
| 集合、数据结构与算法 | 删除满足条件的元素而不跳项 | `/learn/list-remove?preview=1` |
| 集合、数据结构与算法 | 验证嵌套括号与提前闭合 | `/learn/parentheses-check?preview=1` |
| Maven 与工程化 | 声明编译目标 JDK 21 | `/learn/pom-release?preview=1` |
| Maven 与工程化 | 将 JUnit 声明为测试依赖 | `/learn/pom-test-dependency?preview=1` |
| Maven 与工程化 | 校验根模块与子模块的坐标关系 | `/learn/pom-modules?preview=1` |
| Spring Boot 与服务端工程 | 修复构造器参数未保存 | `/learn/spring-constructor?preview=1` |
| Spring Boot 与服务端工程 | 在多个实现之间指定 Qualifier | `/learn/spring-qualifier?preview=1` |
| Spring Boot 与服务端工程 | 组合字段约束与错误报告 | `/learn/spring-validation?preview=1` |
| 并发、JVM 与性能诊断 | 等待线程完成后汇总 | `/learn/thread-join?preview=1` |
| 并发、JVM 与性能诊断 | 修复确定性丢失更新 | `/learn/atomic-lost-update?preview=1` |
| 并发、JVM 与性能诊断 | 实现可重复验证的并发计数 | `/learn/atomic-counter?preview=1` |
| 高级工程实践与项目交付 | 修复无版本检查的库存扣减 | `/learn/stock-version?preview=1` |
| 高级工程实践与项目交付 | 实现有界任务接收与拒绝 | `/learn/bounded-work-queue?preview=1` |
| 高级工程实践与项目交付 | 缓存重复请求的首次响应 | `/learn/idempotent-ledger?preview=1` |

## 验证边界

- POM 任务使用禁用外部实体/DTD 的 JDK XML 解析器，读取实际编辑的根/子模块 POM 并校验属性、依赖范围和坐标。未执行 Maven 生命周期、解析任意外部依赖或生成 jar。
- Spring 任务实际创建 AnnotationConfigApplicationContext，验证构造器注入和 Qualifier；Bean Validation 任务运行真实 Hibernate Validator。可信依赖来自构建好的 judge jar，不在判题时下载。尚不是启动 Spring Boot Web 应用并发送 HTTP 请求。
- 并发样例通过 join、屏障、原子更新验证确定性行为，没有线程时间线或锁动画。
- 库存、有界任务接收和幂等响应使用内存模型、多组输入和边界验收。没有把它们称为数据库事务、分布式系统或生产业务服务。
- Java/集合/业务题实际 javac 编译并比较执行结果；只读驱动由 API 保护。按行为判题仍可能被硬编码通过，不是源码语义/防作弊保证。

当前开放 65 个练习，完整目录有 600 个标题，其余 535 项明确标记规划。完整细目见 [COURSE-ROADMAP.md](COURSE-ROADMAP.md)。
