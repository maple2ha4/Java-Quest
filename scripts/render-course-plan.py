"""Render the reviewable course outline from the same catalog consumed by the application."""
import json,pathlib
root=pathlib.Path(__file__).resolve().parent.parent
stages=json.loads((root/'backend/src/main/resources/curriculum.json').read_text(encoding='utf-8'))
lessons={l['id']:l for l in json.loads((root/'backend/src/main/resources/lessons.json').read_text(encoding='utf-8'))}
chapters=sum(len(s['chapters']) for s in stages);topics=sum(len(c['topics']) for s in stages for c in s['chapters']);total=sum(len(t['exercises']) for s in stages for c in s['chapters'] for t in c['topics'])
lines=['# Java Quest 完整课程目录与开放状态','',f'当前目录：{len(stages)} 阶段、{chapters} 章节、{topics} 知识点、{total} 个练习标题。其中 {len(lessons)} 个已开放，{total-len(lessons)} 个规划中。标题覆盖不等于教学和运行器已经实现。','', '练习数量按概念、边界、错误模式和迁移场景配置，不再固定三题；包含执行推演、配置校验、框架实验和工程片段。初学固定输出题用于建立语法反馈；后续题使用不同输入和边界案例，项目题保护调用方并编译整套文件。输出判题仍不能完全验证源码是否采用指定技巧，不提供防作弊承诺。','', '课程目录来自 `backend/src/main/resources/curriculum.json`；已开放教学与测试来自 `lessons.json`。运行 `python scripts/render-course-plan.py` 可重新生成此文件。','']
for i,s in enumerate(stages,1):
 lines += [f'## 阶段 {i}：{s["title"]}','',s['goal'],'']
 for j,c in enumerate(s['chapters'],1):
  lines += [f'### {i}.{j} {c["title"]}','']
  for t in c['topics']:
   lines += [f'#### {t["title"]}（{len(t["exercises"])} 项）','',t['practiceRationale'],'']
   for k,e in enumerate(t['exercises'],1):
    published=e['lessonId'] is not None
    suffix=f'（已开放，`{e["lessonId"]}`）' if published else '（规划中）'
    lines.append(f'{k}. {e["title"]}{suffix}')
   lines.append('')
lines += ['## 每课程体验入口','', '网页 /samples 为六类课程各选择三个可运行样例：Java 边界与资源、集合、POM 配置、真实 Spring 组件、线程与原子更新、内存业务契约。体验可跳过前置但不计进度，正式学习仍执行前置校验。POM 仅做受控 XML 语义检查，不运行 Maven；Spring 仅做真实容器/验证器实验，不是完整 HTTP 项目。','', '## 工程项目递进规范','', '每个工程系列采用：读调用链与契约 → 修改单个职责 → 修复跨文件缺陷 → 实现需求 → 验证边界/失败 → 工程评审。每个步骤给出与前一步正确结果衔接的独立 starter，草稿按步骤隔离保存；不把上一关用户提交隐式复制为正确代码。','', '当前订单项目实际开放 3 步、4 个 package 源文件，用 javac 真实编译整套源码：商品行小计 → 折扣服务 → 校验与报表。Application 是只读调用方，服务端拒绝修改它。', '', '后续 Maven 项目需要受控依赖仓库、冻结工具链、构建产物与 JUnit 断言；Spring 项目需要临时应用/数据库和真实 HTTP 合约验证；并发项目需要确定性同步与不变量测试，不能依赖睡眠或随机执行顺序。上述运行器未实现之前，目录保持规划状态。','', '高级工程能力通过架构决策、数据一致性、性能预算、故障恢复与交付证据验收，不能仅凭刷题数量或 XP 认定。']
(root/'docs/COURSE-ROADMAP.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(f'Rendered {len(stages)} stages / {chapters} chapters / {topics} topics / {total} titles / {len(lessons)} runnable lessons')
