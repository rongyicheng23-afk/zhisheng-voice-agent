<template>
  <flow-background-panel>
    <div class="meeting-page container-md" style="margin-top: 10px;">
      <div class="meeting-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div>
            <p class="hero-badge">智能纪要</p>
            <h2>会议 / 课堂纪要</h2>
            <p class="hero-text">把长音频整理成可导出、可搜索、可校正的结构化纪要，支持本地上传和录音历史复用。</p>
          </div>
          <div class="hero-side">
            <span>当前模式</span>
            <strong>{{ sceneLabel }}</strong>
            <small>{{ sceneDescription }}</small>
          </div>
        </div>

        <quick-start-panel
          storage-key="meeting-home"
          title="第一次用智能纪要，推荐这样完成一轮体验"
          subtitle="你可以上传一段会议或课堂音频，系统会自动转写、提炼摘要，并把结果归档到历史。"
          :steps="quickStartSteps"
          :tips="quickStartTips"
        />

        <div class="toolbar">
          <div class="hint-list">
            <span class="hint-chip">全文转写</span>
            <span class="hint-chip">自动摘要</span>
            <span class="hint-chip">待办提取</span>
            <span class="hint-chip">历史可追溯</span>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" plain class="history-btn" @click="openAudioHistoryDrawer">
              <el-icon class="el-icon--left"><UploadFilled /></el-icon>
              从录音历史导入
            </el-button>
            <el-button type="primary" plain class="history-btn" @click="openHistoryDrawer">
              <el-icon class="el-icon--left"><Clock /></el-icon>
              历史记录
            </el-button>
          </div>
        </div>

        <div class="stats-grid">
          <article class="stats-card">
            <span>累计纪要</span>
            <strong>{{ stats.totalNotes }}</strong>
            <small>近 7 天新增 {{ stats.recentSevenDaysNotes }}</small>
          </article>
          <article class="stats-card">
            <span>会议 / 课堂</span>
            <strong>{{ stats.meetingNotes }} / {{ stats.classroomNotes }}</strong>
            <small>覆盖会议与课堂双场景</small>
          </article>
          <article class="stats-card">
            <span>成功率</span>
            <strong>{{ successRate }}</strong>
            <small>成功 {{ stats.successNotes }}，失败 {{ stats.failedNotes }}</small>
          </article>
          <article class="stats-card">
            <span>发言人与片段</span>
            <strong>{{ stats.speakerProfiles }} / {{ stats.totalSegments }}</strong>
            <small>档案数 / 识别片段数</small>
          </article>
          <article class="stats-card">
            <span>待办事项</span>
            <strong>{{ stats.totalTodos }}</strong>
            <small>累计提取的待办线索</small>
          </article>
          <article class="stats-card">
            <span>最近一次生成</span>
            <strong>{{ latestLabel }}</strong>
            <small>{{ stats.latestCreateTime || '暂无历史数据' }}</small>
          </article>
        </div>

        <div class="form-grid">
          <section class="form-panel">
            <h4 class="section-title">任务信息</h4>
            <el-form label-position="top">
              <el-form-item label="纪要标题">
                <el-input
                  v-model="form.title"
                  maxlength="128"
                  placeholder="例如：软件工程组会 / 数据库课程第 3 讲"
                />
              </el-form-item>
              <el-form-item label="场景模式">
                <el-radio-group v-model="form.sceneType">
                  <el-radio-button label="meeting">会议模式</el-radio-button>
                  <el-radio-button label="classroom">课堂模式</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="发言人区分">
                <el-select
                  v-model="form.selectedSpeakerIds"
                  multiple
                  collapse-tags
                  collapse-tags-tooltip
                  clearable
                  filterable
                  placeholder="选择已注册的发言人档案"
                >
                  <el-option
                    v-for="speaker in speakerProfiles"
                    :key="speaker.id"
                    :label="speaker.speakerRole ? `${speaker.speakerName}（${speaker.speakerRole}）` : speaker.speakerName"
                    :value="speaker.id"
                  />
                </el-select>
                <div class="speaker-auto-option">
                  <el-switch v-model="form.autoDiarization" />
                  <span>无样本时自动区分说话人</span>
                </div>
                <div class="speaker-help">
                  <span>不选择样本时生成“说话人 1/2/3”；选择样本后优先匹配档案，未命中的声音仍会自动分组。</span>
                  <el-button type="primary" link @click="openSpeakerDrawer">管理发言人档案</el-button>
                </div>
              </el-form-item>
            </el-form>
          </section>

          <section class="upload-panel">
            <h4 class="section-title">上传录音</h4>
            <el-upload
              ref="uploadRef"
              class="meeting-upload"
              drag
              :auto-upload="false"
              :show-file-list="false"
              accept=".wav,.mp3,.ogg,.flac,.m4a"
              :limit="1"
              @click.capture="handleUploadTrigger"
              :on-change="handleFileChange"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-title">上传会议 / 课堂音频</div>
              <div class="upload-tip">支持 WAV、MP3、OGG、FLAC、M4A，建议单段录音不超过 300MB</div>
            </el-upload>
            <div class="file-meta" :class="{ 'file-meta--empty': !fileName }">
              {{ fileName || '尚未选择音频文件' }}
            </div>
            <div class="submit-row">
              <el-button
                type="primary"
                class="submit-btn"
                :disabled="!selectedFile || submitLoading"
                :loading="submitLoading"
                @click="handleSubmit"
              >
                <el-icon class="el-icon--left"><DocumentChecked /></el-icon>
                {{ submitLoading ? '生成中...' : '生成纪要' }}
              </el-button>
            </div>
          </section>
        </div>

        <div class="result-panel" :class="resultClass">
          <div class="result-header">
            <div>
              <span class="result-label">纪要结果</span>
              <h4>{{ resultTitle }}</h4>
            </div>
            <el-tag :type="resultTagType">{{ resultTagText }}</el-tag>
          </div>

          <p class="result-description">{{ resultDescription }}</p>

          <div v-if="showProcessingStatus" class="processing-card">
            <div class="processing-head">
              <div>
                <span class="processing-kicker">当前进度</span>
                <strong>{{ result.processingLabel || resultTagText }}</strong>
              </div>
              <em>{{ processingPercentValue }}%</em>
            </div>
            <p class="processing-copy">{{ result.processingDescription || resultDescription }}</p>
            <div class="processing-bar">
              <span :style="{ width: `${processingPercentValue}%` }" />
            </div>
            <div class="processing-steps">
              <span
                v-for="step in processingSteps"
                :key="step.key"
                class="processing-step"
                :class="`processing-step--${step.status}`"
              >
                {{ step.label }}
              </span>
            </div>
          </div>

          <div v-if="result.meetingId" class="result-meta">
            <span>纪要编号：#{{ result.meetingId }}</span>
            <span>场景：{{ resultSceneLabel }}</span>
            <el-button type="primary" link @click="goDetail">查看详情</el-button>
          </div>

          <div v-if="result.structuredSections?.length" class="structured-grid">
            <article
              v-for="section in result.structuredSections"
              :key="section.key || section.title"
              class="structured-card"
            >
              <div class="structured-head">
                <span>{{ section.title || '结构化小节' }}</span>
                <small>{{ section.subtitle || '系统正在按场景整理更适合阅读的纪要内容。' }}</small>
              </div>
              <ul class="structured-list">
                <li
                  v-for="(item, index) in section.items || []"
                  :key="`${section.key || section.title}-${index}`"
                >
                  {{ item }}
                </li>
              </ul>
            </article>
          </div>

          <div v-if="result.roleInsights?.length" class="analysis-grid">
            <article class="analysis-card">
              <div class="analysis-head">
                <span>发言角色分析</span>
                <small>快速判断谁在主讲、提问、回应与承接任务。</small>
              </div>
              <div class="analysis-stack">
                <article
                  v-for="insight in result.roleInsights"
                  :key="`${insight.roleKey}-${insight.speakerName}`"
                  class="analysis-item"
                >
                  <div class="analysis-item-head">
                    <strong>{{ insight.speakerName || '待确认发言人' }}</strong>
                    <el-tag size="small" effect="plain">{{ insight.roleLabel || '角色待识别' }}</el-tag>
                  </div>
                  <p>{{ insight.contribution || insight.evidence || '系统正在整理角色判断依据。' }}</p>
                  <small>{{ insight.evidence || '暂无判定依据' }}</small>
                </article>
              </div>
            </article>

            <article class="analysis-card" v-if="result.todoChains?.length">
              <div class="analysis-head">
                <span>待办责任链分析</span>
                <small>识别责任人、时间约束和任务完整度。</small>
              </div>
              <div class="analysis-stack">
                <article
                  v-for="(chain, index) in result.todoChains"
                  :key="`${chain.taskText}-${index}`"
                  class="analysis-item"
                >
                  <div class="analysis-item-head">
                    <strong>{{ chain.owner || '待确认负责人' }}</strong>
                    <span class="chain-status" :class="`chain-status--${chain.statusKey || 'pending'}`">
                      {{ chain.statusLabel || '待确认' }}
                    </span>
                  </div>
                  <p>{{ chain.action || chain.taskText || '暂无任务描述' }}</p>
                  <small>时间约束：{{ chain.deadline || '待补充' }}</small>
                </article>
              </div>
            </article>

            <article class="analysis-card" v-if="result.decisionInsights?.length">
              <div class="analysis-head">
                <span>结论与待确认事项</span>
                <small>区分已经明确的决议和仍需跟进的内容。</small>
              </div>
              <div class="analysis-stack">
                <article
                  v-for="(decision, index) in result.decisionInsights"
                  :key="`${decision.typeKey}-${index}-${decision.content}`"
                  class="analysis-item"
                >
                  <div class="analysis-item-head">
                    <strong>{{ decision.typeLabel || '结论' }}</strong>
                    <el-tag size="small" :type="decision.typeKey === 'confirmed' ? 'success' : 'warning'" effect="plain">
                      {{ decision.typeKey === 'confirmed' ? '已确认' : '待确认' }}
                    </el-tag>
                  </div>
                  <p>{{ decision.content || '暂无结论内容' }}</p>
                  <small v-if="decision.sourceSpeaker">来源发言人：{{ decision.sourceSpeaker }}</small>
                </article>
              </div>
            </article>
          </div>

          <div v-if="result.summaryText" class="content-card">
            <span>摘要</span>
            <p>{{ result.summaryText }}</p>
          </div>

          <div v-if="result.keywords?.length" class="content-card">
            <span>关键词</span>
            <div class="tag-list">
              <el-tag v-for="keyword in result.keywords" :key="keyword" effect="plain">{{ keyword }}</el-tag>
            </div>
          </div>

          <div v-if="result.todos?.length" class="content-card">
            <span>待办事项</span>
            <ul class="todo-list">
              <li v-for="todo in result.todos" :key="todo">{{ todo }}</li>
            </ul>
          </div>

          <div v-if="result.fullTranscript" class="content-card">
            <span>全文转写</span>
            <p class="transcript-text">{{ result.fullTranscript }}</p>
          </div>

          <div v-if="result.speakerTranscript" class="content-card">
            <span>发言人纪要</span>
            <p class="transcript-text">{{ result.speakerTranscript }}</p>
          </div>

          <div v-if="result.speakerBlocks?.length" class="content-card">
            <span>整理后发言块</span>
            <div class="segment-list">
              <article
                v-for="(block, index) in result.speakerBlocks"
                :key="`${block.speakerName || 'unknown'}-${index}`"
                class="segment-item"
              >
                <div class="segment-head">
                  <strong>{{ block.speakerName || '未知发言人' }}</strong>
                  <span>{{ formatRange(block.startMs, block.endMs) }}</span>
                </div>
                <p>{{ block.transcript || '暂无文本' }}</p>
                <small class="segment-extra">合并片段数：{{ block.segmentCount || 1 }}</small>
              </article>
            </div>
          </div>

          <div v-if="result.speakerSegments?.length" class="content-card">
            <span>原始发言片段</span>
            <div class="segment-list">
              <article v-for="segment in result.speakerSegments" :key="segment.id || segment.segmentIndex" class="segment-item">
                <div class="segment-head">
                  <strong>{{ segment.speakerName || '未知发言人' }}</strong>
                  <span>{{ formatRange(segment.startMs, segment.endMs) }}</span>
                </div>
                <p>{{ segment.transcript || '暂无文本' }}</p>
              </article>
            </div>
          </div>
        </div>
      </div>
    </div>

    <speaker-profile-drawer v-model="speakerDrawerVisible" />
    <asr-history-drawer
      v-model="audioHistoryDrawerVisible"
      :show-use-audio-action="true"
      @use-audio="handleUseHistoryAudio"
    />
    <meeting-history-drawer v-model="historyDrawerVisible" />
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { Clock, DocumentChecked, UploadFilled } from '@element-plus/icons-vue'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import MeetingHistoryDrawer from '@/components/MeetingHistoryDrawer.vue'
import SpeakerProfileDrawer from '@/components/SpeakerProfileDrawer.vue'
import AsrHistoryDrawer from '@/components/AsrHistoryDrawer.vue'
import QuickStartPanel from '@/components/QuickStartPanel.vue'
import { createMeetingNote, createMeetingNoteFromHistory, getMeetingHistoryDetail, getMeetingStats, MeetingNoteResult, MeetingStats } from '@/api/meeting'
import { getMySpeakerProfiles, SpeakerProfileItem } from '@/api/speaker'
import type { AudioHistoryItem } from '@/api/history'
import store from '@/store'

