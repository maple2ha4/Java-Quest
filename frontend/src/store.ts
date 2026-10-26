import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, ApiError } from './api'
import type { Lesson, Profile, Capabilities } from './types'
export const useQuest = defineStore('quest', () => {
  const lessons = ref<Lesson[]>([]),
    user = ref<Profile | null>(null),
    ready = ref(false),
    judgeEnabled = ref(false),
    judgeMode = ref<Capabilities['judgeMode']>('off')
  const completed = computed(() => user.value?.completed || [])
  const nextLesson = computed(
    () => lessons.value.find((l) => !completed.value.includes(l.id)) || lessons.value[0],
  )
  const percentage = computed(() =>
    lessons.value.length ? Math.round((completed.value.length / lessons.value.length) * 100) : 0,
  )
  async function load() {
    const [course, capabilities] = await Promise.all([
      api<Lesson[]>('/lessons'),
      api<Capabilities>('/capabilities'),
    ])
    lessons.value = course
    judgeEnabled.value = capabilities.judgeEnabled
    judgeMode.value = capabilities.judgeMode || (capabilities.judgeEnabled ? 'remote' : 'off')
    try {
      user.value = await api<Profile>('/me')
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) throw e
    }
    ready.value = true
  }
  async function refresh() {
    user.value = await api<Profile>('/me')
  }
  async function logout() {
    await api('/auth/logout', { method: 'POST' })
    user.value = null
  }
  return {
    lessons,
    user,
    ready,
    judgeEnabled,
    judgeMode,
    completed,
    nextLesson,
    percentage,
    load,
    refresh,
    logout,
  }
})
