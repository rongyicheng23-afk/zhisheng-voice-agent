import { getRuntimeWsBaseUrl, getRuntimeHttpBaseUrl } from '@/api'

/**
 * WebSocket配置接口
 * 用于发送给语音识别服务器的配置参数
 */
export interface WebSocketConfig {
  /** 音频数据块大小配置 */
  chunk_size: number[]
  /** 音频文件名 */
  wav_name: string
  /** 是否正在说话 */
  is_speaking: boolean
  /** 数据块间隔时间 */
  chunk_interval: number
  /** 识别模式：online/offline/2pass */
  mode: string
  /** 当前实时交互轮次，用于拒收已取消轮次的迟到结果 */
  turnId?: string
  /** 生命周期事件，例如 turn.interrupt */
  event?: string
}

/**
 * WebSocket消息接口
 * 用于接收语音识别服务器返回的消息
 */
export interface WebSocketMessage {
  /** 识别的文本内容 */
  text?: string
  /** 识别模式 */
  mode?: string
  /** 是否为最终结果 */
  is_final?: boolean
}

// 60ms PCM frames × 10 match the current model's 600ms online chunk.
// Do not halve the interval without also adapting the model/VAD frame contract.
export const REALTIME_CHUNK_SIZE = [5, 10, 5] as const
export const REALTIME_CHUNK_INTERVAL = 10

function createTurnId(): string {
  const cryptoApi = (typeof globalThis !== 'undefined' ? globalThis.crypto : undefined) as
    (Crypto & { randomUUID?: () => string }) | undefined
  if (typeof cryptoApi?.randomUUID === 'function') return cryptoApi.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, marker => {
    const value = Math.floor(Math.random() * 16)
    return (marker === 'x' ? value : (value & 0x3) | 0x8).toString(16)
  })
}

/**
 * WebSocket连接方法类
 * 仿照FunASR的wsconnecter.js实现风格
 */
