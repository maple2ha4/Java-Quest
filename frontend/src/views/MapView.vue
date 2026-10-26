<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Check, Lock, ArrowRight, Clock, Code2, Search } from 'lucide-vue-next'
import { exerciseKinds } from '../courseLabels'
import { useQuest } from '../store'
const quest = useQuest(),
  route = useRoute(),
  router = useRouter(),
  query = ref(''),
  onlyAvailable = ref(false)
const selected = computed(
  () => quest.curriculum.find((s) => s.id === route.query.stage) || quest.curriculum[0],
)
const plans = computed(
  () => selected.value?.chapters.flatMap((c) => c.topics.flatMap((t) => t.exercises)) || [],
)
const chapters = computed(
  () =>
    selected.value?.chapters
      .map((c) => ({
        ...c,
        topics: c.topics.filter((t) => {
          const matches =
            !query.value ||
            [c.title, t.title, ...t.exercises.map((e) => e.title)].some((text) =>
              text.toLowerCase().includes(query.value.toLowerCase()),
            )
          return matches && (!onlyAvailable.value || t.exercises.some((e) => e.lessonId))
        }),
      }))
      .filter((c) => c.topics.length) || [],
)
function select(id: string) {
  router.replace({ path: '/map', query: { stage: id } })
}
watch(
  () => selected.value?.id,
  () => {
    query.value = ''
  },
)
</script>
<template>
  <div class="page curriculum-page">
    <span class="eyebrow">FROM FIRST LINE TO ENGINEERING</span>
    <h1>从一个知识点，到独立交付。</h1>
    <p class="page-intro">
      按阶段、章节、知识点展开。任务数量根据概念、边界、故障和工程迁移需求安排；规划中的练习不计入学习进度。
    </p>
    <RouterLink to="/samples" class="subtle-link"
      >先体验六大课程的代表练习 <ArrowRight :size="16"
    /></RouterLink>
    <nav class="stage-tabs" aria-label="课程阶段">
      <button
        v-for="(stage, i) in quest.curriculum"
        :key="stage.id"
        :class="{ active: stage.id === selected?.id }"
        @click="select(stage.id)"
      >
        {{ String(i + 1).padStart(2, '0') }} {{ stage.title }}
      </button>
    </nav>
    <div class="map-banner">
      <span class="stage-icon green"><Code2 :size="25" /></span>
      <div>
        <h2>{{ selected?.title }}</h2>
        <p>{{ selected?.goal }}</p>
      </div>
      <span class="pill"
        >{{ plans.filter((e) => e.lessonId).length }} 已开放 / {{ plans.length }} 练习标题</span
      >
    </div>
    <div class="catalog-tools">
      <label
        ><Search :size="16" /><input
          v-model="query"
          aria-label="搜索当前阶段"
          placeholder="搜索章节、知识点或练习…" /></label
      ><label class="availability-filter"
        ><input v-model="onlyAvailable" type="checkbox" />仅看已开放知识点</label
      >
    </div>
    <p v-if="!chapters.length" class="empty-card">
      当前筛选下没有知识点。可以关闭筛选或更换关键词。
    </p>
    <section v-for="(chapter, ci) in chapters" :key="chapter.id" class="chapter-block">
      <div class="chapter-heading">
        <span>{{ String(ci + 1).padStart(2, '0') }}</span>
        <div>
          <h2>{{ chapter.title }}</h2>
          <small>{{ chapter.topics.length }} 个知识点</small>
        </div>
      </div>
      <details
        v-for="topic in chapter.topics"
        :key="topic.id"
        class="topic-block"
        :open="!!query || topic.exercises.some((e) => e.lessonId)"
      >
        <summary>
          <div>
            <h3>{{ topic.title }}</h3>
            <p>{{ topic.goal }}</p>
          </div>
          <span class="pill"
            >{{ topic.exercises.filter((e) => e.lessonId).length }} /
            {{ topic.exercises.length }} 已开放</span
          >
        </summary>
        <p class="practice-rationale">练习安排：{{ topic.practiceRationale }}</p>
        <div class="exercise-list">
          <component
            :is="exercise.lessonId ? 'RouterLink' : 'div'"
            v-for="(exercise, ei) in topic.exercises"
            :key="exercise.id"
            :to="exercise.lessonId ? '/learn/' + exercise.lessonId : undefined"
            class="exercise-row"
            :class="{
              planned: !exercise.lessonId,
              done: exercise.lessonId && quest.completed.includes(exercise.lessonId),
            }"
            ><span class="exercise-marker"
              ><Check
                v-if="exercise.lessonId && quest.completed.includes(exercise.lessonId)"
                :size="18" /><Lock
                v-else-if="
                  !exercise.lessonId || !quest.isUnlocked(quest.lessonById(exercise.lessonId)!)
                "
                :size="16" /><Code2 v-else :size="18"
            /></span>
            <div>
              <small
                >第 {{ ei + 1 }} 练 · {{ exerciseKinds[exercise.kind] || exercise.kind }}</small
              >
              <h3>{{ exercise.title }}</h3>
              <p v-if="exercise.lessonId">
                {{ quest.lessonById(exercise.lessonId)?.starter.length }} 个源码文件 ·
                {{ quest.lessonById(exercise.lessonId)?.minutes }} 分钟 · +{{
                  quest.lessonById(exercise.lessonId)?.xp
                }}
                XP
              </p>
            </div>
            <span v-if="!exercise.lessonId" class="quiet">内容规划中</span
            ><span v-else-if="quest.completed.includes(exercise.lessonId)" class="green-text"
              >已通关</span
            ><span v-else class="quiet">{{
              quest.isUnlocked(quest.lessonById(exercise.lessonId)!)
                ? '开始练习'
                : '可预习 · 前置未完成'
            }}</span
            ><ArrowRight v-if="exercise.lessonId" :size="17"
          /></component>
        </div>
      </details>
    </section>
    <div class="info-banner">
      目录展示完整学习范围；“已开放”才有讲解、起始源码和真实测试。高级课程将按章节逐步交付。
    </div>
  </div>
</template>
