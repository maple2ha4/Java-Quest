export interface SourceFile {
  name: string
  content: string
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
  status: string
  passed: boolean
  tests: TestResult[]
  totalTests: number
}
export interface RunResponse {
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
