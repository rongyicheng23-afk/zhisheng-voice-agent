import { getRuntimeWsBaseUrl } from '@/api'

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

// 保守优化版实时参数：
// 1. 保持 60ms 音频帧不变，避免过度增加请求频次
// 2. 将在线触发间隔从 10 帧降到 5 帧，把上屏粒度从约 600ms 压到约 300ms
export const REALTIME_CHUNK_SIZE = [5, 10, 5] as const
export const REALTIME_CHUNK_INTERVAL = 5

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
  const msgHandle = config.msgHandle
  const stateHandle = config.stateHandle

  const resolveStoredToken = (): string => {
    return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
  }

  const resolveUrl = () => {
    if (config.url) {
      return config.url
    }

    const token = resolveStoredToken()
    const query = token ? `?token=${encodeURIComponent(token)}` : ''
    const wsBaseUrl = getRuntimeWsBaseUrl()
    return `${wsBaseUrl}/ws/funasr${query}`
  }

  // 定义开始连接函数
  const wsStart = function(): number {
    const Uri = resolveUrl()
    
    if (Uri.match(/wss:\S*|ws:\S*/)) {
    } else {
      console.error("请检查WebSocket地址正确性")
      return 0
    }

    if ('WebSocket' in window) {
      speechSocket = new WebSocket(Uri)
      speechSocket.onopen = function(e) { onOpen(e) }
      speechSocket.onclose = function(e) {
        onClose(e)
      }
      speechSocket.onmessage = function(e) { onMessage(e) }
      speechSocket.onerror = function(e) { onError(e) }
      return 1
    } else {
      console.error('当前浏览器不支持 WebSocket')
      return 0
    }
  }

  // 定义停止连接函数
  const wsStop = function(): void {
    if (speechSocket != undefined) {
      speechSocket.close()
      speechSocket = null
    }
  }

  // 定义发送数据函数
  const wsSend = function(oneData: string | ArrayBuffer): void {
    if (speechSocket == undefined) {
      return
    }
    
    if (speechSocket.readyState === 1) { // 0:CONNECTING, 1:OPEN, 2:CLOSING, 3:CLOSED
      speechSocket.send(oneData)
    }
  }

  // WebSocket连接中的消息与状态响应
  function onOpen(e: Event): void {
    // 发送json
    const request: WebSocketConfig = {
      "chunk_size": [...REALTIME_CHUNK_SIZE],
      "wav_name": "microphone",
      "is_speaking": true,
      "chunk_interval": REALTIME_CHUNK_INTERVAL,
      "mode": "2pass"
    }
    
    speechSocket?.send(JSON.stringify(request))
    stateHandle?.(0) // 0: 连接成功
  }

  function onClose(e: CloseEvent): void {
    stateHandle?.(1) // 1: 连接关闭
  }

  function onMessage(e: MessageEvent): void {
    msgHandle?.(e)
  }

  function onError(e: Event): void {
    console.error("连接错误:", e)
    stateHandle?.(2) // 2: 连接错误
  }

  // 检查连接状态
  const isConnected = function(): boolean {
    return speechSocket?.readyState === WebSocket.OPEN
  }

  // 获取连接状态信息
  const getConnectionStatus = function() {
    return {
      connected: isConnected(),
      readyState: speechSocket?.readyState || null,
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

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const result = this.wsConnectMethod.wsStart()
      if (result === 1) {
        // 连接成功，但需要等待onOpen回调
        setTimeout(() => {
          if (this.isConnected()) {
            resolve()
          } else {
            reject(new Error('连接超时'))
          }
        }, 1000)
      } else {
        reject(new Error('连接失败'))
      }
    })
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
