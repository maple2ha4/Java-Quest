<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { Trophy, Code2, Sparkles, ArrowRight, Check } from 'lucide-vue-next'
import { useQuest } from '../store'
import { api, message } from '../api'
import type { Submission } from '../types'
const quest = useQuest(),
  history = ref<Submission[]>([]),
  error = ref('')
onMounted(async () => {
  if (quest.user)
    try {
      history.value = await api<Submission[]>('/submissions')
    } catch (e) {
      error.value = message(e)
    }
})
const badges = computed(() => [
  {
    name: '初次问候',
    text: '完成 Hello World',
    unlocked: quest.completed.includes('hello-world'),
    icon: Code2,
  },
  {
    name: '渐入佳境',
    text: '完成 5 个基础关卡',
    unlocked: quest.completed.length >= 5,
    icon: Sparkles,
  },
  {
    name: '基础探索者',
    text: '完成全部 10 关',
    unlocked: quest.completed.length >= 10,
    icon: Trophy,
  },
])
</script>
<template>
  <div class="page">
    <span class="eyebrow">YOUR PERSONAL JOURNEY</span>
    <h1>每一份努力，都算数。</h1>
    <p class="page-intro">看见自己走过的路，也看见下一步的可能。</p>
    <div v-if="!quest.user" class="empty-card">
      <h2>登录，开启你的学习档案</h2>
      <p>进度、经验和笔记会保存到你的账户。</p>
      <RouterLink to="/login" class="primary">前往登录 <ArrowRight :size="16" /></RouterLink>
    </div>
    <template v-else
      ><section class="profile-banner">
        <span class="avatar large">{{ quest.user.name.slice(0, 1) }}</span>
        <div>
          <h2>{{ quest.user.name }}</h2>
          <p>{{ quest.user.email }}</p>
        </div>
        <span class="pill">Java 探索者</span>
      </section>
      <div class="stats-grid">
        <div class="stat-card">
          <small>已通关关卡</small><strong>{{ quest.completed.length }}<span> / 10</span></strong>
        </div>
        <div class="stat-card">
          <small>累计经验</small><strong>{{ quest.user.xp }}<span> XP</span></strong>
        </div>
        <div class="stat-card">
          <small>真实代码提交</small><strong>{{ quest.user.submissions }}<span> 次</span></strong>
        </div>
      </div>
      <div class="section-heading">
        <h2>你的成就</h2>
        <span class="quiet">以首次通关记录解锁</span>
      </div>
      <div class="badge-grid">
        <div
          v-for="badge in badges"
          :key="badge.name"
          class="badge-card"
          :class="{ earned: badge.unlocked }"
        >
          <component :is="badge.icon" :size="30" />
          <h3>{{ badge.name }}</h3>
          <p>{{ badge.text }}</p>
          <span>{{ badge.unlocked ? '已解锁' : '待探索' }}</span>
        </div>
      </div>
      <div class="section-heading">
        <h2>最近学习记录</h2>
        <RouterLink to="/map" class="subtle-link">继续学习 <ArrowRight :size="16" /></RouterLink>
      </div>
      <p v-if="error" class="error-box">{{ error }}</p>
      <div v-if="!history.length" class="empty-card">还没有提交记录。第一关，正在等你。</div>
      <RouterLink
        v-for="item in history.slice(0, 10)"
        :key="item.id"
        :to="'/learn/' + item.lessonId"
        class="history-row"
        ><span class="stage-icon" :class="item.status === 'PASSED' ? 'green' : 'peach'"
          ><Check v-if="item.status === 'PASSED'" :size="20" /><Code2 v-else :size="20"
        /></span>
        <div>
          <strong>{{ quest.lessons.find((l) => l.id === item.lessonId)?.title }}</strong
          ><small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</small>
        </div>
        <span :class="item.status === 'PASSED' ? 'green-text' : 'quiet'">{{
          item.status === 'PASSED' ? '通关' : '继续尝试'
        }}</span></RouterLink
      >
      <p class="scope-note">当前未采集有效学习时长，因此不显示估算时间。</p></template
    >
  </div>
</template>