export default defineComponent({
  name: 'MeetingNotes',
  components: {
    FlowBackgroundPanel,
    MeetingHistoryDrawer,
    SpeakerProfileDrawer,
    AsrHistoryDrawer,
    QuickStartPanel,
    UploadFilled,
    DocumentChecked,
    Clock
  },
  setup () {
    const router = useRouter()
    const uploadRef = ref<UploadInstance>()
    const selectedFile = ref<File | null>(null)
    const fileName = ref('')
    const submitLoading = ref(false)
    const historyDrawerVisible = ref(false)
    const audioHistoryDrawerVisible = ref(false)
    const speakerDrawerVisible = ref(false)
    const resultPolling = ref(false)
    const speakerProfiles = ref<SpeakerProfileItem[]>([])
    const stats = reactive<MeetingStats>({
      totalNotes: 0,
      meetingNotes: 0,
      classroomNotes: 0,
      successNotes: 0,
      failedNotes: 0,
      speakerProfiles: 0,
      totalSegments: 0,
      totalTodos: 0,
      recentSevenDaysNotes: 0,
      latestCreateTime: ''
    })

    const quickStartSteps = [
      {
        title: '填写标题并选择场景',
        description: '先明确这是会议模式还是课堂模式，系统会按对应场景去组织摘要、待办和纪要内容。'
      },
      {
        title: '上传录音或从历史导入',
        description: '你可以直接上传长音频，也可以复用录音转文字历史中的音频，快速生成一条新的纪要任务。'
      },
      {
        title: '等待生成后查看详情',
        description: '处理完成后进入详情页查看摘要、关键词、待办和时间轴，还能继续人工校正与导出。'
      }
    ]

    const quickStartTips = [
      '无发言人样本也可自动区分说话人',
      '长音频会走后台异步处理',
      '详情页支持时间轴和版本对比'
    ]

    const form = reactive({
      title: '',
      sceneType: 'meeting',
      selectedSpeakerIds: [] as number[],
      autoDiarization: true
    })

    const result = reactive<Partial<MeetingNoteResult>>({
      meetingId: undefined,
      title: '',
      sceneType: 'meeting',
      summaryText: '',
      keywords: [],
      todos: [],
      structuredSections: [],
      roleInsights: [],
      todoChains: [],
      decisionInsights: [],
      fullTranscript: '',
      speakerTranscript: '',
      speakerBlocks: [],
      speakerSegments: [],
      status: undefined,
      processingStage: '',
      processingLabel: '',
      processingDescription: '',
      processingPercent: 0,
      errorMessage: ''
    })
    let resultPollingTimer: number | null = null

    const isLoggedIn = computed(() => Boolean(store.state.token && store.state.user?.isLogin && Number(store.state.user?._id)))

    const sceneLabel = computed(() => (form.sceneType === 'classroom' ? '课堂模式' : '会议模式'))
    const resultSceneLabel = computed(() => (result.sceneType === 'classroom' ? '课堂模式' : '会议模式'))
    const sceneDescription = computed(() =>
      form.sceneType === 'classroom'
        ? '适合课程录音整理、知识点归纳和课堂待办提取'
        : '适合组会、访谈、讨论等场景的纪要整理'
    )
    const successRate = computed(() => {
      if (!stats.totalNotes) {
        return '0%'
      }
      return `${((stats.successNotes / stats.totalNotes) * 100).toFixed(0)}%`
    })
    const latestLabel = computed(() => (stats.latestCreateTime ? '已生成' : '暂无'))

    const resultTagType = computed(() => {
      if (!result.meetingId) return 'info'
      if (result.status === 'SUCCESS') return 'success'
      if (result.status === 'FAILED') return 'danger'
      if (result.status === 'PROCESSING' || result.status === 'PENDING' || result.status === 'UPLOADED') return 'warning'
      return 'info'
    })

    const resultTagText = computed(() => {
      if (!result.meetingId) return '待生成'
      if (result.status === 'SUCCESS') return '已完成'
      if (result.status === 'FAILED') return '失败'
      if (result.status === 'PROCESSING' || result.status === 'PENDING' || result.status === 'UPLOADED') {
        return result.processingLabel || '处理中'
      }
      return result.status || '处理中'
    })

    const resultTitle = computed(() => {
      if (!result.meetingId) return '等待上传会议或课堂音频'
      if (result.status === 'FAILED') return result.title || '纪要生成失败'
      if (result.status === 'PROCESSING' || result.status === 'PENDING' || result.status === 'UPLOADED') {
        return result.title || result.processingLabel || '纪要任务已提交'
      }
      return result.title || '纪要生成完成'
    })

    const resultDescription = computed(() => {
      if (!result.meetingId) {
        return '登录后上传音频，即可得到全文转写、摘要、关键词和待办事项。'
      }
      if (result.status === 'FAILED') {
        return result.errorMessage || '后台处理失败，你可以检查音频内容后重新生成。'
      }
      if (result.status === 'PROCESSING' || result.status === 'PENDING' || result.status === 'UPLOADED') {
        return result.processingDescription || '任务已进入后台处理队列，页面会自动刷新结果，你也可以先进入详情页查看当前状态。'
      }
      return '系统已完成纪要生成，你可以在下方查看摘要、关键词、待办事项和全文转写。'
    })

    const resultClass = computed(() => {
      if (!result.meetingId) return 'result-panel--idle'
      if (result.status === 'FAILED') return 'result-panel--failed'
      if (result.status === 'PROCESSING' || result.status === 'PENDING' || result.status === 'UPLOADED') {
        return 'result-panel--pending'
      }
      return 'result-panel--success'
    })

    const showProcessingStatus = computed(() => Boolean(
      result.meetingId &&
      result.status &&
      result.status !== 'SUCCESS' &&
      result.status !== 'FAILED'
    ))

    const processingPercentValue = computed(() => {
      const percent = Number(result.processingPercent || 0)
      if (Number.isNaN(percent) || percent <= 0) {
        return 12
      }
      return Math.max(8, Math.min(100, Math.round(percent)))
    })

    const processingSteps = computed(() => {
      const currentStage = (result.processingStage || '').toUpperCase()
      const includeSpeakerStep = Boolean(
        form.autoDiarization ||
        form.selectedSpeakerIds.length ||
        result.speakerBlocks?.length ||
        result.speakerSegments?.length ||
        currentStage === 'SPEAKER_MATCHING' ||
        currentStage === 'FINALIZING'
      )
      const steps = [
        { key: 'QUEUED', label: '排队中' },
        { key: 'UPLOADED', label: '音频入库' },
        { key: 'TRANSCRIBING', label: '语音转写' },
        ...(includeSpeakerStep ? [{ key: 'SPEAKER_MATCHING', label: '发言匹配' }] : []),
        { key: 'STRUCTURING', label: '纪要整理' },
        { key: 'FINALIZING', label: '结果封装' },
        { key: 'SUCCESS', label: '完成' }
      ]

      const aliases: Record<string, string> = {
        PENDING: 'QUEUED',
        QUEUED: 'QUEUED',
        AUDIO_READY: 'UPLOADED',
        UPLOADED: 'UPLOADED',
        TRANSCRIBING: 'TRANSCRIBING',
        SPEAKER_MATCHING: 'SPEAKER_MATCHING',
        STRUCTURING: 'STRUCTURING',
        FINALIZING: 'FINALIZING',
        SUCCESS: 'SUCCESS',
        FAILED: 'FINALIZING'
      }
      const normalized = aliases[currentStage] || 'QUEUED'
      const currentIndex = Math.max(0, steps.findIndex(step => step.key === normalized))

      return steps.map((step, index) => ({
        ...step,
        status: index < currentIndex ? 'done' : (index === currentIndex ? 'active' : 'todo')
      }))
    })

    const ensureMeetingAccess = (showMessage = true) => {
      if (isLoggedIn.value) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再使用智能纪要功能')
      }
      router.push('/login')
      return false
    }

    const loadSpeakerProfiles = async () => {
      if (!isLoggedIn.value) {
        speakerProfiles.value = []
        return
      }
      try {
        const res = await getMySpeakerProfiles()
        if (res.code === 200) {
          speakerProfiles.value = Array.isArray(res.data) ? res.data : []
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
        }
      }
    }

    const loadStats = async () => {
      if (!isLoggedIn.value) {
        Object.assign(stats, {
          totalNotes: 0,
          meetingNotes: 0,
          classroomNotes: 0,
          successNotes: 0,
          failedNotes: 0,
          speakerProfiles: 0,
          totalSegments: 0,
          totalTodos: 0,
          recentSevenDaysNotes: 0,
          latestCreateTime: ''
        })
        return
      }
      try {
        const res = await getMeetingStats()
        if (res.code === 200 && res.data) {
          Object.assign(stats, res.data)
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
        }
      }
    }

    const handleUploadTrigger = (event?: Event) => {
      if (ensureMeetingAccess()) {
        return
      }
      event?.preventDefault()
      event?.stopPropagation()
    }

    const handleUnauthorized = () => {
      stopResultPolling()
      store.commit('logout')
      ElMessage.error('登录状态已失效，请重新登录后再使用智能纪要')
      router.push('/login')
    }

    const resetResult = () => {
      result.meetingId = undefined
      result.title = ''
      result.sceneType = form.sceneType as 'meeting' | 'classroom'
      result.summaryText = ''
      result.keywords = []
      result.todos = []
      result.structuredSections = []
      result.roleInsights = []
      result.todoChains = []
      result.decisionInsights = []
      result.fullTranscript = ''
      result.speakerTranscript = ''
      result.speakerBlocks = []
      result.speakerSegments = []
      result.status = undefined
      result.processingStage = ''
      result.processingLabel = ''
      result.processingDescription = ''
      result.processingPercent = 0
      result.errorMessage = ''
    }

    const applyResultPayload = (payload?: Partial<MeetingNoteResult>) => {
      result.meetingId = payload?.meetingId
      result.title = payload?.title || ''
      result.sceneType = (payload?.sceneType || form.sceneType) as 'meeting' | 'classroom'
      result.summaryText = payload?.summaryText || ''
      result.keywords = Array.isArray(payload?.keywords) ? payload?.keywords : []
      result.todos = Array.isArray(payload?.todos) ? payload?.todos : []
      result.structuredSections = Array.isArray(payload?.structuredSections) ? payload?.structuredSections : []
      result.roleInsights = Array.isArray(payload?.roleInsights) ? payload?.roleInsights : []
      result.todoChains = Array.isArray(payload?.todoChains) ? payload?.todoChains : []
      result.decisionInsights = Array.isArray(payload?.decisionInsights) ? payload?.decisionInsights : []
      result.fullTranscript = payload?.fullTranscript || ''
      result.speakerTranscript = payload?.speakerTranscript || ''
      result.speakerBlocks = Array.isArray(payload?.speakerBlocks) ? payload?.speakerBlocks : []
      result.speakerSegments = Array.isArray(payload?.speakerSegments) ? payload?.speakerSegments : []
      result.status = payload?.status
      result.processingStage = payload?.processingStage || ''
      result.processingLabel = payload?.processingLabel || ''
      result.processingDescription = payload?.processingDescription || ''
      result.processingPercent = Number(payload?.processingPercent || 0)
      result.errorMessage = payload?.errorMessage || ''
    }

    const stopResultPolling = () => {
      if (resultPollingTimer !== null) {
        window.clearTimeout(resultPollingTimer)
        resultPollingTimer = null
      }
      resultPolling.value = false
    }

    const scheduleResultPolling = (meetingId: number) => {
      resultPollingTimer = window.setTimeout(() => {
        void pollMeetingResult(meetingId, true)
      }, 2000)
    }

    const pollMeetingResult = async (meetingId: number, silent = false) => {
      try {
        const res = await getMeetingHistoryDetail(meetingId)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.msg || '刷新纪要状态失败')
        }
        applyResultPayload({
          meetingId: res.data.id,
          title: res.data.title,
          sceneType: res.data.sceneType,
          summaryText: res.data.summaryText,
          keywords: res.data.keywords,
          todos: res.data.todos,
          structuredSections: res.data.structuredSections,
          roleInsights: res.data.roleInsights,
          todoChains: res.data.todoChains,
          decisionInsights: res.data.decisionInsights,
          fullTranscript: res.data.fullTranscript,
          speakerTranscript: res.data.speakerTranscript,
          speakerBlocks: res.data.speakerBlocks,
          speakerSegments: res.data.speakerSegments,
          status: res.data.status as MeetingNoteResult['status'],
          processingStage: res.data.processingStage,
          processingLabel: res.data.processingLabel,
          processingDescription: res.data.processingDescription,
          processingPercent: res.data.processingPercent,
          errorMessage: res.data.errorMessage
        })

        if (res.data.status === 'SUCCESS') {
          stopResultPolling()
          await loadStats()
          if (silent) {
            ElMessage.success('智能纪要生成完成')
          }
          return
        }

        if (res.data.status === 'FAILED') {
          stopResultPolling()
          await loadStats()
          if (silent) {
            ElMessage.error(res.data.errorMessage || '智能纪要生成失败')
          }
          return
        }

        resultPolling.value = true
        scheduleResultPolling(meetingId)
      } catch (error: any) {
        stopResultPolling()
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        if (!silent) {
          ElMessage.error(error?.message || '刷新纪要状态失败')
        }
      }
    }

    const beginQueuedResultTracking = async (payload: MeetingNoteResult, successMessage: string) => {
      applyResultPayload(payload)
      await loadStats()
      if (!payload.meetingId) {
        return
      }
      if (payload.status === 'SUCCESS') {
        ElMessage.success(successMessage)
        return
      }
      resultPolling.value = true
      ElMessage.success('任务已提交，系统正在后台生成纪要')
      scheduleResultPolling(payload.meetingId)
    }

    const handleFileChange = (file: UploadFile) => {
      if (!ensureMeetingAccess(false)) {
        uploadRef.value?.clearFiles()
        return
      }
      const raw = file.raw
      if (!raw) return
      if (raw.size > 300 * 1024 * 1024) {
        ElMessage.error('文件大小不能超过 300MB')
        uploadRef.value?.clearFiles()
        return
      }
      selectedFile.value = raw
      fileName.value = raw.name
      uploadRef.value?.clearFiles()
      ElMessage.success('音频已选择')
    }

    const openHistoryDrawer = () => {
      if (!ensureMeetingAccess()) {
        return
      }
      historyDrawerVisible.value = true
    }

    const openAudioHistoryDrawer = () => {
      if (!ensureMeetingAccess()) {
        return
      }
      audioHistoryDrawerVisible.value = true
    }

    const openSpeakerDrawer = () => {
      if (!ensureMeetingAccess()) {
        return
      }
      speakerDrawerVisible.value = true
    }

    const handleSubmit = async () => {
      if (!selectedFile.value) {
        ElMessage.warning('请先选择音频文件')
        return
      }
      if (!ensureMeetingAccess()) {
        return
      }
      try {
        submitLoading.value = true
        stopResultPolling()
        resetResult()
        const formData = new FormData()
        if (form.title.trim()) {
          formData.append('title', form.title.trim())
        }
        formData.append('sceneType', form.sceneType)
        if (form.selectedSpeakerIds.length) {
          formData.append('selectedSpeakerIds', form.selectedSpeakerIds.join(','))
        }
        formData.append('autoDiarization', String(form.autoDiarization))
        formData.append('file', selectedFile.value)

        const res = await createMeetingNote(formData)
        if (res.code !== 200 || !res.data?.meetingId) {
          throw new Error(res.msg || '智能纪要生成失败')
        }
        await beginQueuedResultTracking(res.data, '智能纪要生成完成')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '智能纪要生成失败')
      } finally {
        if (!resultPolling.value) {
          submitLoading.value = false
        } else {
          submitLoading.value = false
        }
      }
    }

    const handleUseHistoryAudio = async (item: AudioHistoryItem) => {
      if (!ensureMeetingAccess()) {
        return
      }
      if (!item?.id) {
        ElMessage.warning('未找到可用的录音历史')
        return
      }
      try {
        submitLoading.value = true
        stopResultPolling()
        resetResult()
        const formData = new FormData()
        if (form.title.trim()) {
          formData.append('title', form.title.trim())
        }
        formData.append('sceneType', form.sceneType)
        if (form.selectedSpeakerIds.length) {
          formData.append('selectedSpeakerIds', form.selectedSpeakerIds.join(','))
        }
        formData.append('autoDiarization', String(form.autoDiarization))

        const res = await createMeetingNoteFromHistory(item.id, formData)
        if (res.code !== 200 || !res.data?.meetingId) {
          throw new Error(res.msg || '从录音历史生成纪要失败')
        }

        fileName.value = `${item.originalFilename}（来自录音历史）`
        selectedFile.value = null
        await beginQueuedResultTracking({
          ...res.data,
          title: res.data.title || form.title.trim() || item.originalFilename
        }, '已从录音历史生成纪要')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '从录音历史生成纪要失败')
      } finally {
        submitLoading.value = false
      }
    }

    const formatRange = (startMs?: number, endMs?: number) => {
      const toText = (value?: number) => {
        if (value === undefined || value === null || value < 0) {
          return '--:--'
        }
        const totalSeconds = Math.floor(value / 1000)
        const minutes = Math.floor(totalSeconds / 60)
        const seconds = totalSeconds % 60
        return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
      }
      return `${toText(startMs)} - ${toText(endMs)}`
    }

    const goDetail = () => {
      if (!result.meetingId) {
        return
      }
      router.push(`/MeetingNotes/${result.meetingId}`)
    }

    onMounted(() => {
      void loadSpeakerProfiles()
      void loadStats()
    })

    onBeforeUnmount(() => {
      stopResultPolling()
    })

    watch(speakerDrawerVisible, visible => {
      if (!visible) {
        void loadSpeakerProfiles()
        void loadStats()
      }
    })

    return {
      uploadRef,
      form,
      selectedFile,
      fileName,
      submitLoading,
      historyDrawerVisible,
      audioHistoryDrawerVisible,
      speakerDrawerVisible,
      speakerProfiles,
      stats,
      quickStartSteps,
      quickStartTips,
      result,
      sceneLabel,
      resultSceneLabel,
      sceneDescription,
      successRate,
      latestLabel,
      resultTagType,
      resultTagText,
      resultTitle,
      resultDescription,
      resultClass,
      showProcessingStatus,
      processingPercentValue,
      processingSteps,
      resultPolling,
      handleUploadTrigger,
      handleFileChange,
      openHistoryDrawer,
      openAudioHistoryDrawer,
      openSpeakerDrawer,
      handleSubmit,
      handleUseHistoryAudio,
      formatRange,
      goDetail
    }
  }
})
</script>

