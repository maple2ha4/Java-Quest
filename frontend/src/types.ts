export interface SourceFile {
  name: string
  content: string
}
export interface ExercisePlan {
  id: string
  title: string
  kind: string
  lessonId: string | null
}
export interface Topic {
  id: string
  title: string
  goal: string
  practiceRationale: string
  exercises: ExercisePlan[]
}
export interface Chapter {
  id: string
  title: string
  topics: Topic[]
}
export interface Stage {
  id: string
  title: string
  goal: string
  sampleLessonIds: string[]
  chapters: Chapter[]
}
export interface Lesson {
  id: string
  order: number
  title: string
  shortTitle: string
  summary: string
  concept: string
  xp: number
  minutes: number
  paragraphs: string[]
  task: string
  hints: string[]
  topicId: string
  kind: string
  difficulty: number
  prerequisites: string[]
  entryClass: string
  buildKind: string
  editableFiles: string[]
  starter: SourceFile[]
}
export interface Profile {
  id: number
  email: string
  name: string
  completed: string[]
  xp: number
  submissions: number
}
export interface TestResult {
  status: string
  passed: boolean
  stdout: string
  stderr: string
  expected: string
  input: string
  durationMs: number
}
export interface JudgeResult {
  mode?: 'process' | 'docker'
  status: string
  passed: boolean
  tests: TestResult[]
  totalTests: number
}
export interface RunResponse {
  preview?: boolean
  result: JudgeResult
  newlyCompleted: boolean
  awardedXp: number
  profile: Profile
}
export interface Submission {
  id: number
  lessonId: string
  status: string
  createdAt: string
  result: JudgeResult
}
export interface Note {
  lessonId: string
  body: string
  updatedAt: string
}

export interface Capabilities {
  judgeMode: 'off' | 'process' | 'docker' | 'remote'
  judgeEnabled: boolean
}
