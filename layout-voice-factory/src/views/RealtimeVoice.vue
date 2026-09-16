<template>
  <flow-background-panel>
    <div class="realtime-voice-page container-md" style="margin-top: 10px;">
      <div class="realtime-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div class="hero-copy">
            <p class="hero-badge">实时识别</p>
            <h2>实时语音</h2>
            <p class="hero-text">适合实时发言、课堂记录和现场演示场景，系统会边录音边返回稳定的转写文本。</p>
          </div>
          <div class="hero-status">
            <span class="hero-status-label">连接状态</span>
            <strong>{{ isConnected ? '已连接' : '未连接' }}</strong>
            <small>{{ conversationActive ? (isRecording ? '正在聆听你说话。' : '正在等待或播报回复。') : '准备就绪后点击开始对话。' }}</small>
          </div>
        </div>

        <quick-start-panel
          storage-key="realtime-home"
          title="第一次用实时语音，先完成这三步"
          subtitle="这条链路适合现场演示和边说边记，开始录音后系统会持续返回转写文本。"
          :steps="quickStartSteps"
          :tips="quickStartTips"
        />

        <div class="toolbar">
          <div class="hint-list">
            <span class="hint-chip">Python 实时网关</span>
            <span class="hint-chip">ASR → DeepSeek → 讯飞播报</span>
            <span class="hint-chip">资料回答附带来源依据</span>
          </div>
        </div>

        <div class="voice-recording-area">
          <div class="recording-content" :class="{ 'recording-active': conversationActive }">
            <div class="recording-icon-wrapper">
              <div class="microphone-circle" :class="{ 'recording': isRecording }">
                <span class="microphone-ring microphone-ring--outer"></span>
                <span class="microphone-ring microphone-ring--inner"></span>
                <span class="microphone-live-dot" :class="{ 'microphone-live-dot--active': isRecording }"></span>
                <el-icon class="microphone-icon"><Microphone /></el-icon>
              </div>
            </div>
            <div class="recording-text">
              <p class="main-text">{{ recordingStatusText }}</p>
              <p class="support-text">说完停顿约 1.4 秒会自动提交；也可再次点击手动停止</p>
              <p class="connection-status" :class="{ 'connected': isConnected, 'disconnected': !isConnected }">
                WebSocket状态: {{ isConnected ? '已连接' : '未连接' }}
              </p>
            </div>

            <section class="conversation-stream">
              <header class="conversation-stream__header">
                <div>
                  <span>对话记录</span>
                  <small>语音转写与智能回复会实时显示</small>
                </div>
                <el-button text size="small" :disabled="!chatMessages.length" @click="clearText">清空</el-button>
              </header>
              <div ref="chatScrollRef" class="conversation-stream__body">
                <p v-if="!chatMessages.length" class="conversation-stream__empty">
                  开始对话或直接输入文字，消息会显示在这里。
                </p>
                <article
                  v-for="message in chatMessages"
                  :key="message.id"
                  class="chat-message"
                  :class="'chat-message--' + message.role"
                >
                  <span class="chat-message__role">{{ message.role === 'user' ? '你' : 'AI 助手' }}</span>
                  <p>{{ message.text }}</p>
                </article>
                <aside v-if="citationEntries.length" class="chat-citations">
                  <strong>资料依据</strong>
                  <span v-for="item in citationEntries" :key="item.id" :class="{ 'chat-citations__active': activeCitationIds.includes(item.id) }">
                    {{ item.id }} · {{ item.title || '未命名资料' }}<template v-if="item.version"> · {{ item.version }}</template>
                    <template v-if="item.validUntil"> · 有效至 {{ item.validUntil }}</template>
                    <template v-if="item.page"> · 第 {{ item.page }} 页</template>
                  </span>
                </aside>
              </div>
            </section>

            <div class="waveform-shell" :class="{ 'waveform-shell--active': isRecording }">
              <span
                v-for="(bar, index) in waveformBars"
                :key="index"
                class="wave-bar"
                :style="{
                  transform: `scaleY(${bar})`,
                  opacity: `${0.32 + bar * 0.68}`
                }"
              ></span>
            </div>

            <div class="recording-button-wrapper">
              <el-button
                type="primary"
                size="large"
                :class="{ 'recording': conversationActive }"
                @click="toggleRecording"
              >
                <el-icon class="el-icon--left">
                  <Microphone v-if="!conversationActive" />
                  <VideoPause v-else />
                </el-icon>
                {{ conversationActive ? '结束对话' : (chatMessages.length ? '继续对话' : '开始对话') }}
              </el-button>
              <el-button
                type="info"
                size="large"
                class="test-btn"
                @click="testConnection"
              >
                测试连接
              </el-button>
              <el-button
                v-if="conversationActive && !isRecording"
                v-show="isAnswering"
                type="warning"
                size="large"
                class="interrupt-btn"
                @click="stopAssistantResponse"
              >
                <span class="stop-answer-icon" aria-hidden="true"></span>
                停止回答
              </el-button>
              <el-button
                v-if="conversationActive && !isRecording && !isAnswering"
                type="warning"
                size="large"
                class="interrupt-btn"
                @click="interruptAndListen"
              >
                {{ readyForNextTurn ? '继续对话' : '开始聆听' }}
              </el-button>
            </div>

            <div class="inline-text-entry">
              <div class="inline-text-entry__header">
                <span>也可以直接输入问题</span>
                <small>Enter 发送 · Shift + Enter 换行</small>
              </div>
              <div class="inline-text-entry__composer">
                <el-input
                  v-model="textPrompt"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 3 }"
                  maxlength="4000"
                  resize="none"
                  placeholder="输入文字问题，不需要开始录音…"
                  @keydown.enter.exact.prevent="sendTextPrompt"
                />
                <el-button
                  v-if="isAnswering"
                  type="primary"
                  class="composer-stop-button"
                  aria-label="停止回答"
                  title="停止回答"
                  @click="stopAssistantResponse"
                >
                  <span class="stop-answer-icon" aria-hidden="true"></span>
                  停止
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  :loading="isTextSending"
                  :disabled="!textPrompt.trim()"
                  @click="sendTextPrompt"
                >
                  发送
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Microphone, VideoPause, DocumentCopy, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  REALTIME_CHUNK_INTERVAL,
  REALTIME_CHUNK_SIZE,
  WebSocketConnectMethod
} from '@/libs/websocket-client'
import type { Citation, RealtimeGatewayEvent } from '@/libs/websocket-client'
import { AudioRecorder } from '@/libs/audio-recorder'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import QuickStartPanel from '@/components/QuickStartPanel.vue'
import {
  clearRealtimeHistory,
  getRealtimeHistory,
  saveRealtimeHistoryMessage
} from '@/api/realtime-history'
import store from '@/store'