<style scoped>
.meeting-page {
  padding-bottom: 24px;
}

.meeting-card {
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

.meeting-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

.meeting-card::after {
  content: '';
  position: absolute;
  right: -44px;
  top: -42px;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(34, 179, 167, 0.14) 0%, rgba(34, 179, 167, 0) 72%);
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
  background: radial-gradient(circle, rgba(34, 179, 167, 0.16) 0%, rgba(34, 179, 167, 0) 72%);
  pointer-events: none;
}

.hero-badge {
  margin: 0 0 8px;
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(34, 179, 167, 0.08);
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
  max-width: 620px;
}

.hero-side {
  position: relative;
  z-index: 1;
  min-width: 226px;
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

.hero-side::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.hero-side::after {
  content: '';
  position: absolute;
  right: -34px;
  bottom: -46px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 72%);
}

.hero-side span,
.hero-side small {
  font-size: 13px;
  opacity: 0.82;
}

.hero-side strong {
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.2;
}

.hero-side small {
  margin-top: 10px;
  line-height: 1.6;
}

.toolbar {
  margin-top: 24px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
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

.history-btn {
  min-width: 140px;
  height: 46px;
  border-radius: 999px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.stats-card {
  padding: 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.52);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: 0 16px 30px rgba(38, 70, 132, 0.08);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.stats-card span {
  display: block;
  color: #7d8ba6;
  font-size: 13px;
}

.stats-card strong {
  display: block;
  margin-top: 10px;
  color: #223164;
  font-size: 28px;
  line-height: 1.15;
}

.stats-card small {
  display: block;
  margin-top: 8px;
  color: #5f6c84;
  line-height: 1.6;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(260px, 360px) 1fr;
  gap: 20px;
  margin-top: 24px;
}

.form-panel,
.upload-panel {
  position: relative;
  padding: 22px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.section-title {
  margin: 0 0 14px;
  color: #33415c;
  font-size: 18px;
  font-weight: 700;
}

.speaker-help {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  color: #7282a4;
  font-size: 12px;
}

.speaker-auto-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  color: #33415c;
  font-size: 13px;
  font-weight: 600;
}

.meeting-upload :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 240px;
  border-radius: 16px;
  border: 1px solid rgba(148, 128, 238, 0.2);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88) 0%, rgba(243, 248, 255, 0.94) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), inset 0 0 0 1px rgba(148, 128, 238, 0.08);
  transition: all 0.35s ease;
}

