# API 使用说明

API 默认端口 8080，前端在开发环境通过 Vite `/api` 同源代理。请求/响应均为 JSON。运行后 `/v3/api-docs` 提供 OpenAPI，`/swagger-ui.html` 可浏览接口与请求体。写请求带 `X-Quest-Client: 1`，浏览器使用 `credentials: 'include'` 接受/发送 HttpOnly cookie；没有 localStorage token。

## 账户

`POST /api/auth/register`：`{"email":"you@example.com","name":"学习者","password":"your-own-password"}`，密码 8–72 字符且最多 72 UTF-8 字节。`POST /api/auth/login` 使用 email/password；`POST /api/auth/logout` 撤销当前 token。

成功后设置 7 天 `quest_session` cookie（HttpOnly、SameSite=Strict、Path=/api）。`GET /api/me` 返回：

```json
{"id":1,"email":"you@example.com","name":"学习者","completed":["hello-world"],"xp":30,"submissions":3,"createdAt":"2026-10-21T00:00:00.000+00:00"}
```

以上是协议示例，不是种子用户。没有默认演示账户；请自行注册。

## 课程与提交

`GET /api/lessons` 返回有序数组：id/order/title/shortTitle/summary/concept/xp/minutes/paragraphs/task/hints/starter。starter 为 `{name,content}[]`，不下发 tests。课程位于 `backend/src/main/resources/lessons.json`；新增关卡必须提供教学、起始文件与可信测试，并维护连续 order、稳定 id。

`POST /api/submissions`：

```json
{"lessonId":"hello-world","files":[{"name":"Main.java","content":"public class Main { public static void main(String[] args) { System.out.println(\"Hello, Java Quest!\"); } }"}]}
```

完成判题才返回结果：

```json
{
 "result":{"status":"PASSED","passed":true,"totalTests":1,
  "tests":[{"status":"PASSED","passed":true,"input":"","expected":"Hello, Java Quest!\n","stdout":"Hello, Java Quest!\n","stderr":"","durationMs":3000}]},
 "newlyCompleted":true,"awardedXp":30,
 "profile":{"id":1,"name":"学习者","email":"you@example.com","completed":["hello-world"],"xp":30,"submissions":1}
}
```

`durationMs` 包含创建容器、编译与执行，是端到端时间，不能当作算法耗时。结果状态：PASSED / WRONG_ANSWER / COMPILE_ERROR / RUNTIME_ERROR / TIME_LIMIT / OUTPUT_LIMIT。编译/超时等提前终止时，tests 可能少于 totalTests；未执行的测试不能计作通过。TIME_LIMIT 同时覆盖某些被终止的资源超限情形，当前不细分 OOM。stdout 比较只规范化 CRLF 与结尾空白，保留内部空格。提交结果永远来自服务器判题，不接受客户端的 passed/XP 字段。

未登录 401；上一关未过 403；每用户 2 秒内重复请求 429；判题基础设施失败 503 且不增加进度、不记录为学习错误。HTTP 200 的失败判题会记入真实学习提交。首次通过写入唯一 progress，重复通过 awardedXp=0。

`GET /api/submissions` 返回当前用户最近 50 条，含 id/lessonId/status/createdAt/result，不暴露他人的源码或历史。

## 草稿与笔记

- GET `/api/drafts/{lessonId}`：返回 `{name,content}[]`，无草稿时返回 starter。
- PUT 同地址：`{"files":[{"name":"Main.java","content":"..."}]}`，成功 `{"ok":true}`。
- GET `/api/notes`：返回 `{lessonId,body,updatedAt}[]`。
- PUT `/api/notes/{lessonId}`：`{"body":"我的理解"}`，最多 10000 字符，空白正文表示删除笔记。

所有读写都绑定当前账户。多个标签页同时保存草稿时采用最后提交生效；当前没有编辑冲突合并。

错误统一 `{"message":"可读的错误原因"}`。详细状态码见架构文档。当前 API 无版本前缀，是 0.1 本地开发协议；生产版本需正式版本化与兼容策略。

## 运行能力

GET `/api/capabilities` 返回 `{"judgeEnabled":true}` 或 `{"judgeEnabled":false}`。该字段表示是否配置启用判题，不表示远端判题服务已通过健康验证。`local` profile 默认禁用判题，读取/保存课程学习数据正常，POST /submissions 对已登录用户返回 503，不执行代码、不记录失败提交、不授予进度。

模式扩展：`/api/capabilities` 同时返回 judgeMode：off/process/docker/remote。API 依据 JUDGE_MODE 向可信协调器发送 X-Judge-Mode，Java 协调器在执行前核对；process 必须显式启动并提供相同模式。真实结果增加 mode 字段；API 拒绝与声明模式不符的 process 结果，成功仍按同一事务保存，不重复加 XP。Python Docker 协调器仍兼容（旧结果不含 mode），只接受它作为 Docker/remote 的服务，不能用作 process。运行能力字段不是健康保证。

## 课程结构和工程任务扩展

GET /api/curriculum 返回阶段/章节/知识点/练习标题，lessonId=null 表示规划，不可提交。/lessons 仅返回已开放教学，新增 topicId/kind/difficulty/prerequisites/entryClass/buildKind/editableFiles。前置关系由显式 ID 数组确定，不依赖展示顺序。旧 ID 保留。课程加载时验证目录唯一性、映射、入口、教学完整性及依赖环。

源文件支持 96 字符以内的安全相对 Java 路径（包括 src/main/java），拒绝绝对路径、反斜线、点段、设备名和大小写别名；仍限制 8 文件/32KiB 字符。课程指定入口类，不接受客户端选择入口。初始文件必须保留；未列入 editableFiles 的初始文件内容必须保持原样，API 在保存草稿和运行前均验证。额外帮助类可以添加。

Java 判题协议增加可信 entryClass，缺省 Main 兼容旧任务；-d . 编译包目录后按入口类运行。Python 旧协调器仅支持平面 Main 任务，遇到 package 项目会拒绝，需要使用新版 Java 判题 jar。目录显示的 Maven/Spring 练习尚不可执行，不能把它们当作已支持的构建器。
