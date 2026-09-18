import { getRuntimeHttpBaseUrl } from '@/api'

export interface ReplyUpdate {
  citations?: Array<{ id: string, title: string, sourceVersion: string, publisher: string, sourceUrl: string, validFrom: string, validUntil: string, paragraph: number, quote: string }>
  status?: string
  text?: string
  busy?: boolean
  firstTokenMs?: number
  firstAudioMs?: number
}

/** One socket per reply. Generation gates all callbacks, including late ticket responses. */
export class VoiceReply {
  private generation = 0
  private socket: WebSocket | null = null
  private context: AudioContext | null = null
  private player: AudioWorkletNode | null = null
  private abort: AbortController | null = null
  private timer: ReturnType<typeof setTimeout> | null = null
  private turnId = ''
  constructor(private update: (value: ReplyUpdate) => void) {}

  stop(notify = true) {
    ++this.generation
    this.abort?.abort()
    this.abort = null
    if (this.timer) clearTimeout(this.timer)
    this.timer = null
    if (this.socket?.readyState === WebSocket.OPEN && this.turnId) {
      try { this.socket.send(JSON.stringify({ event: 'turn.interrupt', turnId: this.turnId })) } catch (_) { /* close below */ }
    }
    this.socket?.close()
    this.socket = null
    this.player?.port.postMessage({ event: 'turn.interrupt', turnId: this.turnId })
    this.player?.disconnect()
    this.player = null
    if (this.context) void this.context.close().catch(() => undefined)
    this.context = null
    this.turnId = ''
    if (notify) this.update({ busy: false, status: '已停止回答' })
  }

