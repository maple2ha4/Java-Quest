<script setup lang="ts">
import { useQuest } from '../store'
import { exerciseKinds, buildKinds } from '../courseLabels'
import { ArrowRight, BookOpen, FlaskConical } from 'lucide-vue-next'
const quest = useQuest()
</script>
<template>
  <div class="page samples-page">
    <span class="eyebrow">TRY BEFORE YOU FOLLOW THE PATH</span>
    <h1>先试一试，每门课怎样练。</h1>
    <p class="page-intro">
      六类课程各精选三个代表样例，先看讲解、修改代码、获得真实反馈。这里的样例数量不限制正式课程的练习数量。
    </p>
    <div class="preview-notice">
      <FlaskConical :size="20" /><span
        >体验模式可跳过前置要求；登录后可以运行，但不会增加经验或通关记录。未启用判题时可阅读和编辑。</span
      >
    </div>
    <section v-for="(stage, index) in quest.curriculum" :key="stage.id" class="sample-stage">
      <div class="section-heading">
        <div>
          <span class="eyebrow">COURSE {{ String(index + 1).padStart(2, '0') }}</span>
          <h2>{{ stage.title }}</h2>
          <p>{{ stage.goal }}</p>
        </div>
        <RouterLink :to="{ path: '/map', query: { stage: stage.id } }" class="subtle-link"
          >完整练习链 <ArrowRight :size="15"
        /></RouterLink>
      </div>
      <div class="sample-grid">
        <article v-for="id in stage.sampleLessonIds" :key="id" class="sample-card">
          <span class="pill">{{ exerciseKinds[quest.lessonById(id)!.kind] }}</span>
          <h3>{{ quest.lessonById(id)!.title }}</h3>
          <p>{{ quest.lessonById(id)!.summary }}</p>
          <div class="sample-meta">
            {{ buildKinds[quest.lessonById(id)!.buildKind] }} ·
            {{ quest.lessonById(id)!.starter.length }} 文件
          </div>
          <RouterLink :to="{ path: '/learn/' + id, query: { preview: '1' } }" class="primary"
            >体验这道练习 <ArrowRight :size="15"
          /></RouterLink>
        </article>
      </div>
    </section>
    <RouterLink to="/map" class="subtle-link"><BookOpen :size="17" />回到正式学习路线</RouterLink>
  </div>
</template>