/**
 * 实时语音识别组件
 * 提供实时语音录制和文字转换功能
 */
export default defineComponent({
  name: 'RealtimeVoice',
  components: {
    FlowBackgroundPanel,
    QuickStartPanel,
    Microphone,
    VideoPause,
    DocumentCopy,
    Delete
  },
  setup() {
    const router = useRouter()
    const isRecording = ref(false)
    const conversationActive = ref(false)
    const readyForNextTurn = ref(false)
    const finalTranscriptionText = ref('')
    const draftTranscriptionText = ref('')
    const assistantText = ref('')
    const citations = ref<Record<string, Citation>>({})
    const activeCitationIds = ref<string[]>([])
    const segmentCitations = new Map<string, string[]>()
    const textPrompt = ref('')
    const isTextSending = ref(false)
    const isAnswering = ref(false)
    const chatMessages = ref<Array<{ id: string, role: 'user' | 'assistant', text: string }>>([])
    const chatScrollRef = ref<HTMLElement | null>(null)
    const assistantMessageIds = new Map<string, string>()
    const persistedUserTurnIds = new Set<string>()
    const persistedAssistantTurnIds = new Set<string>()
    let chatMessageSequence = 0
    const recordingStatusText = ref('点击开始录音，系统将实时转换您的语音为文字')
    const isConnected = ref(false)
    let realtimeFailureReason = ''
    const createIdleWaveform = () => Array.from({ length: 28 }, (_, index) => {
      const centerOffset = Math.abs(index - 13.5)
      return Math.max(0.14, 0.24 - centerOffset * 0.007)
    })
    const waveformBars = ref<number[]>(createIdleWaveform())
    let waveformDecayTimer: number | null = null
    let asrFinalWaitTimer: number | null = null
    let nextListeningTimer: number | null = null
    let startingListeningTurn = false
    let speechDetected = false
    let silenceStartedAt: number | null = null
    let autoStopping = false
    const speechThreshold = 0.018
    const autoStopSilenceMs = 1400

    const resetSpeechDetector = () => {
      speechDetected = false
      silenceStartedAt = null
      autoStopping = false
    }

    const clearAsrFinalWait = () => {
      if (asrFinalWaitTimer !== null) {
        window.clearTimeout(asrFinalWaitTimer)
        asrFinalWaitTimer = null
      }
    }

    const clearNextListening = () => {
      if (nextListeningTimer !== null) {
        window.clearTimeout(nextListeningTimer)
        nextListeningTimer = null
      }
    }

    const quickStartSteps = [
      {
        title: '先确认已登录',
        description: '实时语音会按当前账号建立 WebSocket，会话开始前先确认登录状态和网络状态都正常。'
      },
      {
        title: '点击开始录音并正常说话',
        description: '系统会持续采集麦克风音频并实时推送到后端进行边录边识别，适合现场演示和课堂记录。'
      },
      {
        title: '结束后复制或清空文本',
        description: '停止录音后可直接复制识别结果，也可以清空文本继续下一轮演示。'
      }
    ]

    const quickStartTips = [
      '首次使用可先点测试连接',
      '安静环境下实时效果更稳',
      '网络稳定时延迟会更低'
    ]

    const transcriptionText = computed(() => {
      const finalText = finalTranscriptionText.value.trim()
      const draftText = draftTranscriptionText.value.trim()
      if (finalText && draftText) {
        return `${finalText}\n${draftText}`
      }
      return finalText || draftText
    })
    const citationEntries = computed(() => Object.entries(citations.value).map(([id, value]) => ({ id, ...value })))

    const normalizeText = (value: string): string => {
      return value.replace(/^[,，\s]+/, '').replace(/\s+/g, ' ').trim()
    }

    const scrollChatToBottom = () => {
      void nextTick(() => {
        const container = chatScrollRef.value
        if (container) container.scrollTop = container.scrollHeight
      })
    }

    const addChatMessage = (role: 'user' | 'assistant', text: string): string | null => {
      const cleanText = text.trim()
      if (!cleanText) return null
      const id = 'message-' + (++chatMessageSequence)
      chatMessages.value.push({ id, role, text: cleanText })
      scrollChatToBottom()
      return id
    }

    const saveHistoryMessage = async (role: 'user' | 'assistant', text: string, inputMode: 'text' | 'voice') => {
      const content = text.trim()
      if (!content) return
      try {
        await saveRealtimeHistoryMessage({ role, content, inputMode })
      } catch (error) {
        // History saving must never interrupt the live ASR/LLM/TTS path.
        console.warn('保存实时对话记录失败', error)
      }
    }

    const saveVoiceUserTurn = (turnId: string | undefined, text: string) => {
      if (!turnId || persistedUserTurnIds.has(turnId)) return
      persistedUserTurnIds.add(turnId)
      void saveHistoryMessage('user', text, 'voice')
    }

    const saveAssistantTurn = (turnId: string | null | undefined) => {
      if (!turnId || persistedAssistantTurnIds.has(turnId)) return
      const messageId = assistantMessageIds.get(turnId)
      const message = messageId ? chatMessages.value.find(item => item.id === messageId) : undefined
      if (!message?.text.trim()) return
      persistedAssistantTurnIds.add(turnId)
      void saveHistoryMessage('assistant', message.text, 'text')
    }

    const loadChatHistory = async () => {
      try {
        const response = await getRealtimeHistory()
        const history = response.data || []
        if (chatMessages.value.length) return
        chatMessages.value = history
          .filter(item => (item.role === 'user' || item.role === 'assistant') && item.content?.trim())
          .map(item => ({ id: `history-${item.id}`, role: item.role, text: item.content.trim() }))
        scrollChatToBottom()
      } catch (error) {
        // An expired login is already handled by the normal API interceptor.
        console.warn('加载实时对话记录失败', error)
      }
    }

    const appendAssistantDelta = (turnId: string | undefined, delta: string) => {
      if (!delta) return
      const key = turnId || 'text-turn'
      const messageId = assistantMessageIds.get(key)
      const message = messageId
        ? chatMessages.value.find(item => item.id === messageId)
        : undefined
      if (message) {
        message.text += delta
      } else {
        const id = addChatMessage('assistant', delta)
        if (id) assistantMessageIds.set(key, id)
      }
      scrollChatToBottom()
    }

    const isMeaningfulText = (value: string): boolean => {
      if (!value) {
        return false
      }
      if (/^[，。！？,\.!?]+$/.test(value)) {
        return false
      }
      return value.length >= 2
    }

    const findOverlapLength = (left: string, right: string): number => {
      const max = Math.min(left.length, right.length)
      for (let length = max; length > 0; length--) {
        if (left.slice(-length) === right.slice(0, length)) {
          return length
        }
      }
      return 0
    }

    const mergeRealtimeDraft = (existing: string, incoming: string): string => {
      if (!existing) {
        return incoming
      }
      if (existing === incoming || existing.endsWith(incoming)) {
        return existing
      }
      if (incoming.includes(existing)) {
        return incoming
      }
      if (existing.includes(incoming)) {
        return existing
      }
      const overlap = findOverlapLength(existing, incoming)
      return overlap > 0 ? `${existing}${incoming.slice(overlap)}` : `${existing}${incoming}`
    }

    const stopWaveformDecay = () => {
      if (waveformDecayTimer !== null) {
        window.clearInterval(waveformDecayTimer)
        waveformDecayTimer = null
      }
    }

    const resetWaveform = () => {
      waveformBars.value = createIdleWaveform()
    }

    const startWaveformDecay = () => {
      stopWaveformDecay()
      waveformDecayTimer = window.setInterval(() => {
        waveformBars.value = waveformBars.value.map((value, index) => {
          const idle = createIdleWaveform()[index]
          const next = value * 0.8
          return next > idle ? next : idle
        })
      }, 90)
    }

    const updateWaveform = (audioData: ArrayBuffer): number => {
      const pcm = new Int16Array(audioData)
      if (!pcm.length) {
        return 0
      }

      const barCount = 28
      const blockSize = Math.max(1, Math.floor(pcm.length / barCount))
      const nextBars: number[] = []
      let squareSum = 0

      for (let index = 0; index < barCount; index++) {
        const start = index * blockSize
        const end = index === barCount - 1 ? pcm.length : Math.min(pcm.length, start + blockSize)
        let peak = 0
        for (let i = start; i < end; i++) {
          const amplitude = Math.abs(pcm[i]) / 32768
          if (amplitude > peak) {
            peak = amplitude
          }
        }
        nextBars.push(Math.max(0.12, Math.min(1, 0.14 + peak * 1.9)))
      }
      for (let index = 0; index < pcm.length; index++) {
        const amplitude = pcm[index] / 32768
        squareSum += amplitude * amplitude
      }

      waveformBars.value = nextBars
      return Math.sqrt(squareSum / pcm.length)
    }

    const mergeCommittedText = (currentText: string, cleanText: string): string => {
      const trimmedCurrent = currentText.trim()
      if (!trimmedCurrent) {
        return cleanText
      }

      const lines = trimmedCurrent.split('\n').filter(line => line.trim() !== '')
      let shouldAdd = true
      let indicesToRemove: number[] = []
      let replaceIndex = -1

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        if (line === cleanText) {
          shouldAdd = false
          break
        }

        if (cleanText.includes(line) && cleanText.length > line.length + 2) {
          if (replaceIndex === -1) {
            replaceIndex = i
          }
          indicesToRemove.push(i)
          continue
        }

        if (isChineseTextFragment(line, cleanText)) {
          if (replaceIndex === -1) {
            replaceIndex = i
          }
          indicesToRemove.push(i)
          continue
        }

        if (line.includes(cleanText) && line.length > cleanText.length + 2) {
          shouldAdd = false
          break
        }

        const similarity = calculateSimilarity(line, cleanText)
        if (similarity > 0.7) {
          if (cleanText.length > line.length) {
            if (replaceIndex === -1) {
              replaceIndex = i
            }
            indicesToRemove.push(i)
          } else {
            shouldAdd = false
            break
          }
        }
      }

      if (replaceIndex >= 0) {
        const newLines = lines.filter((_, index) => !indicesToRemove.includes(index))
        const safeIndex = Math.min(replaceIndex, newLines.length)
        newLines.splice(safeIndex, 0, cleanText)
        return newLines.join('\n')
      }

      if (!shouldAdd) {
        return trimmedCurrent
      }

      return `${trimmedCurrent}\n${cleanText}`
    }

    const audioRecorder = new AudioRecorder()

    const wsConnectMethod = WebSocketConnectMethod({
      errorHandle: (message: string) => {
        realtimeFailureReason = message
      },
      gatewayEventHandle: (data: RealtimeGatewayEvent) => {
        if (data.event === 'retrieval.completed') {
          citations.value = data.citations || {}
        } else if (data.event === 'llm.started' || data.event === 'llm.first_token') {
          isAnswering.value = true
        } else if (data.event === 'segment.ready') {
          const key = `${data.turnId || ''}:${data.segmentSequence ?? 0}`
          segmentCitations.set(key, data.citationIds || [])
        } else if (data.event === 'asr.final') {
          const finalText = normalizeText(data.text || '')
          if (isMeaningfulText(finalText)) {
            addChatMessage('user', finalText)
            saveVoiceUserTurn(data.turnId, finalText)
          }
        } else if (data.event === 'llm.delta') {
          isAnswering.value = true
          assistantText.value += data.text || ''
          appendAssistantDelta(data.turnId, data.text || '')
          recordingStatusText.value = '正在生成并播报智能回复...'
        } else if (data.event === 'tts.first_audio') {
          isAnswering.value = true
          recordingStatusText.value = '正在语音播报；需要补充时可点击“打断并继续说”'
        } else if (data.event === 'playback.started') {
          activeCitationIds.value = segmentCitations.get(`${data.turnId || ''}:${data.segmentSequence ?? 0}`) || []
        } else if (data.event === 'playback.stopped') {
          activeCitationIds.value = []
        } else if (data.event === 'turn.completed') {
          clearAsrFinalWait()
          saveAssistantTurn(data.turnId)
          recordingStatusText.value = '本轮回答已生成，等待播报结束...'
        } else if (data.event === 'turn.cancelled') {
          clearAsrFinalWait()
          isAnswering.value = false
          saveAssistantTurn(data.turnId)
          recordingStatusText.value = '本轮已中断，已停止播报'
        } else if (data.event === 'turn.failed') {
          clearAsrFinalWait()
          isAnswering.value = false
          recordingStatusText.value = data.message || '本轮处理失败'
          addChatMessage('assistant', '本轮未完成：' + recordingStatusText.value)
          readyForNextTurn.value = true
        }
      },
      playbackEndedHandle: () => {
        isAnswering.value = false
        if (!conversationActive.value) return
        readyForNextTurn.value = true
        recordingStatusText.value = '本轮播报完成，点击“继续对话”开始下一轮'
      },
      msgHandle: (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data)
          const rectxt = data.text || ''
          const asrmodel = data.mode || ''

          const cleanText = normalizeText(rectxt)
          if (!isMeaningfulText(cleanText)) {
            return
          }

          if (asrmodel.includes('offline')) {
            clearAsrFinalWait()
            draftTranscriptionText.value = ''
            finalTranscriptionText.value = mergeCommittedText(finalTranscriptionText.value, cleanText)
            recordingStatusText.value = '已收到稳定识别结果'
            return
          }

          if (asrmodel.includes('online')) {
            draftTranscriptionText.value = mergeRealtimeDraft(draftTranscriptionText.value, cleanText)
            recordingStatusText.value = '正在录音中...实时识别中'
            return
          }

          finalTranscriptionText.value = mergeCommittedText(finalTranscriptionText.value, cleanText)
        } catch (error) {
          console.error('处理消息失败:', error)
        }
      },
      stateHandle: (state: number) => {
        switch (state) {
          case 0: // 连接成功
            isConnected.value = true
            audioRecorder.setConnected(true)
            resetWaveform()
            startWaveformDecay()
            console.log('WebSocket连接成功')
            break
          case 1: // 连接关闭
            isConnected.value = false
            conversationActive.value = false
            isAnswering.value = false
            recordingStatusText.value = 'WebSocket连接已关闭'
            if (isRecording.value) {
              isRecording.value = false
              audioRecorder.stopRecording()
            }
            window.setTimeout(resetWaveform, 220)
            break
          case 2: // 连接错误
            isConnected.value = false
            conversationActive.value = false
            isAnswering.value = false
            recordingStatusText.value = realtimeFailureReason || 'WebSocket连接错误，请检查服务器是否运行'
            ElMessage.error(realtimeFailureReason || 'WebSocket连接失败，请检查服务器状态')
            realtimeFailureReason = ''
            if (isRecording.value) {
              isRecording.value = false
              audioRecorder.stopRecording()
            }
            window.setTimeout(resetWaveform, 220)
            break
        }
      }
    })

    const observeSpeechActivity = (level: number) => {
      if (!isRecording.value || autoStopping) return
      const now = Date.now()
      if (level >= speechThreshold) {
        speechDetected = true
        silenceStartedAt = null
        return
      }
      if (!speechDetected) return
      if (silenceStartedAt === null) {
        silenceStartedAt = now
        return
      }
      if (now - silenceStartedAt >= autoStopSilenceMs) {
        autoStopping = true
        isRecording.value = false
        recordingStatusText.value = '检测到你已说完，正在自动提交识别结果...'
        stopRecording(true)
      }
    }

    const ensureUserLoggedIn = () => {
      const userId = Number(store.state.user?._id)
      if (!userId) {
        ElMessage.warning('请先登录后再使用实时语音转文字')
        if (router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
        return false
      }
      return true
    }

    const calculateSimilarity = (text1: string, text2: string): number => {
      const len1 = text1.length
      const len2 = text2.length

      if (len1 === 0 || len2 === 0) return 0

      // 使用最长公共子序列计算相似度
      const matrix: number[][] = Array(len1 + 1).fill(null).map(() => Array(len2 + 1).fill(0))

      for (let i = 1; i <= len1; i++) {
        for (let j = 1; j <= len2; j++) {
          if (text1[i - 1] === text2[j - 1]) {
            matrix[i][j] = matrix[i - 1][j - 1] + 1
          } else {
            matrix[i][j] = Math.max(matrix[i - 1][j], matrix[i][j - 1])
          }
        }
      }

      const lcs = matrix[len1][len2]
      return (2 * lcs) / (len1 + len2)
    }

    const isChineseTextFragment = (shortText: string, longText: string): boolean => {
      if (!/[\u4e00-\u9fa5]/.test(shortText) || !/[\u4e00-\u9fa5]/.test(longText)) {
        return false
      }

      if (longText.length - shortText.length < 2) {
        return false
      }

      if (longText.startsWith(shortText)) {
        return true
      }

      const shortChars = shortText.split('')
      const longChars = longText.split('')

      let shortIndex = 0
      for (const longChar of longChars) {
        if (shortChars[shortIndex] === longChar) {
          shortIndex++
          if (shortIndex === shortChars.length) {
            return true
          }
        }
      }

      return false
    }

    const startListeningTurn = async (interrupted = false) => {
      if (!conversationActive.value || isRecording.value || startingListeningTurn) return
      startingListeningTurn = true
      clearNextListening()
      resetSpeechDetector()
      readyForNextTurn.value = false
      isAnswering.value = false
      draftTranscriptionText.value = ''
      isRecording.value = true
      recordingStatusText.value = interrupted
        ? '已打断旧回答，正在听你说话...'
        : '正在聆听，请直接说话...'
      try {
        await wsConnectMethod.wsStart()
        await startRecording()
      } catch (_) {
        isRecording.value = false
        conversationActive.value = false
      } finally {
        startingListeningTurn = false
      }
    }

    const startRecording = async () => {
      try {
        await audioRecorder.startRecording((audioData: ArrayBuffer) => {
          const level = updateWaveform(audioData)
          observeSpeechActivity(level)
          if (wsConnectMethod.isConnected()) {
            wsConnectMethod.wsSend(audioData)
          }
        })

        recordingStatusText.value = '正在录音中...请说话'

      } catch (error) {
        console.error('录音失败:', error)
        isRecording.value = false
        recordingStatusText.value = '录音失败，请检查麦克风权限'

        ElMessage.error({
          message: '无法访问麦克风，请检查：\n1. 浏览器是否允许访问麦克风\n2. 麦克风是否正常工作\n3. 其他应用是否正在使用麦克风',
          duration: 5000,
          showClose: true
        })
      }
    }

    const stopRecording = (automatically = false) => {
      const remainingData = audioRecorder.stopRecording()

      if (remainingData && remainingData.byteLength > 0 && wsConnectMethod.isConnected()) {
        wsConnectMethod.wsSend(remainingData)
      }

      const stopConfig = {
        "chunk_size": [...REALTIME_CHUNK_SIZE],
        "wav_name": "microphone",
        "is_speaking": false,
        "chunk_interval": REALTIME_CHUNK_INTERVAL,
        "mode": "2pass"
      }

      wsConnectMethod.wsSend(JSON.stringify(stopConfig))

      clearAsrFinalWait()
      recordingStatusText.value = automatically
        ? '已自动结束录音，正在等待稳定识别结果...'
        : '录音已停止，正在等待稳定识别结果...'
      resetWaveform()
      // FunASR must first return an offline final result. Do not claim that
      // the LLM is working before this event has actually arrived.
      asrFinalWaitTimer = window.setTimeout(() => {
        if (!finalTranscriptionText.value && !draftTranscriptionText.value) {
          recordingStatusText.value = '暂未收到识别结果：请说话至少 2 秒后再停止录音，然后重试'
        }
      }, 15_000)
    }

    const toggleRecording = async () => {
      if (!conversationActive.value && !ensureUserLoggedIn()) {
        return
      }

      if (!conversationActive.value) {
        conversationActive.value = true
        readyForNextTurn.value = false
        isAnswering.value = false
        clearNextListening()
        // Ending or switching input mode must not erase the visible chat.
        // Only the explicit “清空” button below is allowed to clear history.
        draftTranscriptionText.value = ''
        activeCitationIds.value = []
        await startListeningTurn()
      } else {
        conversationActive.value = false
        isAnswering.value = false
        clearAsrFinalWait()
        clearNextListening()
        if (isRecording.value) {
          isRecording.value = false
          audioRecorder.stopRecording()
        }
        wsConnectMethod.wsStop()
        resetWaveform()
        recordingStatusText.value = '对话已结束'
      }
    }

    const interruptAndListen = async () => {
      if (!conversationActive.value || isRecording.value) return
      await startListeningTurn(true)
    }

    const stopAssistantResponse = () => {
      if (!isAnswering.value) return
      clearAsrFinalWait()
      wsConnectMethod.wsInterrupt()
      activeCitationIds.value = []
      isAnswering.value = false
      readyForNextTurn.value = true
      recordingStatusText.value = '已停止本轮回答，可继续输入或开始下一轮对话'
      ElMessage.info('已停止回答')
    }

    const sendTextPrompt = async () => {
      const prompt = textPrompt.value.trim()
      if (!prompt || !ensureUserLoggedIn() || isTextSending.value) return
      isTextSending.value = true
      clearAsrFinalWait()
      if (isRecording.value) {
        isRecording.value = false
        audioRecorder.stopRecording()
        resetWaveform()
      }
      resetSpeechDetector()
      activeCitationIds.value = []
      addChatMessage('user', prompt)
      void saveHistoryMessage('user', prompt, 'text')
      recordingStatusText.value = '正在发送文字问题并生成智能回复...'
      try {
        await wsConnectMethod.wsStartText(prompt)
        isAnswering.value = true
        textPrompt.value = ''
      } catch (_) {
        recordingStatusText.value = realtimeFailureReason || '文字问题发送失败'
      } finally {
        isTextSending.value = false
      }
    }

    const copyText = async () => {
      try {
        await navigator.clipboard.writeText(transcriptionText.value)
        ElMessage.success('文本已复制到剪贴板')
      } catch (error) {
        ElMessage.error('复制失败，请手动复制')
      }
    }

    const clearText = async () => {
      try {
        await clearRealtimeHistory()
        finalTranscriptionText.value = ''
        draftTranscriptionText.value = ''
        assistantText.value = ''
        citations.value = {}
        activeCitationIds.value = []
        segmentCitations.clear()
        chatMessages.value = []
        assistantMessageIds.clear()
        persistedUserTurnIds.clear()
        persistedAssistantTurnIds.clear()
        ElMessage.success('该账号的对话记录已清空')
      } catch (error) {
        ElMessage.error('清空对话记录失败，请稍后重试')
      }
    }

    const testConnection = async () => {
      if (!ensureUserLoggedIn()) {
        return
      }

      recordingStatusText.value = '正在测试WebSocket连接...'
      ElMessage.info('正在测试WebSocket连接')

      try {
        await wsConnectMethod.wsTest()
        ElMessage.success('WebSocket连接测试成功')
      } catch (_) {
        // stateHandle has already displayed the precise safe failure reason.
      }
    }

    onMounted(() => {
      void loadChatHistory()
    })

    onUnmounted(() => {
      clearAsrFinalWait()
      clearNextListening()
      conversationActive.value = false
      stopWaveformDecay()
      if (isRecording.value) {
        stopRecording()
      }
      audioRecorder.dispose()
      wsConnectMethod.wsStop()
    })

    return {
      isRecording,
      conversationActive,
      readyForNextTurn,
      chatMessages,
      chatScrollRef,
      transcriptionText,
      assistantText,
      citationEntries,
      activeCitationIds,
      textPrompt,
      isTextSending,
      isAnswering,
      waveformBars,
      quickStartSteps,
      quickStartTips,
      recordingStatusText,
      isConnected,
      toggleRecording,
      interruptAndListen,
      stopAssistantResponse,
      sendTextPrompt,
      copyText,
      clearText,
      testConnection
    }
  }
})
</script>

