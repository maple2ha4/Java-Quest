<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowRight,
  ArrowUpRight,
  Flame,
  Check,
  Code2,
  Layers,
  GitBranch,
  Terminal,
  Leaf,
  Zap,
  Compass,
  Clock,
  Trophy,
} from 'lucide-vue-next'
import { useQuest } from '../store'
const quest = useQuest()
const next = computed(() => quest.nextLesson)
const icons = [Code2, Layers, Terminal, Leaf, GitBranch, Zap]
const stages = computed(() =>
  quest.curriculum.map((s, i) => {
    const plans = s.chapters.flatMap((c) => c.topics.flatMap((t) => t.exercises))
    const published = plans.filter((e) => e.lessonId).length
    return {
      id: s.id,
      n: String(i + 1).padStart(2, '0'),
      name: s.title,
      tag: published ? '可学习' : '查看目录',
      text: s.goal,
      icon: icons[i],
      color: ['green', 'blue', 'peach', 'green', 'purple', 'peach'][i],
      open: published > 0,
      published,
      total: plans.length,
    }
  }),
)
</script>
<template>
  <div class="page home-page">
    <div class="welcome-line">
      <span class="eyebrow">YOUR NEXT CHAPTER</span
      ><span class="quiet"><span class="status-dot" /> 为真正的理解而学习</span>
    </div>
    <section class="greeting">
      <div>
        <h1>
          {{ quest.user ? `你好，${quest.user.name}。` : '每一行，都是进步。'
          }}<span class="greeting-leaf">✳</span>
        </h1>
        <p>
          {{
            quest.user
              ? '欢迎回来，继续把想法写成代码。'
              : '从第一声 Hello World 开始，让 Java 成为你的超能力。'
          }}
        </p>
      </div>
      <RouterLink to="/map" class="subtle-link"
        >查看学习路线 <ArrowUpRight :size="16"
      /></RouterLink>
    </section>
    <section class="dashboard-grid">
      <RouterLink :to="'/learn/' + next?.id" class="continue-card"
        ><div class="continue-copy">
          <div class="pill">
            <span class="status-dot" />
            {{ quest.completed.length ? '继续你的旅程' : '旅程，从这里开始' }}
          </div>
          <p class="chapter-kicker">
            {{ quest.context(next?.id || '')?.chapter.title || '学习路线' }} <span>/</span> 第
            {{
              String(
                quest.topicLessons(next?.id || '').findIndex((l) => l.id === next?.id) + 1,
              ).padStart(2, '0')
            }}
            关
          </p>
          <h2>{{ next?.title }}</h2>
          <p>{{ next?.summary }}</p>
          <span class="start-button"
            >{{ quest.completed.length ? '继续学习' : '开始第一关' }} <ArrowRight :size="17"
          /></span>
          <div class="continue-meta">
            <span><Clock :size="13" />约 {{ next?.minutes }} 分钟</span><span>零基础友好</span
            ><span>+{{ next?.xp }} XP</span>
          </div>
        </div>
        <div class="code-illustration" aria-hidden="true">
          <div class="floating-label"><span class="status-dot" /> 你的代码，即刻运行</div>
          <div class="code-window">
            <div class="window-bar"><i /><i /><i /><span>Main.java</span></div>
            <pre><span class="code-purple">public class</span> <span class="code-yellow">Main</span> {
  <span class="code-purple">public static void</span> main(
      String[] args) {
    System.out.<span class="code-yellow">println</span>(
      <span class="code-green">"Hello, Java Quest!"</span>
    );
  }
}</pre>
            <div class="fake-console">
              &gt; <span>Hello, Java Quest!</span><span class="cursor-block" />
            </div>
          </div>
          <div class="orbit-line" />
          <span class="decor-star">✳</span>
        </div></RouterLink
      >
      <div class="progress-card">
        <div class="card-top">
          <h3>我的学习进度</h3>
          <Compass :size="18" />
        </div>
        <div class="progress-ring" :style="{ '--progress': quest.percentage + '%' }">
          <div>
            <strong>{{ quest.percentage }}<small>%</small></strong
            ><span>持续积累中</span>
          </div>
        </div>
        <div class="progress-stats">
          <span
            ><strong
              >{{ quest.completed.length }}<small> / {{ quest.lessons.length }}</small></strong
            ><small>已通关</small></span
          ><span
            ><strong>{{ quest.user?.xp || 0 }}</strong
            ><small>累计经验 XP</small></span
          >
        </div>
        <p class="encouragement">
          {{ quest.completed.length ? '每一次尝试，都在靠近答案。' : '写下第一行，点亮你的进度。' }}
        </p>
      </div>
    </section>
    <RouterLink to="/samples" class="sample-shortcut"
      ><span>先看看每门课程如何练习</span
      ><span>六类课程 · 可直接体验 · 不计进度 <ArrowRight :size="16" /></span
    ></RouterLink>
    <section class="learning-path">
      <div class="section-heading">
        <div>
          <h2>一条路线，无限可能<span class="tiny-label">LEARNING PATH</span></h2>
          <p>循序渐进，从入门到独立构建。</p>
        </div>
        <RouterLink to="/map" class="subtle-link"
          >探索全部课程 <ArrowRight :size="16"
        /></RouterLink>
      </div>
      <div class="stage-grid">
        <component
          :is="'RouterLink'"
          v-for="stage in stages"
          :key="stage.n"
          :to="{ path: '/map', query: { stage: stage.id } }"
          class="stage-card"
          :class="{ 'stage-available': stage.open }"
          ><div class="stage-card-top">
            <span class="stage-icon" :class="stage.color"
              ><component :is="stage.icon" :size="23" /></span
            ><span class="stage-number">{{ stage.n }}</span>
          </div>
          <h3>{{ stage.name }}<ArrowUpRight v-if="stage.open" :size="17" /></h3>
          <p>{{ stage.text }}</p>
          <div class="stage-bottom">
            <span :class="stage.open ? 'green-text' : ''">{{
              stage.open
                ? `${stage.published} 已开放 / ${stage.total} 个练习`
                : `${stage.total} 个练习规划中`
            }}</span
            ><span v-if="stage.open" class="small-pill">{{ stage.tag }}</span
            ><span v-else class="quiet">{{ stage.tag }}</span>
          </div></component
        >
      </div>
    </section>
    <section class="bottom-grid">
      <div class="daily-card">
        <span class="stage-icon peach"><Flame :size="22" /></span>
        <div>
          <h3>不止是看懂，更要亲手做到。</h3>
          <p>真实 Java 环境 · 即时运行反馈 · 每一步都有提示</p>
        </div>
        <RouterLink :to="'/learn/' + next?.id" class="icon-button" aria-label="进入互动课堂"
          ><ArrowRight :size="20"
        /></RouterLink>
      </div>
      <div class="milestone-card">
        <Trophy :size="24" />
        <div>
          <h3>第一行代码，值得纪念。</h3>
          <p>
            {{
              quest.completed.includes('hello-world')
                ? '你已解锁「初次问候」成就。'
                : '通过第一关，解锁「初次问候」成就。'
            }}
          </p>
        </div>
        <Check v-if="quest.completed.includes('hello-world')" :size="20" />
      </div>
    </section>
  </div>
</template>
