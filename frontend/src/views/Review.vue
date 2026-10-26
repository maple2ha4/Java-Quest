<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ArrowRight, NotebookPen, RotateCcw } from 'lucide-vue-next'
import { useQuest } from '../store'
import { api, message } from '../api'
import type { Submission, Note } from '../types'
const quest = useQuest(),
  tab = ref('mistakes'),
  history = ref<Submission[]>([]),
  notes = ref<Note[]>([]),
  error = ref(''),
  loading = ref(true)
const failed = computed(() => history.value.filter((s) => s.status !== 'PASSED'))
async function load() {
  error.value = ''
  loading.value = true
  try {
    ;[history.value, notes.value] = await Promise.all([
      api<Submission[]>('/submissions'),
      api<Note[]>('/notes'),
    ])
  } catch (e) {
    error.value = message(e)
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  if (quest.user) load()
  else loading.value = false
})
function title(id: string) {
  return quest.lessons.find((l) => l.id === id)?.title || id
}
</script>
<template>
  <div class="page">
    <span class="eyebrow">REFLECT & GROW</span>
    <h1>回头看，也是在向前。</h1>
    <p class="page-intro">把一次错误，变成下一次理解的起点。</p>
    <div v-if="!quest.user" class="empty-card">
      <NotebookPen :size="30" />
      <h2>你的复习空间，等你登录</h2>
      <RouterLink to="/login" class="primary">登录并查看 <ArrowRight :size="16" /></RouterLink>
    </div>
    <template v-else
      ><div class="tab-switch">
        <button :class="{ active: tab === 'mistakes' }" @click="tab = 'mistakes'">
          错题记录 <span>{{ failed.length }}</span></button
        ><button :class="{ active: tab === 'notes' }" @click="tab = 'notes'">
          学习笔记 <span>{{ notes.length }}</span>
        </button>
      </div>
      <div v-if="error" class="error-box">
        {{ error }}<button class="text-button" @click="load">重试</button>
      </div>
      <p v-else-if="loading" class="empty-card">正在加载学习记录…</p>
      <template v-else-if="tab === 'mistakes'"
        ><div v-if="!failed.length" class="empty-card">
          <RotateCcw :size="30" />
          <h2>这里暂时没有错误记录</h2>
          <p>每一次真实的失败提交都会自动记录在这里。</p>
        </div>
        <article v-for="item in failed" :key="item.id" class="review-card">
          <div>
            <h3>{{ title(item.lessonId) }}</h3>
            <span class="pill">{{ item.status }}</span>
          </div>
          <p>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</p>
          <pre>{{
            item.result.tests[0]?.stderr || item.result.tests[0]?.stdout || '程序没有输出'
          }}</pre>
          <RouterLink :to="'/learn/' + item.lessonId" class="subtle-link"
            >回到关卡复习 <ArrowRight :size="16"
          /></RouterLink>
        </article>
        <p class="scope-note">
          显示最近 50 次提交中的失败记录；完整历史与复习计划将在后续阶段扩展。
        </p></template
      ><template v-else
        ><div v-if="!notes.length" class="empty-card">
          <h2>让理解，留下痕迹。</h2>
          <p>在互动课堂展开「记下你的理解」，保存你的第一条笔记。</p>
        </div>
        <article v-for="note in notes" :key="note.lessonId" class="review-card">
          <h3>{{ title(note.lessonId) }}</h3>
          <p class="note-body">{{ note.body }}</p>
          <RouterLink :to="'/learn/' + note.lessonId" class="subtle-link"
            >继续编辑 <ArrowRight :size="16"
          /></RouterLink></article></template
    ></template>
  </div>
</template>
