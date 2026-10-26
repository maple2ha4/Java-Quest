<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  BookOpen,
  Map,
  LayoutDashboard,
  NotebookPen,
  FolderCode,
  Sun,
  Moon,
  ArrowUpRight,
  ChevronRight,
  Code2,
} from 'lucide-vue-next'
import { useQuest } from './store'
import { message } from './api'
const quest = useQuest(),
  route = useRoute(),
  router = useRouter(),
  error = ref(''),
  loading = ref(true)
const dark = ref(
  localStorage.getItem('quest-theme') === 'dark' ||
    (!localStorage.getItem('quest-theme') && matchMedia('(prefers-color-scheme: dark)').matches),
)
function applyTheme() {
  document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
}
function toggleTheme() {
  dark.value = !dark.value
  localStorage.setItem('quest-theme', dark.value ? 'dark' : 'light')
  applyTheme()
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    await quest.load()
  } catch (e) {
    error.value = message(e)
  } finally {
    loading.value = false
  }
}
async function logout() {
  try {
    await quest.logout()
    router.push('/')
  } catch (e) {
    error.value = message(e)
  }
}
onMounted(() => {
  applyTheme()
  load()
})
const nav = [
  { to: '/', label: '学习概览', icon: LayoutDashboard },
  { to: '/map', label: '课程地图', icon: Map },
  { to: '/projects', label: '项目挑战', icon: FolderCode },
  { to: '/review', label: '错题与笔记', icon: NotebookPen },
]
</script>
<template>
  <div class="app-shell" :class="{ 'learning-shell': route.path.startsWith('/learn/') }">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark"><Code2 :size="23" /></span
        ><span>Java Quest<span class="brand-dot">.</span></span></RouterLink
      >
      <div class="workspace-label">你的编程旅程 <span>LEARN BY DOING</span></div>
      <nav class="nav-list" aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          :class="{ selected: route.path === item.to }"
          ><component :is="item.icon" :size="19" />{{ item.label
          }}<ChevronRight v-if="route.path === item.to" :size="14" class="nav-chevron"
        /></RouterLink>
      </nav>
      <div class="sidebar-course">
        <div class="eyebrow">正在学习</div>
        <RouterLink :to="'/learn/' + (quest.nextLesson?.id || 'hello-world')"
          ><span class="mini-java">J</span
          ><span
            ><strong>Java 基础</strong
            ><small>{{ quest.completed.length }} / {{ quest.lessons.length }} 关已完成</small></span
          ><ChevronRight :size="15"
        /></RouterLink>
        <div class="progress-bar"><i :style="{ width: quest.percentage + '%' }" /></div>
      </div>
      <div class="sidebar-bottom">
        <div class="small-card">
          <BookOpen :size="20" /><strong>每天一小步。</strong>
          <p>让知识，变成你的能力。</p>
          <RouterLink to="/map">探索学习路线 <ArrowUpRight :size="14" /></RouterLink>
        </div>
        <RouterLink :to="quest.user ? '/profile' : '/login'" class="user-card"
          ><span class="avatar">{{ quest.user?.name.slice(0, 1) || 'J' }}</span
          ><span
            ><strong>{{ quest.user?.name || '新的探索者' }}</strong
            ><small>{{ quest.user ? '查看学习档案' : '登录，保存你的进步' }}</small></span
          ><ChevronRight :size="15"
        /></RouterLink>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <span
          ><span class="topbar-product">学习空间</span><ChevronRight :size="14" />{{
            route.path.startsWith('/learn')
              ? '互动课堂'
              : nav.find((n) => n.to === route.path)?.label ||
                (route.path === '/profile' ? '个人中心' : '欢迎来到 Java Quest')
          }}</span
        >
        <div>
          <span class="beta-tag">EARLY ACCESS</span
          ><button
            class="icon-button"
            :aria-label="dark ? '切换浅色主题' : '切换深色主题'"
            @click="toggleTheme"
          >
            <Sun v-if="dark" :size="18" /><Moon v-else :size="18" /></button
          ><button v-if="quest.user" class="text-button" @click="logout">退出</button
          ><RouterLink v-else to="/login" class="text-button"
            >登录 <ArrowUpRight :size="14"
          /></RouterLink>
        </div>
      </header>
      <main>
        <div v-if="error" class="load-state" role="alert">
          <h2>暂时无法连接学习服务</h2>
          <p>{{ error }}</p>
          <button class="primary" @click="load">重新连接</button>
        </div>
        <div v-else-if="loading" class="load-state">正在准备你的学习空间…</div>
        <template v-else>
          <div v-if="!quest.judgeEnabled" class="info-banner app-mode-notice" role="status">
            本地学习模式：隔离判题未启用。可以阅读课程、编辑代码、保存草稿和笔记，暂不能在线运行或通关。
          </div>
          <div v-if="quest.judgeMode === 'process'" class="warning app-mode-notice" role="status">
            本地进程判题：真实编译和运行，但没有文件、网络或进程隔离。只运行你自己编写、信任的代码，并遵守公司规定。
          </div>
          <RouterView />
        </template>
      </main>
      <footer v-if="!route.path.startsWith('/learn')">
        <span>Java Quest · 在实践中，学会编程。</span
        ><span>保持好奇，持续进步 <span class="status-dot" /></span>
      </footer>
    </div>
  </div>
</template>
