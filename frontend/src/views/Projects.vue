<script setup lang="ts">
import { computed } from 'vue'
import { FolderCode, ArrowUpRight, Check, Lock } from 'lucide-vue-next'
import { useQuest } from '../store'
const quest = useQuest()
const steps = computed(() => quest.orderedLessons.filter((l) => l.topicId === 'order-project'))
const planned = [
  {
    title: '图书与借阅 API',
    level: 'Maven + Spring Boot + MySQL',
    text: '真实多模块服务、API 合约、数据库迁移与并发借阅。',
  },
  {
    title: '并发任务调度平台',
    level: '并发 + Quartz + WebSocket',
    text: '有界队列、幂等执行、取消、实时状态和故障恢复。',
  },
  {
    title: '订单系统与工程评审',
    level: '分布式一致性 + 性能 + 运维',
    text: '以业务不变量、容量预算、故障演练与架构取舍验收。',
  },
]
</script>
<template>
  <div class="page">
    <span class="eyebrow">READ · CHANGE · VERIFY</span>
    <h1>在已有工程中，完成真实改动。</h1>
    <p class="page-intro">
      先理解调用链，再修改指定类。只读入口由服务器保护，每次提交编译整个源码项目。
    </p>
    <section class="live-project">
      <div>
        <span class="pill">已开放 · Java 多文件项目</span>
        <h2>订单结算工作台</h2>
        <p>src/main/java → 领域对象 → 价格服务 → 报表输出。三步 starter 已包含前一步的正确实现。</p>
        <p class="scope-note">
          当前使用 javac 编译 package 源码，不执行 Maven 生命周期或 Spring Boot
          服务；这两类项目运行器尚在规划中。
        </p>
      </div>
      <ol>
        <li v-for="step in steps" :key="step.id">
          <RouterLink :to="'/learn/' + step.id"
            ><Check v-if="quest.completed.includes(step.id)" :size="18" /><FolderCode
              v-else
              :size="18" /><span
              ><strong>{{ step.title }}</strong
              ><small
                >{{ step.starter.length }} 个文件 · 修改
                {{ step.editableFiles.map((n) => n.split('/').pop()).join('、') }}</small
              ></span
            ><ArrowUpRight :size="17"
          /></RouterLink>
        </li>
      </ol>
    </section>
    <div class="section-heading">
      <h2>下一批工程挑战</h2>
      <RouterLink :to="{ path: '/map', query: { stage: 'engineering' } }" class="subtle-link"
        >查看完整路线 <ArrowUpRight :size="16"
      /></RouterLink>
    </div>
    <div class="project-grid">
      <article v-for="project in planned" :key="project.title" class="project-card">
        <div class="project-cover blue"><Lock :size="36" /><span>规划中 · 尚未开放运行</span></div>
        <div class="project-copy">
          <span class="eyebrow">{{ project.level }}</span>
          <h2>{{ project.title }}</h2>
          <p>{{ project.text }}</p>
        </div>
      </article>
    </div>
  </div>
</template>