.meeting-upload :deep(.el-upload-dragger:hover) {
  border-color: #7c5cff;
  background: linear-gradient(180deg, #f7fbff 0%, #eef5ff 100%);
}

.upload-icon {
  font-size: 34px;
  color: #7c5cff;
  margin-bottom: 10px;
}

.upload-title {
  color: #2f3b53;
  font-size: 18px;
  font-weight: 600;
}

.upload-tip {
  margin-top: 10px;
  color: #7d8ba6;
  font-size: 13px;
}

.file-meta {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(244, 248, 255, 0.88);
  color: #395180;
  font-size: 14px;
}

.file-meta--empty {
  color: #95a2bb;
}

.submit-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.submit-btn {
  min-width: 160px;
  height: 46px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  box-shadow: 0 18px 34px rgba(106, 79, 224, 0.22);
}

.result-panel {
  margin-top: 24px;
  padding: 22px 24px;
  border-radius: 18px;
  border: 1px solid transparent;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.result-panel--idle {
  background: rgba(255, 255, 255, 0.52);
  border-color: rgba(150, 134, 228, 0.14);
}

.result-panel--success {
  background: rgba(92, 193, 132, 0.12);
  border-color: rgba(92, 193, 132, 0.22);
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
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

.result-description {
  margin: 14px 0 0;
  color: #5f6c84;
  line-height: 1.7;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  color: #4d628e;
  font-size: 13px;
}

.processing-card {
  margin-top: 16px;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(255, 247, 229, 0.88) 0%, rgba(247, 250, 255, 0.92) 100%);
  border: 1px solid rgba(240, 171, 41, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.processing-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.processing-kicker {
  display: block;
  margin-bottom: 6px;
  color: #8b6a2f;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.processing-head strong {
  color: #29385c;
  font-size: 20px;
  line-height: 1.2;
}

.processing-head em {
  font-style: normal;
  color: #6a4fe0;
  font-size: 24px;
  font-weight: 700;
}

.processing-copy {
  margin: 10px 0 0;
  color: #5f6c84;
  line-height: 1.75;
}

.processing-bar {
  overflow: hidden;
  margin-top: 14px;
  height: 10px;
  border-radius: 999px;
  background: rgba(130, 152, 210, 0.16);
}

.processing-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6a4fe0 0%, #9b87ff 58%, #6cc9c5 100%);
  box-shadow: 0 10px 20px rgba(106, 79, 224, 0.22);
  transition: width 0.35s ease;
}

.processing-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.processing-step {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(150, 134, 228, 0.12);
  background: rgba(255, 255, 255, 0.68);
  color: #7b89a5;
  font-size: 12px;
  transition: all 0.25s ease;
}

.processing-step--done {
  border-color: rgba(92, 193, 132, 0.2);
  background: rgba(92, 193, 132, 0.12);
  color: #2f8051;
}

.processing-step--active {
  border-color: rgba(106, 79, 224, 0.24);
  background: rgba(106, 79, 224, 0.12);
  color: #335ad8;
}

.structured-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.structured-card {
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(248, 251, 255, 0.92) 0%, rgba(238, 244, 255, 0.96) 100%);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: 0 16px 26px rgba(38, 70, 132, 0.08);
}

.structured-head span {
  display: block;
  color: #2a3960;
  font-size: 16px;
  font-weight: 700;
}

.structured-head small {
  display: block;
  margin-top: 6px;
  color: #71809d;
  line-height: 1.65;
}

.structured-list {
  margin: 14px 0 0;
  padding-left: 18px;
  color: #2f3f63;
}

.structured-list li + li {
  margin-top: 10px;
}

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.analysis-card {
  padding: 16px;
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(248, 251, 255, 0.92) 0%, rgba(238, 244, 255, 0.96) 100%);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: 0 16px 26px rgba(38, 70, 132, 0.08);
}

.analysis-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.analysis-head span {
  display: block;
  color: #2a3960;
  font-size: 16px;
  font-weight: 700;
}

.analysis-head small {
  color: #71809d;
  line-height: 1.65;
}

.analysis-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(148, 128, 250, 0.15);
}