<style scoped>
.realtime-voice-page {
  padding-bottom: 24px;
}

.realtime-card {
  position: relative;
  overflow: hidden;
  max-width: 1040px;
  margin: 0 auto;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74) 0%, rgba(246, 249, 255, 0.88) 100%);
  padding: 30px;
  box-shadow: 0 28px 68px rgba(19, 39, 78, 0.14);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.realtime-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

.realtime-card::after {
  content: '';
  position: absolute;
  left: -40px;
  top: -34px;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.16) 0%, rgba(124, 92, 255, 0) 72%);
  pointer-events: none;
}

.hero-section {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 24px;
  border-radius: 18px;
  border: 1px solid rgba(150, 134, 228, 0.18);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.58) 0%, rgba(235, 243, 255, 0.82) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.hero-section::after {
  content: '';
  position: absolute;
  right: -70px;
  top: -40px;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.18) 0%, rgba(124, 92, 255, 0) 72%);
  pointer-events: none;
}

.hero-copy {
  max-width: 640px;
  position: relative;
  z-index: 1;
}

.hero-badge {
  margin: 0 0 8px;
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(124, 92, 255, 0.1);
  color: #7c5cff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.hero-section h2 {
  margin: 2px 0 12px;
  color: #22314f;
  font-size: 40px;
  line-height: 1.05;
  letter-spacing: -0.04em;
}

