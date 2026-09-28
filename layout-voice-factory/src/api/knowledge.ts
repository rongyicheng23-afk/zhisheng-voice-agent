import { getRuntimeHttpBaseUrl } from '@/api'

export interface KnowledgeDocument {
  id: string; seriesId: string; title: string; sourceUrl: string; publisher: string
  sourceVersion: string; validFrom: string; validUntil: string; content: string; status: string; revision: number
}
export interface KnowledgeCitation {
  id: string; documentId: string; title: string; sourceUrl: string; publisher: string
  sourceVersion: string; validFrom: string; validUntil: string; paragraph: number; quote: string
}
export interface KnowledgeComparison {
  beforeId: string; afterId: string; coarse: boolean; added: number; removed: number
  fields: Array<{ field: string, before: string, after: string }>
  lines: Array<{ kind: 'ADDED' | 'REMOVED' | 'UNCHANGED', beforeLine: number | null, afterLine: number | null, text: string }>
}
export interface PublicationReview {
  candidate: KnowledgeDocument; replaced: KnowledgeDocument[]; checkedOn: string
  eligible: boolean; message: string; reviewToken: string; comparison: KnowledgeComparison | null
}
export interface KnowledgeImportPreview {
  format: 'txt' | 'pdf' | 'docx'; suggestedTitle: string; content: string; pages: number; warnings: string[]
}
export async function knowledgeImport(file: File): Promise<KnowledgeImportPreview> {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) throw new Error('请先登录')
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 30000)
  try {
    const body = new FormData(); body.append('file', file)
    const response = await fetch(getRuntimeHttpBaseUrl() + '/api/knowledge/import-preview', {
      method: 'POST', cache: 'no-store', signal: controller.signal,
      headers: { Authorization: 'Bearer ' + token }, body
    })
    if (!response.ok) {
      if (response.status === 401) throw new Error('登录已失效，请重新登录')
      if (response.status === 403) throw new Error('没有访问权限')
      if ([413, 415, 422, 429].includes(response.status)) {
        const data = await response.json().catch(() => null)
        if (typeof data?.message === 'string' && data.message.length <= 300) throw new Error(data.message)
      }
      throw new Error('文件提取失败，请检查文件格式和大小，稍后重试')
    }
    return await response.json()
  } catch (error) {
    if (controller.signal.aborted) throw new Error('文件提取超时，请拆分文件后重试；当前正文已保留')
    throw error
  } finally { clearTimeout(timeout) }
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
        404: '资料不存在或无权访问', 409: '资料状态已变化，请刷新列表或重新预览，核对后再操作',
        400: '请检查必填项、有效日期、版本号及个人资料数量上限', 503: '资料库不可用，请检查数据库初始化与连接' }
      throw new Error(messages[response.status] || '请求失败，请稍后重试')
    }
    return await response.json()
  } finally { clearTimeout(timeout) }
}
