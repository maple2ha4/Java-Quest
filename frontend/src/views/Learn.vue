<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  Play,
  RotateCcw,
  Lightbulb,
  ChevronDown,
  FileCode2,
  Plus,
  Save,
  NotebookPen,
  LoaderCircle,
  XCircle,
  Lock,
} from 'lucide-vue-next'
import { useQuest } from '../store'
import { exerciseKinds, buildKinds } from '../courseLabels'
import { api, message } from '../api'
import type { SourceFile, RunResponse, JudgeResult, Note } from '../types'
const CodeEditor = defineAsyncComponent(() => import('../components/CodeEditor.vue'))
const route = useRoute(),
  router = useRouter(),
  quest = useQuest()
const lesson = computed(() => quest.lessons.find((l) => l.id === route.params.id))
const context = computed(() => quest.context(String(route.params.id)))
const topicLessons = computed(() => quest.topicLessons(String(route.params.id)))
const preview = computed(() => route.query.preview === '1')
const unlocked = computed(() => !!lesson.value && (preview.value || quest.isUnlocked(lesson.value)))
const readOnly = computed(
  () =>
    !!lesson.value?.starter.some((f) => f.name === active.value) &&
    !lesson.value?.editableFiles.includes(active.value),
)
const files = ref<SourceFile[]>([]),
  active = ref('Main.java'),
  loading = ref(true),
  running = ref(false),
  coolingDown = ref(false),
  error = ref(''),
  saveState = ref(''),
  hints = ref(0),
  result = ref<JudgeResult | null>(null),
  awarded = ref(0),
  note = ref(''),
  noteStatus = ref(''),
  newFile = ref(''),
  showNewFile = ref(false),
  resetConfirm = ref(false),
  removeConfirm = ref(false)
const guestDraft = ref<SourceFile[] | null>(null)
const file = computed(() => files.value.find((f) => f.name === active.value))
const topicStep = computed(() => topicLessons.value.findIndex((l) => l.id === lesson.value?.id) + 1)
const next = computed(() => {
  if (!preview.value) return quest.nextForLesson(String(route.params.id))
  const ids = quest.curriculum.flatMap((s) => s.sampleLessonIds),
    index = ids.indexOf(String(route.params.id))
  return quest.lessonById(ids[index + 1] || '')
})
let timer: ReturnType<typeof setTimeout> | undefined,
  runCooldownTimer: ReturnType<typeof setTimeout> | undefined,
  generation = 0