export function WebSocketConnectMethod(config: {
  msgHandle?: (event: MessageEvent) => void
  stateHandle?: (state: number) => void
  url?: string
}) {
  let speechSocket: WebSocket | null = null
  let generation = 0
  let upstreamReady = false
  let timer: ReturnType<typeof setTimeout> | null = null
  let pending: ((result: number) => void) | null = null
  let activeTurnId: string | null = null
  const msgHandle = config.msgHandle
  const stateHandle = config.stateHandle

  const resolveStoredToken = (): string => {
    return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
  }

  const resolveUrl = () => `${getRuntimeWsBaseUrl()}/ws/funasr`

  const settle = (result: number) => {
    if (timer !== null) clearTimeout(timer)
    timer = null
    const resolve = pending
    pending = null
    resolve?.(result)
  }

  // 定义开始连接函数
  const wsStart = async function(options: { saveAudio?: boolean } = {}): Promise<number> {
    wsStop()
    const attempt = generation
    const token = resolveStoredToken()
    if (!token || !('WebSocket' in window)) return 0
    const controller = new AbortController()
    const requestTimeout = setTimeout(() => controller.abort(), 8000)
    try {
      const target = new URL(config.url || resolveUrl())
      const expected = new URL(resolveUrl())
      if (target.origin !== expected.origin || target.pathname !== expected.pathname || target.search || target.hash) {
        throw new Error('Invalid realtime endpoint')
      }
      const response = await fetch(`${getRuntimeHttpBaseUrl()}/api/realtime/ticket`, {
        method: 'POST', headers: { Authorization: `Bearer ${token}` },
        cache: 'no-store', signal: controller.signal
      })
      if (!response.ok) throw new Error('Ticket unavailable')
      const payload = await response.json()
      if (generation !== attempt) return 0
      const ticket = payload?.data?.ticket
      if (payload.code !== 200 || payload.data.purpose !== 'funasr' || !/^[A-Za-z0-9_-]{43}$/.test(ticket || '')) {
        throw new Error('Invalid ticket')
      }
      target.searchParams.set('ticket', ticket)
      if (options.saveAudio === true) target.searchParams.set('save_audio', 'true')
      return await new Promise<number>(resolve => {
        pending = resolve
        const socket = new WebSocket(target.toString())
        speechSocket = socket
        let ready = false
        const current = () => generation === attempt && speechSocket === socket
        timer = setTimeout(() => {
          if (!current()) return
          wsStop()
          stateHandle?.(2)
        }, 8000)
        socket.onopen = () => { /* Wait for the upstream-ready acknowledgement. */ }
        socket.onmessage = e => {
          if (!current()) return
          if (!ready) {
            try {
              if (JSON.parse(e.data).event !== 'session.ready') return
            } catch (_) { return }
            ready = true
            upstreamReady = true
            settle(1)
            onOpen(e)
            return
          }
          onMessage(e)
        }
        socket.onclose = e => { if (current()) { settle(0); upstreamReady = false; speechSocket = null; onClose(e) } }
        socket.onerror = () => {
          if (!current()) return
          wsStop()
          stateHandle?.(2)
        }
      })
    } catch (_) {
      // Never log WebSocket/error objects: they can contain URL tickets.
      if (generation === attempt) { wsStop(); stateHandle?.(2) }
      return 0
    } finally {
      clearTimeout(requestTimeout)
    }
  }

  // 定义停止连接函数
  const wsStop = function(): void {
    if (activeTurnId && upstreamReady && speechSocket?.readyState === WebSocket.OPEN) {
      try {
        speechSocket.send(JSON.stringify({ event: 'turn.interrupt', turnId: activeTurnId }))
      } catch (_) { /* The close below is the final cancellation boundary. */ }
    }
    generation += 1
    upstreamReady = false
    settle(0)
    if (speechSocket != undefined) {
      speechSocket.close()
      speechSocket = null
    }
    activeTurnId = null
  }

  // 定义发送数据函数
  const wsSend = function(oneData: string | ArrayBuffer): void {
    if (speechSocket == undefined) {
      return
    }
    
    if (upstreamReady && speechSocket.readyState === 1) { // 0:CONNECTING, 1:OPEN, 2:CLOSING, 3:CLOSED
      if (speechSocket.bufferedAmount > 1024 * 1024) {
        wsStop()
        stateHandle?.(2)
        return
      }
      speechSocket.send(oneData)
    }
  }

  // WebSocket连接中的消息与状态响应
  function onOpen(e: Event): void {
    activeTurnId = createTurnId()
    // 发送json
    const request: WebSocketConfig = {
      "chunk_size": [...REALTIME_CHUNK_SIZE],
      "wav_name": "microphone",
      "is_speaking": true,
      "chunk_interval": REALTIME_CHUNK_INTERVAL,
      "mode": "2pass",
      "turnId": activeTurnId
    }
    
    speechSocket?.send(JSON.stringify(request))
    stateHandle?.(0) // 0: 连接成功
  }

  function onClose(e: CloseEvent): void {
    activeTurnId = null
    stateHandle?.(1) // 1: 连接关闭
  }

  function onMessage(e: MessageEvent): void {
    msgHandle?.(e)
  }

  // 检查连接状态
  const isConnected = function(): boolean {
    return upstreamReady && speechSocket?.readyState === WebSocket.OPEN
  }

  // 获取连接状态信息
  const getConnectionStatus = function() {
    return {
      connected: isConnected(),
      readyState: speechSocket?.readyState ?? null,
      url: resolveUrl()
    }
  }

  // 返回公共接口
  return {
    wsStart,
    wsStop,
    wsSend,
    isConnected,
    getConnectionStatus
  }
}

// 为了保持向后兼容，保留WebSocketClient类
export class WebSocketClient {
  private wsConnectMethod: ReturnType<typeof WebSocketConnectMethod>

  constructor(config?: {
    msgHandle?: (event: MessageEvent) => void
    stateHandle?: (state: number) => void
    url?: string
  }) {
    this.wsConnectMethod = WebSocketConnectMethod(config || {})
  }

  async connect(): Promise<void> {
    if (await this.wsConnectMethod.wsStart() !== 1) throw new Error('连接失败')
  }

  sendConfig(config: WebSocketConfig): void {
    this.wsConnectMethod.wsSend(JSON.stringify(config))
  }

  sendAudioData(data: ArrayBuffer): void {
    this.wsConnectMethod.wsSend(data)
  }

  sendStopSignal(config: WebSocketConfig): void {
    const stopRequest = { ...config, is_speaking: false }
    this.wsConnectMethod.wsSend(JSON.stringify(stopRequest))
  }

  close(): void {
    this.wsConnectMethod.wsStop()
  }

  isConnected(): boolean {
    return this.wsConnectMethod.isConnected()
  }

  getConnectionStatus() {
    return this.wsConnectMethod.getConnectionStatus()
  }

  onMessage(callback: (data: WebSocketMessage) => void): void {
    // 这个方法在WebSocketConnectMethod中通过构造函数配置
  }

  onOpen(callback: () => void): void {
    // 这个方法在WebSocketConnectMethod中通过构造函数配置
  }

  onClose(callback: () => void): void {
    // 这个方法在WebSocketConnectMethod中通过构造函数配置
  }

  onError(callback: (error: Event) => void): void {
    // 这个方法在WebSocketConnectMethod中通过构造函数配置
  }
} 
