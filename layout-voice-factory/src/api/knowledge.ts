import { getRuntimeHttpBaseUrl } from '@/api'

export interface KnowledgeDocument {
  id: string; seriesId: string; title: string; sourceUrl: string; publisher: string
  sourceVersion: string; validFrom: string; validUntil: string; content: string; status: string; revision: number
}
export interface KnowledgeCitation {
  id: string; documentId: string; title: string; sourceUrl: string; publisher: string
  sourceVersion: string; validFrom: string; validUntil: string; paragraph: number; quote: string
}
export async function knowledgeRequest<T>(path: string, body?: unknown): Promise<T> {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) throw new Error('请先登录')
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 15000)
  try {
    const response = await fetch(getRuntimeHttpBaseUrl() + '/api/knowledge' + path, {
      method: body === undefined ? 'GET' : 'POST', cache: 'no-store', signal: controller.signal,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
    if (!response.ok) {
      const messages: Record<number, string> = { 401: '登录已失效，请重新登录', 403: '没有访问权限',
        404: '资料不存在或无权访问', 409: '资料已更新，请刷新后核对再操作',
        400: '请检查必填项、有效日期、版本号及个人资料数量上限', 503: '资料库不可用，请检查数据库初始化与连接' }
      throw new Error(messages[response.status] || '请求失败，请稍后重试')
    }
    return await response.json()
  } finally { clearTimeout(timeout) }
}