.analysis-item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.analysis-item-head strong {
  color: #243362;
}

.analysis-item p {
  margin: 0;
  color: #33415c;
  line-height: 1.75;
  white-space: normal;
}

.analysis-item small {
  display: block;
  margin-top: 8px;
  color: #7c8ab2;
  line-height: 1.6;
}

.chain-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.chain-status--complete {
  background: rgba(92, 193, 132, 0.12);
  color: #2f8051;
}

.chain-status--partial {
  background: rgba(255, 180, 84, 0.16);
  color: #a96518;
}

.chain-status--pending {
  background: rgba(150, 134, 228, 0.12);
  color: #496399;
}

.content-card {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(248, 251, 255, 0.86) 0%, rgba(238, 245, 255, 0.92) 100%);
  border: 1px solid rgba(150, 134, 228, 0.12);
}

.content-card span {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 6px;
}

.content-card p,
.todo-list {
  margin: 0;
  color: #243362;
  line-height: 1.8;
  white-space: pre-wrap;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.segment-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.segment-item {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(148, 128, 250, 0.15);
}

.segment-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  color: #243362;
}

.segment-item p {
  margin: 0;
  color: #33415c;
  line-height: 1.8;
}

.segment-extra {
  display: inline-block;
  margin-top: 8px;
  color: #7c8ab2;
  font-size: 12px;
}

