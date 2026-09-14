import http from '@/api/index'

export type RealtimeHistoryMessage = {
  id: number
  role: 'user' | 'assistant'
  content: string
  inputMode: 'text' | 'voice'
  createTime?: string
}

type ApiResponse<T> = { code: number, msg: string, data: T }

export const getRealtimeHistory = () =>
  http.get<any, ApiResponse<RealtimeHistoryMessage[]>>('/api/realtime/history', { params: { limit: 100 } })

export const saveRealtimeHistoryMessage = (message: Pick<RealtimeHistoryMessage, 'role' | 'content' | 'inputMode'>) =>
  http.post<any, ApiResponse<RealtimeHistoryMessage>>('/api/realtime/history', message)

export const clearRealtimeHistory = () =>
  http.delete<any, ApiResponse<null>>('/api/realtime/history')
