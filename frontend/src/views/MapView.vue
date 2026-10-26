<script setup lang="ts">
import { Check, Lock, ArrowRight, Clock, Code2 } from 'lucide-vue-next'
import { useQuest } from '../store'
const quest = useQuest()
function unlocked(order: number) {
  return order === 1 || quest.completed.includes(quest.lessons[order - 2]!.id)
}
</script>
<template>
  <div class="page map-page">
    <span class="eyebrow">THE LEARNING MAP</span>
    <h1>把好奇，写成能力。</h1>
    <p class="page-intro">一个概念，一次练习，一步一个脚印。</p>
    <div class="map-banner">
      <span class="stage-icon green"><Code2 :size="26" /></span>
      <div>
        <h2>第一章 · Java 基础</h2>
        <p>10 个互动关卡 · 从输出到异常处理</p>
      </div>
      <span class="pill">{{ quest.completed.length }} / 10 已完成</span>
    </div>
    <div class="map-list">
      <RouterLink
        v-for="lesson in quest.lessons"
        :key="lesson.id"
        :to="'/learn/' + lesson.id"
        class="map-row"
        :class="{ done: quest.completed.includes(lesson.id), locked: !unlocked(lesson.order) }"
        ><span class="map-node"
          ><Check v-if="quest.completed.includes(lesson.id)" :size="21" /><Lock
            v-else-if="!unlocked(lesson.order)"
            :size="18"
          /><span v-else>{{ String(lesson.order).padStart(2, '0') }}</span></span
        >
        <div>
          <div class="eyebrow">{{ lesson.concept }}</div>
          <h3>{{ lesson.title }}</h3>
          <p>{{ lesson.summary }}</p>
        </div>
        <div class="map-row-meta">
          <span><Clock :size="13" />{{ lesson.minutes }} 分钟</span><span>+{{ lesson.xp }} XP</span
          ><small>{{
            quest.completed.includes(lesson.id)
              ? '已通关'
              : unlocked(lesson.order)
                ? '可以开始'
                : '完成上一关后解锁提交'
          }}</small>
        </div>
        <ArrowRight :size="19"
      /></RouterLink>
    </div>
    <div class="info-banner">下一站：集合与数据结构。后续课程正在设计中，当前尚未开放。</div>
  </div>
</template>
