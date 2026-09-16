import http, { getRealtimeGatewayWsBaseUrl } from '@/api'

export type ServiceStatus = { name: string, status: 'UP' | 'DOWN' | 'UNKNOWN', latencyMs: number, message: string }
export type SystemSnapshot = { status: 'UP' | 'DEGRADED', services: ServiceStatus[], checkedAt: string }
export type GatewayMetrics = {
  turnsStarted: number
  turnsCompleted: number
  turnsCancelled: number
  turnsFailed: number
  bufferUnderruns: number
  averageFirstTokenMs: number | null
  averageFirstAudioMs: number | null
  sampleWindow: number
}

type BackendResponse<T> = { code: number, msg: string, data: T }

export const getSystemStatus = () =>
  http.get<any, BackendResponse<SystemSnapshot>>('/api/system/observation')

export const getGatewayMetrics = async (): Promise<GatewayMetrics> => {
  const base = getRealtimeGatewayWsBaseUrl().replace(/^ws:/, 'http:').replace(/^wss:/, 'https:')
  const response = await fetch(`${base}/realtime/metrics`, { method: 'GET', credentials: 'omit' })
  if (!response.ok) throw new Error('实时网关暂不可用')
  const data = await response.json()
  return data.metrics as GatewayMetrics
}