.hero-text {
  margin: 0;
  color: #5f6f8b;
  line-height: 1.8;
  font-size: 15px;
}

.hero-status {
  position: relative;
  z-index: 1;
  min-width: 236px;
  border-radius: 18px;
  padding: 20px 22px 20px 28px;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-shadow: 0 22px 40px rgba(106, 79, 224, 0.22);
  overflow: hidden;
}

.hero-status::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.hero-status::after {
  content: '';
  position: absolute;
  right: -34px;
  bottom: -46px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 72%);
}

.hero-status-label,
.hero-status small {
  font-size: 13px;
  opacity: 0.84;
}

.hero-status strong {
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.2;
}

.hero-status small {
  margin-top: 10px;
  line-height: 1.6;
}

.toolbar {
  margin-bottom: 24px;
}

.hint-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hint-chip {
  padding: 9px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(150, 134, 228, 0.16);
  color: #4f648f;
  font-size: 13px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.voice-recording-area {
  width: 100%;
  margin-bottom: 30px;
}

.conversation-stream {
  width: min(720px, 100%);
  margin: 0 0 18px;
  overflow: hidden;
  border: 1px solid rgba(126, 104, 225, 0.16);
  border-radius: 16px;
  background: rgba(248, 250, 255, 0.72);
}

.conversation-stream__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 46px;
  padding: 9px 14px;
  border-bottom: 1px solid rgba(126, 104, 225, 0.12);
}

