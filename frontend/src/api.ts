export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message)
  }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch('/api' + path, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'X-Quest-Client': '1', ...options.headers },
  })
  const body = await response.json().catch(() => ({ message: '服务响应格式不正确' }))
  if (!response.ok) throw new ApiError(body.message || '请求失败，请稍后重试', response.status)
  return body as T
}
export function message(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}
