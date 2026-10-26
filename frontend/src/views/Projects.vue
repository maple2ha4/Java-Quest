<script setup lang="ts">
import { FolderCode, ArrowUpRight, Lock } from 'lucide-vue-next'
const projects = [
  {
    title: '命令行任务清单',
    level: 'Java 基础 + 集合',
    text: '练习对象建模、列表操作和异常处理。',
    checks: '验收：新增、完成、删除任务与非法输入处理。',
  },
  {
    title: '你的第一个图书 API',
    level: 'Maven + Spring Boot + MySQL',
    text: '连接 Controller、Service 与数据库，构建可测试的接口。',
    checks: '验收：CRUD、参数校验、事务与 API 合约测试。',
  },
  {
    title: '并发任务调度站',
    level: '多线程 + 企业实战',
    text: '把线程池、定时任务和实时消息连接成完整项目。',
    checks: '验收：有界队列、幂等执行、超时与实时状态推送。',
  },
]
</script>
<template>
  <div class="page">
    <span class="eyebrow">BUILD SOMETHING REAL</span>
    <h1>让知识，成为作品。</h1>
    <p class="page-intro">把一个个知识点，连接成真正可用的项目。</p>
    <div class="info-banner">
      <Lock
        :size="18"
      />项目挑战属于后续阶段，当前尚未开放；以下为课程规划，没有模拟提交或虚假通关。
    </div>
    <div class="project-grid">
      <article v-for="(project, index) in projects" :key="project.title" class="project-card">
        <div class="project-cover" :class="['green', 'blue', 'purple'][index]">
          <FolderCode :size="50" stroke-width="1" /><span>PROJECT / 0{{ index + 1 }}</span>
        </div>
        <div class="project-copy">
          <span class="eyebrow">{{ project.level }}</span>
          <h2>{{ project.title }}</h2>
          <p>{{ project.text }}</p>
          <p class="scope-note">{{ project.checks }}</p>
          <span class="pill">规划中</span>
        </div>
      </article>
    </div>
    <RouterLink to="/map" class="subtle-link">先打好基础 <ArrowUpRight :size="16" /></RouterLink>
  </div>
</template>
