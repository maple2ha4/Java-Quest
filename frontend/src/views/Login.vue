<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Code2 } from 'lucide-vue-next'
import { api, message } from '../api'
import { useQuest } from '../store'
import type { Profile } from '../types'
const quest = useQuest(),
  route = useRoute(),
  router = useRouter()
const register = ref(false),
  name = ref(''),
  email = ref(''),
  password = ref(''),
  error = ref(''),
  busy = ref(false)
function toggleMode() {
  register.value = !register.value
  error.value = ''
}
async function submit() {
  busy.value = true
  error.value = ''
  try {
    const body = register.value
      ? { email: email.value, password: password.value, name: name.value }
      : { email: email.value, password: password.value }
    quest.user = await api<Profile>('/auth/' + (register.value ? 'register' : 'login'), {
      method: 'POST',
      body: JSON.stringify(body),
    })
    const redirect = String(route.query.redirect || '/')
    router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <span class="brand-mark"><Code2 :size="27" /></span
      ><span class="eyebrow">YOUR JOURNEY STARTS HERE</span>
      <h1>{{ register ? '你好，未来的开发者。' : '欢迎回来。' }}</h1>
      <p>让每一份进步，都有迹可循。</p>
      <label v-if="register"
        >怎么称呼你<input
          v-model="name"
          required
          minlength="2"
          maxlength="40"
          autocomplete="nickname"
          placeholder="你的名字" /></label
      ><label
        >邮箱<input
          v-model="email"
          type="email"
          required
          maxlength="254"
          autocomplete="email"
          placeholder="you@example.com" /></label
      ><label
        >密码<input
          v-model="password"
          type="password"
          required
          :minlength="register ? 8 : 1"
          maxlength="72"
          :autocomplete="register ? 'new-password' : 'current-password'"
          placeholder="至少 8 位，建议使用独立密码"
      /></label>
      <p v-if="error" class="error-box" role="alert">{{ error }}</p>
      <button class="primary" :disabled="busy" type="submit">
        {{ busy ? '正在连接…' : register ? '创建账户，开始探索' : '登录学习空间' }}
        <ArrowRight :size="16" />
      </button>
      <p class="login-switch">
        {{ register ? '已经有账户了？' : '第一次来到这里？'
        }}<button type="button" class="text-button" @click="toggleMode">
          {{ register ? '前往登录' : '创建账户' }}
        </button>
      </p>
      <small>本地学习版本 · 进度与笔记保存到当前学习数据库</small>
    </form>
  </div>
</template>