const statuses: Record<string, string> = {
  PASSED: '全部测试通过',
  WRONG_ANSWER: '输出与目标不一致',
  COMPILE_ERROR: '编译没有通过',
  RUNTIME_ERROR: '运行时发生错误',
  TIME_LIMIT: '超过时间或资源限制',
  OUTPUT_LIMIT: '输出超过限制',
}
const explanations: Record<string, string> = {
  WRONG_ANSWER: '程序运行了，但结果还不符合任务。对照预期输出检查大小写、空格和标点。',
  COMPILE_ERROR: '编译器还不能理解这段代码。检查错误中的文件名和行号，留意分号、引号和括号。',
  RUNTIME_ERROR: '程序编译成功，但执行时退出。查看 stderr 中的异常名称与行号。',
  TIME_LIMIT: '程序没有在预算内完成，也可能超出容器资源限制。检查无限循环、线程或大对象。',
  OUTPUT_LIMIT: '程序输出超过 64KiB，执行已终止。检查循环中的打印。',
}
function applyRunCooldown(until: number) {
  clearTimeout(runCooldownTimer)
  const remaining = Math.min(2200, Math.max(0, until - Date.now()))
  coolingDown.value = remaining > 0
  if (remaining > 0)
    runCooldownTimer = setTimeout(() => {
      coolingDown.value = false
    }, remaining)
}
function restoreRunCooldown() {
  let until = 0
  try {
    until = Number(sessionStorage.getItem(`quest-next-run:${quest.user?.id || 'guest'}`)) || 0
  } catch {
    /* Browser storage is optional. */
  }
  applyRunCooldown(until)
}
function localKey(id: string, user = quest.user?.id) {
  return `quest-draft:${user || 'guest'}:${id}`
}
async function load() {
  const ticket = ++generation
  clearTimeout(timer)
  loading.value = true
  restoreRunCooldown()
  guestDraft.value = null
  error.value = ''
  result.value = null
  hints.value = 0
  awarded.value = 0
  saveState.value = ''
  note.value = ''
  noteStatus.value = ''
  active.value = lesson.value?.editableFiles[0] || 'Main.java'
  resetConfirm.value = false
  removeConfirm.value = false
  if (!lesson.value) {
    loading.value = false
    return
  }
  const id = lesson.value.id
  let initial = lesson.value.starter.map((f) => ({ ...f }))
  if (quest.user) {
    try {
      initial = await api<SourceFile[]>('/drafts/' + id)
      const notes = await api<Note[]>('/notes')
      if (ticket !== generation) return
      note.value = notes.find((n) => n.lessonId === id)?.body || ''
    } catch (e) {
      if (ticket !== generation) return
      error.value = message(e)
    }
  }
  if (ticket !== generation) return
  try {
    if (quest.user) {
      const anonymous = localStorage.getItem(`quest-draft:guest:${id}`)
      if (anonymous) {
        const candidate = JSON.parse(anonymous)
        if (
          Array.isArray(candidate) &&
          candidate.every((f) => typeof f.name === 'string' && typeof f.content === 'string')
        )
          guestDraft.value = candidate
      }
    }
    const local = localStorage.getItem(localKey(id))
    if (local) {
      const draft = JSON.parse(local)
      if (
        Array.isArray(draft) &&
        draft.every((f) => typeof f.name === 'string' && typeof f.content === 'string')
      ) {
        initial = draft
        saveState.value = '已恢复本机草稿'
      }
    }
  } catch {
    saveState.value = '本机草稿无法读取，已使用服务器草稿'
  }
  files.value = initial
  loading.value = false
}
watch([() => route.params.id, () => route.query.preview, () => quest.user?.id], load, {
  immediate: true,
})
watch(
  files,
  () => {
    if (loading.value || !lesson.value) return
    result.value = null
    awarded.value = 0
    const id = lesson.value.id,
      userId = quest.user?.id,
      snapshot = JSON.stringify(files.value)
    try {
      localStorage.setItem(localKey(id, userId), snapshot)
      saveState.value = '已保存到本机'
    } catch {
      saveState.value = '本机存储不可用，请保存到云端'
    }
    clearTimeout(timer)
    if (userId) timer = setTimeout(() => saveDraft(id, userId, snapshot), 900)
  },
  { deep: true, flush: 'sync' },
)
async function saveDraft(
  id = lesson.value?.id,
  userId = quest.user?.id,
  snapshot = JSON.stringify(files.value),
) {
  if (!id || !userId) return
  if (userId !== quest.user?.id) return
  try {
    await api('/drafts/' + id, {
      method: 'PUT',
      body: JSON.stringify({ files: JSON.parse(snapshot) }),
    })
    if (id === lesson.value?.id) {
      saveState.value = '已保存到服务器'
      if (localStorage.getItem(localKey(id, userId)) === snapshot)
        localStorage.removeItem(localKey(id, userId))
    }
  } catch (e) {
    if (id === lesson.value?.id) saveState.value = '服务器保存失败：' + message(e)
  }
}
function importGuestDraft() {
  if (!guestDraft.value || !lesson.value) return
  files.value = guestDraft.value.map((f) => ({ ...f }))
  active.value = lesson.value.editableFiles[0] || files.value[0]?.name || 'Main.java'
  guestDraft.value = null
  try {
    localStorage.removeItem(`quest-draft:guest:${lesson.value.id}`)
  } catch {
    /* Keep personal in-memory draft. */
  }
}
async function run() {
  if (!quest.judgeEnabled) {
    error.value = '当前未启用隔离判题服务。代码可编辑并保存，但不会在本机执行。'
    return
  }
  if (!quest.user) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!lesson.value || !unlocked.value || running.value || coolingDown.value) return
  running.value = true
  // The API permits one submission every two seconds; native execution can finish sooner.
  const nextRun = Date.now() + 2200
  try {
    sessionStorage.setItem(`quest-next-run:${quest.user.id}`, String(nextRun))
  } catch {
    /* Timer still works without storage. */
  }
  applyRunCooldown(nextRun)
  error.value = ''
  result.value = null
  awarded.value = 0
  const id = lesson.value.id,
    userId = quest.user.id,
    ticket = generation
  try {
    const response = await api<RunResponse>('/submissions', {
      method: 'POST',
      body: JSON.stringify({ lessonId: id, files: files.value, preview: preview.value }),
    })
    if (userId === quest.user?.id) quest.user = response.profile
    if (ticket === generation) {
      result.value = response.result
      awarded.value = response.awardedXp
    }
  } catch (e) {
    if (ticket === generation) error.value = message(e)
  } finally {
    running.value = false
  }
}
function reset() {
  if (!lesson.value) return
  files.value = lesson.value.starter.map((f) => ({ ...f }))
  active.value = lesson.value.editableFiles[0] || 'Main.java'
  result.value = null
  resetConfirm.value = false
}
function addFile() {
  const name = newFile.value.trim()
  if (
    name.length > 96 ||
    !/^(?:[A-Za-z_][A-Za-z0-9_]{0,30}\/){0,8}(?:[A-Za-z_][A-Za-z0-9_]{0,60}\.java|pom\.xml)$/.test(
      name,
    ) ||
    (name.endsWith('.xml') && lesson.value?.buildKind !== 'pom-check') ||
    files.value.some((f) => f.name.toLowerCase() === name.toLowerCase()) ||
    files.value.length >= 8
  ) {
    error.value =
      '使用安全的 Java 路径，例如 src/main/java/demo/Helper.java；POM 课程还允许 pom.xml（最多 8 个文件）'
    return
  }
  const packageName = name
    .replace(/^src\/main\/java\//, '')
    .split('/')
    .slice(0, -1)
    .join('.')
  files.value.push({
    name,
    content: name.endsWith('pom.xml')
      ? '<project xmlns="http://maven.apache.org/POM/4.0.0">\n  <modelVersion>4.0.0</modelVersion>\n</project>\n'
      : `${packageName ? 'package ' + packageName + ';\n\n' : ''}public class ${name.split('/').pop()!.slice(0, -5)} {\n\n}\n`,
  })
  active.value = name
  newFile.value = ''
  showNewFile.value = false
  error.value = ''
}
function removeFile() {
  files.value = files.value.filter((f) => f.name !== active.value)
  active.value = lesson.value?.editableFiles[0] || files.value[0]?.name || 'Main.java'
  removeConfirm.value = false
  result.value = null
}
async function saveNote() {
  if (!lesson.value || !quest.user) return
  try {
    await api('/notes/' + lesson.value.id, {
      method: 'PUT',
      body: JSON.stringify({ body: note.value }),
    })
    noteStatus.value = '笔记已保存'
  } catch (e) {
    noteStatus.value = message(e)
  }
}
onBeforeRouteUpdate(() => {
  if (running.value) return false
})
onBeforeRouteLeave(() => {
  if (running.value) return false
  if (quest.user) void saveDraft()
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  clearTimeout(runCooldownTimer)
  generation++
})
</script>
<template>
  <div v-if="!lesson" class="load-state">
    <h2>这条路线还不存在</h2>
    <RouterLink to="/map">回到课程地图</RouterLink>
  </div>
  <div v-else class="learn-page">
    <div class="learn-heading">
      <RouterLink to="/map" class="subtle-link"
        ><ArrowLeft :size="16" /> {{ context?.chapter.title || '课程地图' }}</RouterLink
      ><span
        >本知识点已开放第 {{ topicStep }} / {{ topicLessons.length }} 步
        <span class="pill">{{ preview ? '体验 · 不计 XP' : `+${lesson.xp} XP` }}</span></span
      >
    </div>
    <div v-if="preview" class="preview-notice" role="status">
      课程体验：可跳过前置关卡，真实验证，但不计
      XP、通关或提交历史。与正式学习共用本关草稿。<RouterLink to="/samples"
        >返回全部样例</RouterLink
      >
    </div>
    <div class="learn-layout">
      <aside class="lesson-nav">
        <span class="eyebrow">{{ context?.stage.title }}</span>
        <h3>{{ context?.topic.title }}</h3>
        <RouterLink
          v-for="l in topicLessons"
          :key="l.id"
          :to="{ path: '/learn/' + l.id, query: preview ? { preview: '1' } : {} }"
          :class="{ current: l.id === lesson.id, completed: quest.completed.includes(l.id) }"
          :aria-disabled="running"
          ><span
            ><Check v-if="quest.completed.includes(l.id)" :size="14" /><template v-else>{{
              String(l.order).padStart(2, '0')
            }}</template></span
          >{{ l.shortTitle }}</RouterLink
        >
        <RouterLink :to="{ path: '/map', query: { stage: context?.stage.id } }" class="subtle-link"
          >本章与后续路线 <ArrowRight :size="14"
        /></RouterLink>
        <div class="lesson-nav-note">
          <Lightbulb :size="18" />
          <p>卡住也没关系。<br />提示，随时为你准备。</p>
        </div>
      </aside>
      <section class="lesson-content">
        <div class="eyebrow">{{ lesson.concept.toUpperCase() }} · {{ lesson.minutes }} MIN</div>
        <h1>{{ lesson.title }}</h1>
        <p class="lesson-summary">{{ lesson.summary }}</p>
        <div class="lesson-tags">
          <span class="pill">{{ exerciseKinds[lesson.kind] || lesson.kind }}</span
          ><span class="pill"
            >{{ lesson.starter.length }} 文件 ·
            {{ buildKinds[lesson.buildKind] || lesson.buildKind }} · {{ lesson.entryClass }}</span
          >
        </div>
        <p v-if="!unlocked" class="scope-note">
          提交前需完成：{{
            lesson.prerequisites
              .filter((id) => !quest.completed.includes(id))
              .map((id) => quest.lessonById(id)?.title)
              .join('、')
          }}
        </p>
        <div class="lesson-objective">
          <span class="mini-label">本关目标</span>
          <p>{{ lesson.task }}</p>
        </div>
        <h3>先理解，再动手</h3>
        <p
          v-for="(paragraph, index) in lesson.paragraphs"
          :key="paragraph"
          class="teaching-paragraph"
        >
          <span>{{ index + 1 }}</span
          >{{ paragraph }}
        </p>
        <div class="hint-box">
          <button
            @click="hints = Math.min(hints + 1, lesson.hints.length)"
            :disabled="hints >= lesson.hints.length"
          >
            <Lightbulb :size="17" /><span>{{ hints ? '再给我一点提示' : '需要一点提示？' }}</span
            ><small>{{ hints }} / {{ lesson.hints.length }}</small
            ><ChevronDown :size="15" />
          </button>
          <p v-for="(hint, index) in lesson.hints.slice(0, hints)" :key="hint">
            <strong>提示 {{ index + 1 }}</strong
            >{{ hint }}
          </p>
        </div>
        <details class="note-editor">
          <summary><NotebookPen :size="16" /> 记下你的理解</summary>
          <template v-if="quest.user">
            <textarea
              v-model="note"
              maxlength="10000"
              placeholder="这次练习，我学到了…"
              aria-label="本关学习笔记"
            /><button class="secondary" @click="saveNote">保存笔记</button
            ><small role="status">{{ noteStatus }}</small></template
          >
          <p v-else>登录后即可保存笔记。</p>
        </details>
        <p v-if="lesson.buildKind === 'pom-check'" class="scope-note">
          本关实际解析并校验 POM 模型，不调用 Maven，不构建 jar。只读校验程序不能修改。
        </p>
        <p v-else-if="lesson.buildKind === 'spring-component'" class="scope-note">
          使用真实 Spring 容器或校验器，不启动完整 Spring Boot HTTP 服务。依赖由可信判题 jar
          提供，无运行时下载。
        </p>
        <p v-else class="scope-note">
          本关验证程序输出；暂不检查源码是否使用指定语法。尝试按任务完成，才能学到背后的思路。
        </p>
      </section>
      <section class="coding-panel">
        <div v-if="loading" class="load-state">正在恢复草稿…</div>
        <template v-else
          ><div v-if="guestDraft" class="inline-confirm">
            发现未登录时的草稿。导入会替换当前代码。
            <button @click="importGuestDraft">导入未登录草稿</button>
            <button @click="guestDraft = null">暂不导入</button>
          </div>
          <div class="editor-toolbar">
            <span><FileCode2 :size="16" /> 代码实验室</span>
            <div>
              <button
                class="icon-button"
                aria-label="保存草稿"
                :disabled="!quest.user || running"
                @click="saveDraft()"
              >
                <Save :size="16" /></button
              ><button
                class="icon-button"
                aria-label="重置代码"
                :disabled="running"
                @click="resetConfirm = !resetConfirm"
              >
                <RotateCcw :size="16" />
              </button>
            </div>
          </div>
          <div v-if="resetConfirm" class="inline-confirm">
            用起始代码替换当前全部文件？<button @click="reset">确认重置</button
            ><button @click="resetConfirm = false">取消</button>
          </div>
          <div class="file-tabs">
            <button
              v-for="f in files"
              :key="f.name"
              :class="{ active: f.name === active }"
              @click="active = f.name"
            >
              <FileCode2 :size="13" />{{ f.name }}</button
            ><button
              class="icon-button"
              aria-label="添加文件"
              :disabled="running"
              @click="showNewFile = !showNewFile"
            >
              <Plus :size="15" /></button
            ><button
              v-if="!lesson.starter.some((f) => f.name === active)"
              class="text-button"
              :disabled="running"
              @click="removeConfirm = !removeConfirm"
            >
              删除文件
            </button>
          </div>
          <div v-if="removeConfirm" class="inline-confirm">
            删除 {{ active }}？<button @click="removeFile">确认</button
            ><button @click="removeConfirm = false">取消</button>
          </div>
          <form v-if="showNewFile" class="new-file" @submit.prevent="addFile">
            <input v-model="newFile" placeholder="Helper.java" aria-label="新文件名" /><button
              class="secondary"
              type="submit"
            >
              添加
            </button>
          </form>
          <CodeEditor
            v-if="file"
            v-model="file.content"
            :filename="active"
            :readonly="running || readOnly"
          />
          <div class="editor-status">
            <span>{{ saveState || (quest.user ? '服务器草稿已载入' : '草稿保存在本机') }} </span
            ><span
              >{{ readOnly ? '只读调用方' : '可编辑' }} ·
              {{ active.endsWith('.xml') ? 'POM XML' : 'Java 21' }} <span class="status-dot"
            /></span>
          </div>
          <div class="run-toolbar">
            <span
              ><Lock :size="13" />
              {{
                quest.judgeMode === 'process'
                  ? '本地进程 · 未隔离'
                  : quest.judgeMode === 'docker'
                    ? 'Docker 容器 · 无网络'
                    : quest.judgeEnabled
                      ? '独立判题服务'
                      : '未连接判题服务'
              }}</span
            ><button
              class="primary"
              :disabled="running || coolingDown || !unlocked || !quest.judgeEnabled"
              @click="run"
            >
              <LoaderCircle v-if="running" :size="15" class="spin" /><Play
                v-else
                :size="15"
                fill="currentColor"
              />{{
                !quest.judgeEnabled
                  ? '判题未启用'
                  : running
                    ? '正在编译与测试…'
                    : coolingDown
                      ? '稍后可再次运行'
                      : quest.user
                        ? '运行并验证'
                        : '登录并运行'
              }}
            </button>
          </div>
          <p v-if="!unlocked" class="warning">可以预习；完成列出的前置练习后开放提交。</p>
          <div v-if="error" class="error-box" role="alert">{{ error }}</div>
          <div class="output-panel" aria-live="polite">
            <div class="output-title">
              运行结果
              <span v-if="result"
                >{{ result.tests.filter((t) => t.passed).length }} /
                {{ result.totalTests }} 测试通过</span
              ><span v-else>{{ quest.judgeEnabled ? '等待你的代码' : '仅编辑与保存' }}</span>
            </div>
            <div v-if="running" class="output-empty">
              <LoaderCircle class="spin" :size="23" />
              <p>
                {{
                  quest.judgeMode === 'process'
                    ? '正在独立 Java 进程中编译并运行…'
                    : '正在判题服务中编译并运行 Java…'
                }}
              </p>
              <small>首次运行可能需要几秒，页面会保留你的代码。</small>
            </div>
            <template v-else-if="result"
              ><div class="result-banner" :class="result.passed ? 'success' : 'failure'">
                <CheckCircle2 v-if="result.passed" :size="20" /><XCircle v-else :size="20" />
                <div>
                  <strong>{{ statuses[result.status] || result.status }}</strong>
                  <p>
                    {{ result.passed ? '你让代码真正运行起来了。' : explanations[result.status] }}
                  </p>
                </div>
              </div>
              <div v-for="(test, index) in result.tests" :key="index" class="test-output">
                <div>
                  <strong>测试 {{ index + 1 }}</strong
                  ><small>{{ test.durationMs }} ms · {{ test.passed ? '通过' : '未通过' }}</small>
                </div>
                <label>标准输出</label>
                <pre>{{ test.stdout || '（无输出）' }}</pre>
                <template v-if="test.stderr"
                  ><label>编译 / 运行诊断</label>
                  <pre class="stderr">{{ test.stderr }}</pre></template
                ><template v-if="!test.passed"
                  ><label>预期输出</label>
                  <pre>{{ test.expected }}</pre>
                </template>
              </div>
              <div v-if="result.passed" class="completion">
                <span
                  >{{ preview ? '体验验证通过' : '关卡完成' }}
                  <strong>{{
                    preview ? '未计入进度' : awarded ? `+${awarded} XP` : '已计入学习进度'
                  }}</strong></span
                ><RouterLink
                  v-if="next"
                  :to="{ path: '/learn/' + next.id, query: preview ? { preview: '1' } : {} }"
                  class="primary"
                  >{{ preview ? '下一个样例' : '下一关' }} <ArrowRight :size="16" /></RouterLink
                ><RouterLink v-else to="/profile" class="primary"
                  >查看我的收获 <ArrowRight :size="16"
                /></RouterLink></div
            ></template>
            <div v-else class="output-empty">
              <span class="terminal-prompt">&gt;_</span>
              <p>
                {{
                  quest.judgeEnabled
                    ? '每次运行，都是一次发现。'
                    : '当前为本地学习模式，不执行代码。'
                }}
              </p>
              <small>{{
                quest.judgeEnabled
                  ? '修改上方代码，点击「运行并验证」。'
                  : '草稿和笔记可以保留；接入隔离判题服务后可继续验证与通关。'
              }}</small>
            </div>
          </div></template
        >
      </section>
    </div>
  </div>
</template>