.conversation-stream__header > div {
  display: grid;
  gap: 2px;
}

.conversation-stream__header span {
  color: #43516d;
  font-size: 14px;
  font-weight: 700;
}

.conversation-stream__header small {
  color: #8995ae;
  font-size: 12px;
}

.conversation-stream__body {
  height: 190px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 14px;
  scroll-behavior: smooth;
}

.conversation-stream__empty {
  margin: auto;
  color: #96a2ba;
  font-size: 14px;
}

.chat-message {
  display: grid;
  gap: 5px;
  max-width: 82%;
}

.chat-message--user {
  align-self: flex-end;
}

.chat-message--assistant {
  align-self: flex-start;
}

.chat-message__role {
  color: #8390aa;
  font-size: 12px;
  font-weight: 600;
}

.chat-message--user .chat-message__role {
  text-align: right;
  color: #6877d9;
}

.chat-message p {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  color: #33415c;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message--assistant p {
  border: 1px solid rgba(126, 104, 225, 0.14);
  border-top-left-radius: 4px;
  background: rgba(255, 255, 255, 0.9);
}

.chat-message--user p {
  border-top-right-radius: 4px;
  background: linear-gradient(135deg, #7663e8, #5c87ee);
  color: #fff;
}

.chat-citations {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(105, 91, 220, 0.08);
  color: #65728d;
  font-size: 12px;
  line-height: 1.5;
}

.chat-citations strong {
  color: #6658d2;
  font-size: 12px;
}

.chat-citations__active {
  background: rgba(89, 104, 255, 0.14);
  color: #3f54d8 !important;
  box-shadow: inset 0 0 0 1px rgba(89, 104, 255, 0.22);
}

.inline-text-entry {
  width: min(720px, 100%);
  display: grid;
  gap: 9px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid rgba(126, 104, 225, 0.16);
  border-radius: 16px;
  background: rgba(248, 250, 255, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.inline-text-entry__header,
.inline-text-entry__composer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.inline-text-entry__header span {
  color: #43516d;
  font-size: 14px;
  font-weight: 600;
}

.inline-text-entry__header small {
  color: #7786a1;
  font-size: 12px;
}

.inline-text-entry__composer :deep(.el-textarea__inner) {
  min-height: 52px !important;
  padding: 11px 12px;
  border-radius: 10px;
  background: rgba(246, 248, 255, 0.84);
  border-color: rgba(126, 104, 225, 0.18);
  box-shadow: none;
}

.inline-text-entry__composer .el-button {
  align-self: stretch;
  min-width: 72px;
  margin: 0;
  border-radius: 10px;
}

.inline-text-entry__composer .composer-stop-button {
  min-width: 88px;
  gap: 8px;
  color: #fff;
  border: 0;
  background: linear-gradient(135deg, #4d82e7 0%, #2f66cc 100%);
  box-shadow: 0 10px 20px rgba(47, 102, 204, 0.22);
}

.inline-text-entry__composer .composer-stop-button:hover,
.inline-text-entry__composer .composer-stop-button:focus-visible {
  color: #fff;
  background: linear-gradient(135deg, #3f74db 0%, #255bbd 100%);
}

.stop-answer-icon {
  display: inline-block;
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  border-radius: 3px;
  background: currentColor;
}

.interrupt-btn .stop-answer-icon {
  margin-right: 2px;
}

.recording-content {
  width: 100%;
  min-height: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(243, 248, 255, 0.96) 100%);
  border: 1px solid rgba(148, 128, 238, 0.2);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  padding: 32px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), inset 0 0 0 1px rgba(148, 128, 238, 0.08);
  transition: all 0.3s ease;
}

.recording-content:hover {
  border-color: #7c5cff;
  background: linear-gradient(180deg, rgba(250, 252, 255, 0.94) 0%, rgba(236, 245, 255, 0.98) 100%);
  box-shadow: 0 20px 40px rgba(106, 79, 224, 0.12);
}

/* 录音激活时的样式 - 区域变小 */
.recording-content.recording-active {
  min-height: 0;
  padding: 26px 30px;
}

.recording-content.recording-active .recording-icon-wrapper {
  margin-bottom: 20px;
}

.recording-content.recording-active .microphone-circle {
  width: 80px;
  height: 80px;
}

.recording-content.recording-active .microphone-icon {
  font-size: 34px;
}

.recording-content.recording-active .main-text {
  font-size: 18px;
  margin-bottom: 10px;
}

.recording-content.recording-active .support-text {
  font-size: 14px;
}

.recording-content.recording-active .waveform-shell {
  height: 64px;
  margin-bottom: 20px;
}

.recording-content.recording-active .wave-bar {
  width: 8px;
  height: 52px;
}

.recording-content.recording-active .recording-button-wrapper .el-button--primary {
  padding: 12px 25px;
  font-size: 16px;
}

/* 录音图标容器 */
.recording-icon-wrapper {
  margin-bottom: 14px;
}

/* 麦克风圆形容器 */
.microphone-circle {
  position: relative;
  width: 94px;
  height: 94px;
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.16) 0%, rgba(41, 121, 255, 0.22) 100%);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.74), 0 16px 30px rgba(43, 90, 210, 0.14);
  transition: all 0.3s ease;
  overflow: hidden;
}

