import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, ApiError } from './api'
import type { Lesson, Profile, Capabilities, Stage } from './types'
export const useQuest = defineStore('quest', () => {
  const curriculum = ref<Stage[]>([])
  const lessons = ref<Lesson[]>([]),
    user = ref<Profile | null>(null),
    ready = ref(false),
    judgeEnabled = ref(false),
    judgeMode = ref<Capabilities['judgeMode']>('off')
  const completed = computed(() => user.value?.completed || [])
  function isUnlocked(lesson: Lesson) {
    return lesson.prerequisites.every((id) => completed.value.includes(id))
  }
  function lessonById(id: string) {
    return lessons.value.find((l) => l.id === id)
  }
  function context(id: string) {
    const lesson = lessonById(id)
    for (const stage of curriculum.value)
      for (const chapter of stage.chapters)
        for (const topic of chapter.topics)
          if (topic.id === lesson?.topicId) return { stage, chapter, topic }
    return undefined
  }
  const orderedLessons = computed(() =>
    curriculum.value.flatMap((s) =>
      s.chapters.flatMap((c) =>
        c.topics.flatMap((t) =>
          t.exercises.flatMap((e) =>
            e.lessonId && lessonById(e.lessonId) ? [lessonById(e.lessonId)!] : [],
          ),
        ),
      ),
    ),
  )
  const nextLesson = computed(
    () =>
      orderedLessons.value.find((l) => !completed.value.includes(l.id) && isUnlocked(l)) ||
      orderedLessons.value[0],
  )
  function topicLessons(id: string) {
    const topic = context(id)?.topic
    return (
      topic?.exercises.flatMap((e) =>
        e.lessonId && lessonById(e.lessonId) ? [lessonById(e.lessonId)!] : [],
      ) || []
    )
  }
  function nextForLesson(id: string) {
    const same = topicLessons(id),
      position = same.findIndex((l) => l.id === id)
    return (
      same.slice(position + 1).find(isUnlocked) ||
      orderedLessons.value.find(
        (l) => l.id !== id && !completed.value.includes(l.id) && isUnlocked(l),
      )
    )
  }
  const percentage = computed(() =>
    lessons.value.length ? Math.round((completed.value.length / lessons.value.length) * 100) : 0,
  )
  async function load() {
    const [course, capabilities, outline] = await Promise.all([
      api<Lesson[]>('/lessons'),
      api<Capabilities>('/capabilities'),
      api<Stage[]>('/curriculum'),
    ])
    lessons.value = course
    curriculum.value = outline
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
    curriculum,
    orderedLessons,
    isUnlocked,
    lessonById,
    context,
    topicLessons,
    nextForLesson,
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