.todo-list {
  padding-left: 18px;
}

.transcript-text {
  max-height: 240px;
  overflow-y: auto;
}

:global(html[data-auth-theme-mode='dark'] .meeting-card) {
  background: linear-gradient(145deg, rgba(27, 27, 31, 0.96) 0%, rgba(35, 35, 40, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.24);
  box-shadow: 0 24px 54px rgba(6, 12, 28, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .hero-section) {
  background: linear-gradient(135deg, rgba(37, 37, 42, 0.96) 0%, rgba(44, 44, 50, 0.92) 100%);
}

:global(html[data-auth-theme-mode='dark'] .hero-badge) {
  color: #8db2ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-section h2),
:global(html[data-auth-theme-mode='dark'] .section-title),
:global(html[data-auth-theme-mode='dark'] .upload-title),
:global(html[data-auth-theme-mode='dark'] .result-header h4),
:global(html[data-auth-theme-mode='dark'] .content-card p),
:global(html[data-auth-theme-mode='dark'] .todo-list) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-text),
:global(html[data-auth-theme-mode='dark'] .upload-tip),
:global(html[data-auth-theme-mode='dark'] .speaker-auto-option),
:global(html[data-auth-theme-mode='dark'] .speaker-help),
:global(html[data-auth-theme-mode='dark'] .result-description),
:global(html[data-auth-theme-mode='dark'] .processing-copy),
:global(html[data-auth-theme-mode='dark'] .result-label),
:global(html[data-auth-theme-mode='dark'] .result-meta),
:global(html[data-auth-theme-mode='dark'] .content-card span) {
  color: rgba(220, 230, 255, 0.84);
}

:global(html[data-auth-theme-mode='dark'] .processing-card) {
  background: linear-gradient(145deg, rgba(37, 45, 73, 0.92) 0%, rgba(25, 35, 59, 0.95) 100%);
  border-color: rgba(140, 110, 245, 0.2);
}

:global(html[data-auth-theme-mode='dark'] .processing-kicker) {
  color: rgba(255, 214, 128, 0.88);
}

:global(html[data-auth-theme-mode='dark'] .processing-head strong),
:global(html[data-auth-theme-mode='dark'] .structured-head span),
:global(html[data-auth-theme-mode='dark'] .structured-list) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .processing-step) {
  background: rgba(15, 25, 47, 0.92);
  color: rgba(210, 223, 255, 0.72);
  border-color: rgba(140, 110, 245, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .processing-step--done) {
  background: rgba(37, 82, 61, 0.42);
  color: #8de5b2;
}

:global(html[data-auth-theme-mode='dark'] .processing-step--active) {
  background: rgba(42, 71, 152, 0.42);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .structured-card) {
  background: linear-gradient(145deg, rgba(18, 31, 56, 0.96) 0%, rgba(21, 35, 63, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .analysis-card) {
  background: linear-gradient(145deg, rgba(18, 31, 56, 0.96) 0%, rgba(21, 35, 63, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .structured-head small) {
  color: rgba(210, 223, 255, 0.76);
}

:global(html[data-auth-theme-mode='dark'] .analysis-head span),
:global(html[data-auth-theme-mode='dark'] .analysis-item-head strong),
:global(html[data-auth-theme-mode='dark'] .analysis-item p) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .analysis-head small),
:global(html[data-auth-theme-mode='dark'] .analysis-item small) {
  color: rgba(210, 223, 255, 0.76);
}

:global(html[data-auth-theme-mode='dark'] .analysis-item) {
  background: rgba(16, 28, 52, 0.92);
  border-color: rgba(140, 110, 245, 0.14);
}

:global(html[data-auth-theme-mode='dark'] .chain-status--complete) {
  background: rgba(37, 82, 61, 0.42);
  color: #8de5b2;
}

:global(html[data-auth-theme-mode='dark'] .chain-status--partial) {
  background: rgba(117, 76, 19, 0.42);
  color: #ffd58f;
}

:global(html[data-auth-theme-mode='dark'] .chain-status--pending) {
  background: rgba(42, 71, 152, 0.42);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .form-panel),
:global(html[data-auth-theme-mode='dark'] .upload-panel) {
  background: linear-gradient(145deg, rgba(14, 23, 43, 0.92) 0%, rgba(25, 25, 29, 0.9) 100%);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .meeting-upload .el-upload-dragger) {
  background:
    radial-gradient(circle at 50% 18%, rgba(124, 92, 255, 0.18), transparent 24%),
    linear-gradient(145deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 46%, rgba(20, 33, 61, 0.96) 100%);
  border-color: rgba(148, 128, 250, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    inset 0 -1px 0 rgba(86, 119, 214, 0.08),
    0 18px 34px rgba(12, 12, 14, 0.26);
}

:global(html[data-auth-theme-mode='dark'] .meeting-upload .el-upload-dragger:hover) {
  background:
    radial-gradient(circle at 50% 18%, rgba(130, 100, 248, 0.22), transparent 24%),
    linear-gradient(145deg, rgba(12, 22, 42, 0.99) 0%, rgba(18, 30, 56, 0.98) 46%, rgba(26, 42, 74, 0.97) 100%);
  border-color: rgba(138, 110, 250, 0.72);
}

:global(html[data-auth-theme-mode='dark'] .file-meta) {
  background: rgba(17, 28, 53, 0.92);
  color: #d3e0ff;
}

:global(html[data-auth-theme-mode='dark'] .file-meta--empty) {
  color: rgba(208, 220, 255, 0.48);
}

:global(html[data-auth-theme-mode='dark'] .hint-chip) {
  background: rgba(31, 31, 35, 0.92);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .stats-card) {
  background: linear-gradient(145deg, rgba(16, 28, 52, 0.96) 0%, rgba(20, 34, 62, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.18);
  box-shadow: 0 18px 30px rgba(12, 12, 14, 0.22);
}

:global(html[data-auth-theme-mode='dark'] .stats-card span) {
  color: rgba(220, 230, 255, 0.72);
}

:global(html[data-auth-theme-mode='dark'] .stats-card strong) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .stats-card small) {
  color: rgba(220, 230, 255, 0.82);
}

:global(html[data-auth-theme-mode='dark'] .history-btn) {
  background: rgba(25, 25, 29, 0.92);
  border-color: rgba(138, 108, 250, 0.3);
  color: #dce8ff;
  box-shadow: 0 12px 24px rgba(13, 13, 16, 0.28);
}

:global(html[data-auth-theme-mode='dark'] .history-btn:hover) {
  background: rgba(31, 31, 35, 0.96);
  border-color: rgba(138, 108, 250, 0.46);
  color: #f4f7ff;
}

:global(html[data-auth-theme-mode='dark'] .result-panel--idle) {
  background: rgba(20, 20, 23, 0.84);
  border-color: rgba(140, 110, 245, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .result-panel--success) {
  background: rgba(18, 39, 63, 0.88);
  border-color: rgba(92, 193, 132, 0.2);
}

:global(html[data-auth-theme-mode='dark'] .content-card) {
  background: rgba(20, 20, 23, 0.92);
}

:global(html[data-auth-theme-mode='dark'] .segment-item) {
  background: rgba(16, 28, 52, 0.92);
  border-color: rgba(140, 110, 245, 0.14);
}

:global(html[data-auth-theme-mode='dark'] .segment-head),
:global(html[data-auth-theme-mode='dark'] .segment-item p) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .segment-extra) {
  color: rgba(208, 220, 255, 0.76);
}

@media (max-width: 960px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .hero-section,
  .toolbar,
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-side {
    width: 100%;
  }

  .structured-grid {
    grid-template-columns: 1fr;
  }

  .analysis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    width: 100%;
    flex-direction: column;
  }

  .history-btn,
  .submit-btn {
    width: 100%;
  }

  .submit-row {
    justify-content: stretch;
  }
}
</style>
