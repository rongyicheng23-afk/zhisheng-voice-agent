import { getRealtimeGatewayWsBaseUrl, getRuntimeHttpBaseUrl } from '@/api'
import { PcmTurnPlayer } from './pcm-turn-player'

export type RealtimeGatewayEvent = {
  sessionId?: string
  turnId?: string
  event?: string
  sequence?: number
  timestamp?: string
  text?: string
  mode?: string
  citations?: Record<string, Citation>
  citationIds?: string[]
  audioBase64?: string
  sampleRate?: number
  channels?: number
  format?: string
  codec?: string
  segmentSequence?: number
  chunkSequence?: number
  chunkCount?: number
  synthesisMode?: 'streaming' | 'segmented'
  reason?: string
  code?: string
  message?: string
}

export type Citation = {
  title?: string
  source?: string
  version?: string
  excerpt?: string
  page?: number | null
}

type ConnectionState = 'open' | 'closed' | 'error'

/**
 * Browser client for the Python realtime gateway. The login JWT is used only
 * once to obtain a short-lived ticket; it is never placed in a WebSocket URL.
 */
export function RealtimeGatewayClient(config: {
  onEvent: (event: RealtimeGatewayEvent) => void
  onState?: (state: ConnectionState) => void
}) {
  let socket: WebSocket | null = null
  let generation = 0

  const token = () => (localStorage.getItem('token') || sessionStorage.getItem('token') || '').replace(/^Bearer\s+/i, '')
  const isConnected = () => socket?.readyState === WebSocket.OPEN

  const disconnect = () => {
    generation += 1
    if (socket) socket.close()
    socket = null
  }

  const connect = async (): Promise<void> => {
    // Keep one live socket for a conversation. Reconnecting immediately after
    // turn.interrupt could close the browser socket before the cancellation
    // frame had been delivered to the gateway.
    if (isConnected()) return
    disconnect()
    const current = generation
    const jwt = token()
    if (!jwt) throw new Error('请先登录')

    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 8_000)
    try {
      const ticketResponse = await fetch(`${getRuntimeHttpBaseUrl()}/api/realtime/tickets`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${jwt}` },
        cache: 'no-store',
        signal: controller.signal
      })
      if (ticketResponse.status === 401) {
        throw new Error('登录状态已失效，请退出后重新登录，再连接实时语音')
      }
      if (!ticketResponse.ok) {
        throw new Error(`无法申请实时连接票据（HTTP ${ticketResponse.status}）`)
      }
      const ticketPayload = await ticketResponse.json()
      const ticket = ticketPayload?.ticket
      if (typeof ticket !== 'string' || !ticket) throw new Error('实时连接票据无效')
      if (current !== generation) return

      const target = new URL(`${getRealtimeGatewayWsBaseUrl()}/realtime/ws`)
      target.searchParams.set('ticket', ticket)
      await new Promise<void>((resolve, reject) => {
        const nextSocket = new WebSocket(target.toString())
        socket = nextSocket
        let settled = false
        const settle = (callback: () => void) => {
          if (settled) return
          settled = true
          callback()
        }
        const openTimer = window.setTimeout(() => {
          if (socket === nextSocket) nextSocket.close()
          settle(() => reject(new Error('实时连接超时')))
        }, 8_000)
        nextSocket.onopen = () => {
          window.clearTimeout(openTimer)
          if (current !== generation || socket !== nextSocket) return
          config.onState?.('open')
          settle(resolve)
        }
        nextSocket.onmessage = event => {
          if (current !== generation || socket !== nextSocket || typeof event.data !== 'string') return
          try { config.onEvent(JSON.parse(event.data) as RealtimeGatewayEvent) } catch (_) { /* ignore malformed provider data */ }
        }
        nextSocket.onclose = event => {
          window.clearTimeout(openTimer)
          if (current === generation && socket === nextSocket) {
            socket = null
            config.onState?.('closed')
          }
          const detail = event.reason ? `：${event.reason}` : `（关闭码 ${event.code}）`
          settle(() => reject(new Error(`实时网关拒绝连接${detail}`)))
        }
        nextSocket.onerror = () => {
          window.clearTimeout(openTimer)
          // Browser WebSocket errors intentionally hide details. Wait for
          // onclose so its safe close code/reason can be shown to the user.
        }
      })
    } finally {
      window.clearTimeout(timeout)
    }
  }

  const send = (payload: Record<string, unknown> | ArrayBuffer) => {
    if (!isConnected()) throw new Error('实时连接未建立')
    socket?.send(payload instanceof ArrayBuffer ? payload : JSON.stringify(payload))
  }

  return { connect, disconnect, send, isConnected }
}

// Existing pages use this small API. Keep it while routing the legacy recorder
// through the new turn/event protocol rather than the retired Java /ws/funasr
// proxy protocol.
export interface WebSocketConfig {
  chunk_size: number[]
  wav_name: string
  is_speaking: boolean
  chunk_interval: number
  mode: string
}

export interface WebSocketMessage {
  text?: string
  mode?: string
  is_final?: boolean
}

export const REALTIME_CHUNK_SIZE = [5, 10, 5] as const
export const REALTIME_CHUNK_INTERVAL = 10

export function WebSocketConnectMethod(config: {
  msgHandle?: (event: MessageEvent) => void
  gatewayEventHandle?: (event: RealtimeGatewayEvent) => void
  stateHandle?: (state: number) => void
  errorHandle?: (message: string) => void
  playbackEndedHandle?: (turnId: string) => void
  url?: string
}) {
  let activeTurnId: string | null = null
  const turnsWithAudio = new Set<string>()
  const player = new PcmTurnPlayer({
    onError: (turnId, message) => {
      if (client.isConnected() && activeTurnId === turnId) {
        try { client.send({ event: 'turn.interrupt', turnId }) } catch (_) { /* disconnected */ }
      }
      activeTurnId = null
      turnsWithAudio.delete(turnId)
      config.errorHandle?.(message)
      config.gatewayEventHandle?.({ event: 'turn.failed', turnId, message })
    },
    onTurnPlaybackEnded: turnId => {
      turnsWithAudio.delete(turnId)
      config.playbackEndedHandle?.(turnId)
    },
    onMetric: metric => {
      // Playback happens in the browser, so these timing events are produced
      // here rather than guessed by the gateway. They are also sent back as
      // telemetry while the turn is active for a single ordered event trail.
      config.gatewayEventHandle?.(metric)
      if (client.isConnected()) {
        try { client.send(metric) } catch (_) { /* telemetry never breaks playback */ }
      }
    }
  })
  const createTurnId = () => {
    // randomUUID is a browser method and must keep the `crypto` receiver;
    // extracting it first causes Safari/Chrome to throw "Illegal invocation".
    const browserCrypto = crypto as Crypto & { randomUUID?: () => string }
    return typeof browserCrypto.randomUUID === 'function'
      ? browserCrypto.randomUUID()
      : '00000000-0000-4000-8000-000000000001'
  }
  const client = RealtimeGatewayClient({
    onEvent: event => {
      if (event.turnId && event.turnId !== activeTurnId) return
      config.gatewayEventHandle?.(event)
      if (event.event === 'asr.delta' || event.event === 'asr.partial' || event.event === 'asr.final') {
        config.msgHandle?.({ data: JSON.stringify({
          text: event.text || '', mode: event.event === 'asr.final' ? 'offline' : 'online',
          is_final: event.event === 'asr.final'
        }) } as MessageEvent)
      }
      if (event.event === 'audio.chunk' && event.turnId && event.audioBase64) {
        turnsWithAudio.add(event.turnId)
        player.play(
          event.turnId,
          event.audioBase64,
          event.sampleRate || 16000,
          event.channels || 1,
          event.segmentSequence ?? 0,
          event.chunkSequence ?? 0,
          event.codec || event.format || 'pcm_s16le'
        )
      }
      if (event.event === 'tts.segment_completed' && event.turnId) {
        player.completeSegment(event.turnId, event.segmentSequence ?? 0, event.chunkCount ?? -1)
      }
      if (event.event === 'turn.completed' && event.turnId) {
        const hadAudio = turnsWithAudio.has(event.turnId)
        activeTurnId = null
        if (hadAudio) {
          player.markTurnCompleted(event.turnId)
        } else {
          config.playbackEndedHandle?.(event.turnId)
        }
      }
      if (event.event === 'turn.cancelled' || event.event === 'turn.failed') {
        player.stop()
        if (event.turnId) turnsWithAudio.delete(event.turnId)
        activeTurnId = null
      }
    },
    onState: state => {
      if (state === 'closed') config.stateHandle?.(1)
    }
  })

  const interruptActiveTurn = () => {
    const previousTurnId = activeTurnId
    if (previousTurnId && client.isConnected()) {
      // Send the protocol-level cancellation before the next connection is
      // opened. The gateway cancels ASR/LLM/TTS and rejects late old events.
      try { client.send({ event: 'turn.interrupt', turnId: previousTurnId }) } catch (_) { /* socket is closing */ }
    }
    activeTurnId = null
    turnsWithAudio.clear()
    player.stop()
  }

  const wsStart = async (): Promise<void> => {
    try {
      // Starting a new question is an interruption of the previous one,
      // not merely a browser WebSocket reconnect.
      interruptActiveTurn()
      await client.connect()
      const turnId = createTurnId()
      activeTurnId = turnId
      await player.start(turnId)
      client.send({ event: 'turn.start', turnId, asr: { mode: '2pass' } })
      config.stateHandle?.(0)
    } catch (error) {
      const message = error instanceof Error ? error.message : '实时连接失败'
      config.errorHandle?.(message)
      config.stateHandle?.(2)
      throw error
    }
  }

  const wsStartText = async (prompt: string): Promise<void> => {
    const cleanPrompt = prompt.trim()
    if (!cleanPrompt) throw new Error('请输入要提问的内容')
    try {
      interruptActiveTurn()
      await client.connect()
      const turnId = createTurnId()
      activeTurnId = turnId
      await player.start(turnId)
      client.send({ event: 'turn.start', turnId, prompt: cleanPrompt })
      config.stateHandle?.(0)
    } catch (error) {
      const message = error instanceof Error ? error.message : '实时连接失败'
      config.errorHandle?.(message)
      config.stateHandle?.(2)
      throw error
    }
  }

  const wsTest = async (): Promise<void> => {
    try {
      await client.connect()
      config.stateHandle?.(0)
    } catch (error) {
      const message = error instanceof Error ? error.message : '实时连接失败'
      config.errorHandle?.(message)
      config.stateHandle?.(2)
      throw error
    }
  }

  const wsStop = (): void => {
    interruptActiveTurn()
    client.disconnect()
  }

  const wsSend = (data: string | ArrayBuffer): void => {
    if (!activeTurnId || !client.isConnected()) return
    if (typeof data === 'string') {
      try {
        const payload = JSON.parse(data)
        if (payload && payload.is_speaking === false) client.send({ event: 'speech.end', turnId: activeTurnId })
      } catch (_) { /* only lifecycle JSON is accepted here */ }
      return
    }
    try { client.send(data) } catch (_) { config.stateHandle?.(2) }
  }

  return {
    wsStart, wsStartText, wsTest, wsStop, wsSend,
    // Keep the socket/session alive, but cancel the current ASR/LLM/TTS turn.
    // This is the browser equivalent of ChatGPT's “stop generating” button.
    wsInterrupt: interruptActiveTurn,
    isConnected: client.isConnected,
    getConnectionStatus: () => ({ connected: client.isConnected(), readyState: null, url: `${getRealtimeGatewayWsBaseUrl()}/realtime/ws` })
  }
}
