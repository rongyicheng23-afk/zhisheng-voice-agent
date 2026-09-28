import { getRuntimeHttpBaseUrl } from '@/api'

export type ServiceStatus = 'checking' | 'online' | 'offline' | 'unknown'
const definitions = [
  ['funasr-http', '录音转写'], ['funasr-ws', '实时识别'], ['tts', '语音合成'],
  ['voiceprint', '声纹服务'], ['mysql', '数据库'], ['minio', '文件存储']
]
export const initialServices = (status: ServiceStatus = 'checking') => definitions.map(([key, label]) => ({ key, label, status }))

export function parseServiceHealth(data: unknown) {
  const snapshot = data as { services?: Array<{ name?: string, status?: string }>, checkedAt?: string } | null
  const entries = snapshot && Array.isArray(snapshot.services) ? snapshot.services : []
  const services = initialServices('unknown').map(service => {
    const matches = entries.filter(entry => entry?.name === service.key)
    if (matches.length === 1) {
      if (matches[0].status === 'UP') service.status = 'online'
      if (matches[0].status === 'DOWN') service.status = 'offline'
    }
    return service
  })
  const date = typeof snapshot?.checkedAt === 'string' ? new Date(snapshot.checkedAt) : null
  return { services, checkedAt: date && Number.isFinite(date.getTime()) ? date.toLocaleTimeString('zh-CN', { hour12: false }) : '' }
}

export async function fetchServiceHealth() {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetch(getRuntimeHttpBaseUrl() + '/api/system/status', { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new Error('health unavailable')
    return parseServiceHealth(await response.json())
  } finally { clearTimeout(timeout) }
}