.microphone-circle.recording {
  background: linear-gradient(135deg, rgba(245, 108, 108, 0.18) 0%, rgba(231, 76, 60, 0.24) 100%);
  animation: pulse 1.5s infinite;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.74), 0 18px 34px rgba(245, 108, 108, 0.16);
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
  }
}

/* 麦克风图标 */
.microphone-ring {
  position: absolute;
  border-radius: 18px;
  border: 1.5px solid rgba(124, 92, 255, 0.18);
}

.microphone-ring--outer {
  inset: 12px;
}

.microphone-ring--inner {
  inset: 24px;
  border-color: rgba(124, 92, 255, 0.12);
}

.microphone-live-dot {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 11px;
  height: 11px;
  border-radius: 999px;
  background: linear-gradient(135deg, #7ea9ff 0%, #7c5cff 100%);
  box-shadow: 0 0 0 7px rgba(124, 92, 255, 0.08);
  transition: background 0.25s ease, box-shadow 0.25s ease;
}

.microphone-live-dot--active {
  background: linear-gradient(135deg, #ff9b9b 0%, #f56c6c 100%);
  box-shadow: 0 0 0 7px rgba(245, 108, 108, 0.12);
}

.microphone-icon {
  font-size: 40px;
  color: #7058e6;
  z-index: 1;
}

/* 录音文字区域 */
.recording-text {
  text-align: center;
  max-width: 450px;
  margin-bottom: 16px;
}

.waveform-shell {
  width: min(560px, 100%);
  height: 86px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  margin: 0 0 18px;
  padding: 0 10px;
}

.waveform-shell--active {
  opacity: 1;
}

.wave-bar {
  width: 10px;
  height: 68px;
  min-height: 12px;
  border-radius: 999px;
  transform-origin: center bottom;
  background: linear-gradient(180deg, rgba(114, 171, 255, 0.92) 0%, rgba(106, 79, 224, 0.96) 100%);
  box-shadow: 0 8px 18px rgba(106, 79, 224, 0.16);
  transition: transform 90ms ease, opacity 90ms ease, background 160ms ease;
}

.main-text {
  font-size: 24px;
  color: #2a3856;
  line-height: 1.6;
  font-weight: 700;
  margin-bottom: 15px;
}

.support-text {
  font-size: 16px;
  color: #7987a3;
}

.connection-status {
  font-size: 14px;
  margin-top: 10px;
  padding: 5px 10px;
  border-radius: 15px;
  display: inline-block;
}

.connection-status.connected {
  background-color: rgba(240, 249, 255, 0.92);
  color: #67c23a;
  border: 1px solid #67c23a;
}

.connection-status.disconnected {
  background-color: rgba(254, 240, 240, 0.92);
  color: #f56c6c;
  border: 1px solid #f56c6c;
}

/* 按钮容器 */
.recording-button-wrapper {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.test-btn {
  margin: 0;
}

/* 按钮样式 */
.recording-button-wrapper .el-button--primary {
  background: linear-gradient(135deg, #7c5cff 0%, #6a4fe0 100%);
  border: none;
  border-radius: 50px;
  padding: 18px 40px;
  font-size: 18px;
  font-weight: 500;
  box-shadow: 0 18px 34px rgba(124, 92, 255, 0.24);
  transition: all 0.3s ease;
}

.recording-button-wrapper .el-button--primary:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 25px rgba(124, 92, 255, 0.4);
}

.recording-button-wrapper .el-button--primary.recording {
  background: linear-gradient(135deg, #f56c6c 0%, #e74c3c 100%);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.3);
}

.recording-button-wrapper .el-button--primary.recording:hover {
  box-shadow: 0 10px 25px rgba(245, 108, 108, 0.4);
}

.recording-button-wrapper .el-button--primary:active {
  transform: translateY(0);
}

/* 转换结果区域 */
.transcription-area {
  padding: 24px;
  background: #fbfdff;
  border-radius: 16px;
  border: 1px solid #e8f0ff;
}

.transcription-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.transcription-text {
  padding: 20px;
  background: #f7faff;
  border-radius: 12px;
  border: 1px solid #e4ecff;
  font-size: 16px;
  line-height: 1.6;
  color: #2c3e50;
  white-space: pre-wrap;
  word-wrap: break-word;
  min-height: 100px;
  max-height: 300px;
  overflow-y: auto;
}

.transcription-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.citation-list {
  display: grid;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(99, 102, 241, 0.06);
  color: #44516a;
  font-size: 14px;
}

.citation-item {
  padding: 9px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
}

.citation-item b {
  color: #635bdb;
  margin-right: 6px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 14px;
}

.result-label {
  display: inline-block;
  margin-bottom: 8px;
  color: #7d8ba6;
  font-size: 13px;
}

.result-header h4 {
  margin: 0;
  color: #24324b;
  font-size: 22px;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .hero-section {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-status {
    width: 100%;
  }

  .recording-content {
    height: 450px;
    padding: 30px 20px;
  }

  .microphone-circle {
    width: 90px;
    height: 90px;
  }

  .main-text {
    font-size: 18px;
  }

  .support-text {
    font-size: 14px;
  }

  .recording-button-wrapper .el-button--primary {
    padding: 15px 30px;
    font-size: 16px;
  }

  .test-btn {
    margin-left: 0;
    margin-top: 10px;
  }

  .transcription-actions {
    flex-direction: column;
  }

  .inline-text-entry__header,
  .inline-text-entry__composer {
    align-items: flex-start;
    flex-direction: column;
  }

  .inline-text-entry__composer .el-button {
    align-self: auto;
  }
}

:global(html[data-auth-theme-mode='dark'] .realtime-card) {
  background: linear-gradient(145deg, rgba(27, 27, 31, 0.96) 0%, rgba(35, 35, 40, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.24);
  box-shadow: 0 24px 54px rgba(6, 12, 28, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .hero-section) {
  background: linear-gradient(135deg, rgba(37, 37, 42, 0.96) 0%, rgba(44, 44, 50, 0.92) 100%);
}

:global(html[data-auth-theme-mode='dark'] .voice-recording-area),
:global(html[data-auth-theme-mode='dark'] .transcription-area) {
  background: rgba(13, 22, 43, 0.82);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .recording-content) {
  background:
    radial-gradient(circle at 50% 16%, rgba(124, 92, 255, 0.18), transparent 18%),
    linear-gradient(180deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 48%, rgba(19, 32, 61, 0.96) 100%);
  border-color: rgba(148, 128, 250, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    inset 0 -1px 0 rgba(86, 119, 214, 0.08),
    0 24px 40px rgba(12, 12, 14, 0.35);
}

:global(html[data-auth-theme-mode='dark'] .recording-content:hover) {
  background:
    radial-gradient(circle at 50% 16%, rgba(130, 100, 248, 0.22), transparent 18%),
    linear-gradient(180deg, rgba(12, 22, 42, 0.99) 0%, rgba(17, 30, 56, 0.98) 48%, rgba(24, 39, 72, 0.97) 100%);
  border-color: rgba(148, 128, 250, 0.66);
}

:global(html[data-auth-theme-mode='dark'] .microphone-circle) {
  background: linear-gradient(135deg, rgba(60, 102, 224, 0.18) 0%, rgba(40, 63, 128, 0.42) 100%);
  box-shadow:
    0 18px 34px rgba(8, 16, 34, 0.34),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

:global(html[data-auth-theme-mode='dark'] .microphone-ring--outer) {
  border-color: rgba(143, 177, 255, 0.2);
}

:global(html[data-auth-theme-mode='dark'] .microphone-ring--inner) {
  border-color: rgba(143, 177, 255, 0.12);
}

:global(html[data-auth-theme-mode='dark'] .microphone-icon) {
  color: #8db2ff;
}

:global(html[data-auth-theme-mode='dark'] .wave-bar) {
  background: linear-gradient(180deg, rgba(137, 182, 255, 0.94) 0%, rgba(60, 128, 255, 0.96) 100%);
  box-shadow:
    0 10px 20px rgba(34, 99, 223, 0.24),
    0 0 18px rgba(72, 126, 255, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .hero-badge) {
  color: #8db2ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-section h2),
:global(html[data-auth-theme-mode='dark'] .main-text),
:global(html[data-auth-theme-mode='dark'] .transcription-text),
:global(html[data-auth-theme-mode='dark'] .result-header h4),
:global(html[data-auth-theme-mode='dark'] .inline-text-entry__header span) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-text),
:global(html[data-auth-theme-mode='dark'] .support-text),
:global(html[data-auth-theme-mode='dark'] .recording-status),
:global(html[data-auth-theme-mode='dark'] .recording-tip),
:global(html[data-auth-theme-mode='dark'] .result-label),
:global(html[data-auth-theme-mode='dark'] .inline-text-entry__header small) {
  color: rgba(218, 229, 255, 0.82);
}

:global(html[data-auth-theme-mode='dark'] .hint-chip) {
  background: rgba(31, 31, 35, 0.92);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .connection-status.connected) {
  background: rgba(34, 74, 34, 0.22);
  color: #8fe06f;
  border-color: rgba(115, 214, 82, 0.72);
}

:global(html[data-auth-theme-mode='dark'] .connection-status.disconnected) {
  background: rgba(92, 33, 43, 0.24);
  color: #ff9fa7;
  border-color: rgba(255, 129, 141, 0.72);
}

:global(html[data-auth-theme-mode='dark'] .test-btn) {
  background: rgba(27, 41, 73, 0.94);
  border-color: rgba(140, 112, 236, 0.22);
  color: #d8e5ff;
}

:global(html[data-auth-theme-mode='dark'] .test-btn:hover) {
  background: rgba(33, 50, 88, 0.98);
  border-color: rgba(148, 128, 250, 0.4);
  color: #f4f7ff;
}

:global(html[data-auth-theme-mode='dark'] .transcription-area) {
  background: rgba(14, 22, 42, 0.84);
  border-color: rgba(138, 112, 236, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .inline-text-entry) {
  background: rgba(20, 27, 49, 0.88);
  border-color: rgba(138, 112, 236, 0.3);
}

:global(html[data-auth-theme-mode='dark'] .chat-citations__active) {
  background: rgba(121, 135, 255, 0.2);
  color: #b8c5ff !important;
}

:global(html[data-auth-theme-mode='dark'] .inline-text-entry .el-textarea__inner) {
  background: rgba(13, 18, 34, 0.9);
  color: #eef3ff;
  border-color: rgba(138, 112, 236, 0.22);
}

:global(html[data-auth-theme-mode='dark'] .transcription-text) {
  background: rgba(18, 18, 21, 0.94);
  border-color: rgba(138, 112, 236, 0.18);
  color: #eef3ff;
}
</style>