  async start(prompt: string, answerMode: 'general' | 'knowledge' = 'general') {
    this.stop(false)
    const attempt = this.generation
    const current = () => attempt === this.generation
    const began = performance.now()
    const text = prompt.trim()
    if (!text || text.length > (answerMode === 'knowledge' ? 1000 : 4000)) {
      this.update({ busy: false, status: answerMode === 'knowledge' ? '资料模式请输入 1–1000 字的提问' : '请输入 1–4000 字的提问' })
      return
    }
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (!token) {
      this.update({ busy: false, status: '请先登录' })
      return
    }
    this.update({ busy: true, text: '', citations: [], status: '正在连接语音回答', firstTokenMs: undefined, firstAudioMs: undefined })
    const fail = (status: string) => {
      if (!current()) return
      this.stop(false)
      this.update({ busy: false, status })
    }
    try {
      this.timer = setTimeout(() => fail('连接或音频初始化超时，请点击发送重试'), 15000)
      // Run resume from the user gesture, before fetching the ticket.
      const context = new AudioContext({ sampleRate: 24000 })
      this.context = context
      await context.resume()
      if (!current()) return
      await context.audioWorklet.addModule('/audio/turn-pcm-player.js')
      if (!current()) return
      const player = new AudioWorkletNode(context, 'turn-pcm-player', { outputChannelCount: [1] })
      this.player = player
      player.connect(context.destination)
      const controller = new AbortController()
      this.abort = controller
      const response = await fetch(getRuntimeHttpBaseUrl() + '/api/realtime/tickets', {
        method: 'POST', headers: { Authorization: 'Bearer ' + token },
        cache: 'no-store', signal: controller.signal
      })
      if (!response.ok) throw new Error('ticket')
      const ticket = (await response.json()).ticket
      if (!current()) return
      if (typeof ticket !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(ticket)) throw new Error('ticket')
      const local = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8081'
      const defaultBase = local ? 'ws://' + location.hostname + ':18081'
        : (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host
      const base = (process.env.VUE_APP_REALTIME_GATEWAY_URL || defaultBase).replace(/\/$/, '')
      const url = new URL(base + '/realtime/voice')
      if (!['ws:', 'wss:'].includes(url.protocol) || (location.protocol === 'https:' && url.protocol !== 'wss:')) {
        throw new Error('endpoint')
      }
      url.searchParams.set('ticket', ticket)
      const socket = new WebSocket(url.toString())
      this.socket = socket
      let sessionId = '', sequence = 0, answer = '', pendingAck = 0, firstAudio = true
      const send = (event: object) => {
        if (current() && socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify(event))
      }
      player.port.onmessage = ({ data }) => {
        if (!current() || !this.turnId || data.turnId !== this.turnId) return
        if (data.event === 'playback.buffer' && pendingAck && data.bufferedFrames <= 48000) {
          send({ event: 'audio.ack', turnId: this.turnId, sequence: pendingAck })
          pendingAck = 0
        } else if (data.event === 'playback.started' && firstAudio) {
          firstAudio = false
          this.update({ firstAudioMs: Math.round(performance.now() - began), status: '正在播放（分段合成）' })
        } else if (data.event === 'playback.completed') {
          send({ event: 'playback.completed', turnId: this.turnId })
        } else if (data.event === 'playback.failed') fail('音频播放失败，请重新提问')
      }
      socket.onmessage = ({ data }) => {
        if (!current()) return
        try {
          const event = JSON.parse(data)
          if (event.event === 'session.unavailable') {
            fail('语音回答未配置：请配置服务器 DeepSeek 密钥和 TTS 参考音频')
            return
          }
          if (event.event === 'session.ready' && !sessionId) {
            if (event.sampleRate !== 24000 || typeof event.sessionId !== 'string') throw new Error('protocol')
            sessionId = event.sessionId
            if (this.timer) clearTimeout(this.timer)
            this.timer = setTimeout(() => fail('本轮回答超时，请重试'), 10 * 60 * 1000)
            send({ event: 'turn.start', prompt: text, answerMode })
            this.update({ status: '正在生成回答' })
            return
          }
          if (!sessionId || event.sessionId !== sessionId) return
          if (!Number.isSafeInteger(event.sequence) || event.sequence !== sequence + 1) throw new Error('sequence')
          sequence = event.sequence
          if (event.event === 'turn.started' && !this.turnId) {
            if (typeof event.turnId !== 'string' || !event.turnId) throw new Error('turn')
            this.turnId = event.turnId
            player.port.postMessage({ event: 'turn.start', turnId: this.turnId })
          }
          if (event.turnId !== this.turnId) return
          if (event.event === 'turn.sources') {
            if (!Array.isArray(event.citations) || event.citations.length > 3) throw new Error('sources')
            this.update({ citations: event.citations, status: '正在朗读资料原文（不是模型结论）' })
          }
          else if (event.event === 'llm.first_token') this.update({ firstTokenMs: Math.round(performance.now() - began) })
          else if (event.event === 'llm.delta') {
            if (typeof event.text !== 'string' || answer.length + event.text.length > 32000) throw new Error('text')
            answer += event.text
            this.update({ text: answer })
          } else if (event.event === 'audio.chunk') {
            if (pendingAck || typeof event.pcm !== 'string' || event.pcm.length > 6400) throw new Error('audio')
            const pcm = Uint8Array.from(atob(event.pcm), c => c.charCodeAt(0)).buffer
            pendingAck = event.sequence
            player.port.postMessage({ ...event, pcm }, [pcm])
          } else if (event.event === 'audio.completed') player.port.postMessage(event)
          else if (event.event === 'turn.completed') {
            this.stop(false)
            this.update({ busy: false, status: '回答播放完成' })
          } else if (event.event === 'turn.failed') fail(answerMode === 'knowledge'
            ? '资料检索或朗读失败，本轮不会改用无依据回答' : '回答处理失败，请检查模型服务或稍后重试')
          else if (event.event === 'turn.cancelled') fail('回答已取消')
        } catch (_) { fail('回答连接或音频格式异常，请重试') }
      }
      socket.onerror = () => fail('无法连接语音网关，请检查配置')
      socket.onclose = () => fail('回答连接已断开')
    } catch (_) { fail('无法启动语音回答，请检查登录、音频权限和服务状态') }
  }
}
