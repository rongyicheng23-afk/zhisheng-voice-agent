<template>
  <flow-background-panel>
    <div class="tts-page container-md" style="margin-top: 10px;">
      <div class="tts-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div class="hero-copy">
            <p class="hero-badge">智能合成</p>
            <h2>文字转语音</h2>
            <p class="hero-text">输入文本并上传参考音频，快速生成可试听、可下载、可追溯的个性化语音结果。</p>
          </div>
          <div class="hero-status">
            <span class="hero-status-label">当前状态</span>
            <strong>{{ statusHeadline }}</strong>
            <small>{{ statusSubline }}</small>
            <el-tag :type="statusTagType" effect="plain" round class="hero-status-tag">
              {{ statusTagText }}
            </el-tag>
          </div>
        </div>

        <quick-start-panel
          storage-key="tts-home"
          title="第一次用文字转语音，可以先按这三步操作"
          subtitle="先准备文本和参考音频，再生成可试听、可下载、可回溯的语音结果。"
          :steps="quickStartSteps"
          :tips="quickStartTips"
        />

        <div class="composer-panel">
          <div class="panel-header">
            <h4 class="section-title">
              <el-icon class="el-icon--left"><EditPen /></el-icon>
              文本内容
            </h4>
            <div class="panel-meta">
              <span>字数 {{ textLength }}</span>
              <span v-if="audioFileName">参考音频：{{ audioFileName }}</span>
            </div>
          </div>
          <el-input
            v-model="text"
            class="text-input"
            :autosize="{ minRows: 8, maxRows: 15 }"
            type="textarea"
            placeholder="请输入要转换的文本内容..."
          />
        </div>

        <div class="workbench-grid">
          <section class="upload-panel">
            <h4 class="section-title">
              <el-icon class="el-icon--left"><Headset /></el-icon>
              参考音频
            </h4>
            <el-upload
              ref="audioUploadRef"
              class="voice-upload"
              drag
              :auto-upload="false"
              :show-file-list="false"
              accept=".wav,.mp3"
              :limit="1"
              @click.capture="handleUploadTrigger"
              :on-change="handleAudioChange"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-title">上传参考说话人音频</div>
              <div class="upload-tip">支持 WAV、MP3，建议人声清晰，时长 3 秒以上，文件不超过 50MB</div>
            </el-upload>
            <div class="file-meta" :class="{ 'file-meta--empty': !audioFileName }">
              {{ audioFileName || '尚未选择参考音频' }}
              <span v-if="audioFileSizeText !== '--'"> · {{ audioFileSizeText }}</span>
            </div>
          </section>

          <section class="settings-panel">
            <h4 class="section-title">
              <el-icon class="el-icon--left"><Setting /></el-icon>
              合成参数
            </h4>
            <div class="settings-grid">
              <div class="setting-card">
                <label class="setting-label">主要情绪</label>
                <el-select v-model="emotion" placeholder="请选择主要情绪" class="setting-control">
                  <el-option label="中性" value="neutral"></el-option>
                  <el-option label="开心" value="happy"></el-option>
                  <el-option label="悲伤" value="sad"></el-option>
                  <el-option label="愤怒" value="angry"></el-option>
                </el-select>
              </div>

              <div class="setting-card">
                <label class="setting-label">语言选择</label>
                <el-select v-model="language" placeholder="请选择语言" class="setting-control">
                  <el-option label="中文" value="zh-cn"></el-option>
                  <el-option label="英语" value="en"></el-option>
                  <el-option label="西班牙语" value="es"></el-option>
                  <el-option label="法语" value="fr"></el-option>
                  <el-option label="德语" value="de"></el-option>
                </el-select>
              </div>

              <div class="setting-card">
                <label class="setting-label">输出格式</label>
                <el-radio-group v-model="format" class="format-group">
                  <el-radio label="wav">WAV</el-radio>
                  <el-radio label="mp3">MP3</el-radio>
                </el-radio-group>
                <p class="setting-hint">当前 Python 服务通常返回 WAV，实际生成格式以结果音频为准。</p>
              </div>

              <div class="setting-card">
                <label class="setting-label">合成进度</label>
                <div class="status-box">
                  <el-tag :type="statusTagType" round>{{ statusTagText }}</el-tag>
                  <p>{{ statusPanelHint }}</p>
                </div>
              </div>
            </div>
          </section>
        </div>

        <div class="toolbar">
          <div class="hint-list">
            <span class="hint-chip">Java 转发 Python</span>
            <span class="hint-chip">MinIO 双音频存储</span>
            <span class="hint-chip">数据库历史可追溯</span>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" plain class="history-btn" @click="openHistoryDrawer">
              <el-icon class="el-icon--left"><Clock /></el-icon>
              历史记录
            </el-button>
            <el-button
              type="primary"
              class="convert-btn"
              :loading="status === 'processing'"
              :disabled="!canConvert"
              @click="convertToVoice"
            >
              <el-icon class="el-icon--left"><MagicStick /></el-icon>
              {{ status === 'processing' ? '转换中...' : '立即转换' }}
            </el-button>
          </div>
        </div>

        <div class="result-panel" :class="resultClass">
          <div class="result-header">
            <div>
              <span class="result-label">生成结果</span>
              <h4>{{ resultTitle }}</h4>
            </div>
            <el-tag :type="statusTagType">{{ statusTagText }}</el-tag>
          </div>
          <p class="result-description">{{ resultDescription }}</p>
          <div v-if="lastResultFilename || lastHistoryId || audioFileName" class="result-meta">
            <span v-if="audioFileName">参考音频：{{ audioFileName }}</span>
            <span v-if="lastResultFilename">结果文件：{{ lastResultFilename }}</span>
            <span v-if="lastHistoryId">历史编号：#{{ lastHistoryId }}</span>
            <span v-if="resultAudioSizeText !== '--'">结果大小：{{ resultAudioSizeText }}</span>
          </div>
          <div v-if="resultAudio" class="audio-player">
            <audio controls :src="resultAudio" class="audio-element">
              您的浏览器不支持音频播放
            </audio>
            <div class="download-actions">
              <el-button type="success" @click="downloadAudio">
                <el-icon class="el-icon--left"><Download /></el-icon>
                下载音频
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <tts-history-drawer v-model="historyDrawerVisible" />
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import {
  Clock,
  Setting,
  MagicStick,
  UploadFilled,
  Download,
  Headset,
  EditPen
} from '@element-plus/icons-vue'
import {
  fetchTtsResultAudioBlob,
  synthesizeTextToVoice
} from '@/api/tts'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import TtsHistoryDrawer from '@/components/TtsHistoryDrawer.vue'
import QuickStartPanel from '@/components/QuickStartPanel.vue'
import store from '@/store'

export default defineComponent({
  name: 'TextToVoice',
  components: {
    FlowBackgroundPanel,
    TtsHistoryDrawer,
    QuickStartPanel,
    Clock,
    Setting,
    MagicStick,
    UploadFilled,
    Download,
    Headset,
    EditPen
  },
  setup () {
    const router = useRouter()
    const text = ref<string>('')
    const emotion = ref<string>('neutral')
    const language = ref<string>('zh-cn')
    const format = ref<string>('wav')
    const status = ref<'idle' | 'processing' | 'success' | 'error'>('idle')
    const audioFile = ref<File | null>(null)
    const audioFileName = ref<string>('')
    const audioFileSize = ref<number>(0)
    const audioUploadRef = ref<UploadInstance>()
    const resultAudio = ref<string>('')
    const historyDrawerVisible = ref(false)
    const lastHistoryId = ref<number | null>(null)
    const lastResultFilename = ref<string>('')
    const lastResultSize = ref<number | null>(null)

    const canConvert = computed(() => Boolean(text.value.trim() && audioFile.value && emotion.value && language.value))
    const isLoggedIn = computed(() => Boolean(store.state.token && store.state.user?.isLogin && Number(store.state.user?._id)))
    const quickStartSteps = [
      {
        title: '输入待合成文本',
        description: '先填写要转换的内容，建议语句完整，这样生成出来的语音更自然。'
      },
      {
        title: '上传参考音频并调参数',
        description: '选择一段清晰的人声样本，再设置情绪、语言和输出格式。'
      },
      {
        title: '试听并保存结果',
        description: '生成后可以直接试听、下载，历史记录里也会保存参考音频和结果音频。'
      }
    ]
    const quickStartTips = ['参考音频建议大于 3 秒', '先登录后才能保存历史', '生成结果可从历史中再次下载']

    const textLength = computed(() => text.value.trim().length)

    const formatFileSize = (size: number) => {
      if (!size || size <= 0) {
        return '--'
      }
      if (size >= 1024 * 1024) {
        return `${(size / 1024 / 1024).toFixed(1)} MB`
      }
      if (size >= 1024) {
        return `${(size / 1024).toFixed(1)} KB`
      }
      return `${size} B`
    }

    const audioFileSizeText = computed(() => formatFileSize(audioFileSize.value))
    const resultAudioSizeText = computed(() => formatFileSize(lastResultSize.value || 0))

    const statusTagType = computed(() => {
      if (status.value === 'processing') return 'warning'
      if (status.value === 'success') return 'success'
      if (status.value === 'error') return 'danger'
      return 'info'
    })

    const statusTagText = computed(() => {
      if (status.value === 'processing') return '处理中'
      if (status.value === 'success') return '已完成'
      if (status.value === 'error') return '失败'
      return '待开始'
    })

    const statusHeadline = computed(() => {
      if (status.value === 'processing') return '正在生成语音'
      if (status.value === 'success') return '语音合成完成'
      if (status.value === 'error') return '本次生成失败'
      return '等待开始合成'
    })

    const statusSubline = computed(() => {
      if (status.value === 'processing') {
        return 'Java 正在调用 Python TTS 服务，请稍候。'
      }
      if (status.value === 'success') {
        return lastHistoryId.value ? `历史记录已保存，编号 #${lastHistoryId.value}` : '生成成功，可立即试听或下载。'
      }
      if (status.value === 'error') {
        return '请检查文本内容和参考音频后重新尝试。'
      }
      return '上传参考音频并填写文本后，就可以开始一次新的语音合成。'
    })

    const statusPanelHint = computed(() => {
      if (status.value === 'processing') return '后端已接管任务，生成结果会落库并写入 MinIO。'
      if (status.value === 'success') return '当前结果已经可以试听，历史中也可再次下载。'
      if (status.value === 'error') return '本次任务未成功，可调整参数或更换参考音频。'
      return '支持按当前登录用户保存文本、参考音频和生成音频。'
    })

    const resultClass = computed(() => {
      if (status.value === 'success') return 'result-panel--success'
      if (status.value === 'error') return 'result-panel--danger'
      if (status.value === 'processing') return 'result-panel--warning'
      return 'result-panel--idle'
    })

    const resultTitle = computed(() => {
      if (status.value === 'success') return '生成音频已准备就绪'
      if (status.value === 'error') return '本次生成未完成'
      if (status.value === 'processing') return '正在等待 Python 服务返回音频'
      return '等待输入文本与参考音频'
    })

    const resultDescription = computed(() => {
      if (status.value === 'success') {
        return '当前结果已经同步保存到数据库和 MinIO，后续可以在历史记录中继续查看。'
      }
      if (status.value === 'error') {
        return '这次转换没有成功，请检查文本、参考音频或 Python TTS 服务状态后重试。'
      }
      if (status.value === 'processing') {
        return '系统正在合成语音，请稍候，不需要重复点击。'
      }
      return '登录后输入文本、上传参考音频，即可得到真实的语音合成结果，并保留完整历史记录。'
    })

    const resetResultAudio = () => {
      if (resultAudio.value) {
        URL.revokeObjectURL(resultAudio.value)
        resultAudio.value = ''
      }
    }

    const resetResultMeta = () => {
      resetResultAudio()
      lastHistoryId.value = null
      lastResultFilename.value = ''
      lastResultSize.value = null
      if (status.value !== 'processing') {
        status.value = 'idle'
      }
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      ElMessage.error('登录状态已失效，请重新登录后再使用文字转语音')
      router.push('/login')
    }

    const ensureTtsAccess = (showMessage = true) => {
      if (isLoggedIn.value) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再使用文字转语音和历史记录')
      }
      router.push('/login')
      return false
    }

    const handleUploadTrigger = (event?: Event) => {
      if (ensureTtsAccess()) {
        return
      }
      event?.preventDefault()
      event?.stopPropagation()
    }

    const openHistoryDrawer = () => {
      if (!ensureTtsAccess()) {
        return
      }
      historyDrawerVisible.value = true
    }

    const handleAudioChange = (file: UploadFile) => {
      if (!ensureTtsAccess(false)) {
        audioUploadRef.value?.clearFiles()
        audioFile.value = null
        audioFileName.value = ''
        audioFileSize.value = 0
        return
      }

      const raw = file.raw
      if (!raw) {
        return
      }
      if (raw.size > 50 * 1024 * 1024) {
        ElMessage.error('文件大小不能超过50MB')
        audioFile.value = null
        audioFileName.value = ''
        audioFileSize.value = 0
        audioUploadRef.value?.clearFiles()
        return
      }

      audioFile.value = raw
      audioFileName.value = raw.name
      audioFileSize.value = raw.size
      audioUploadRef.value?.clearFiles()
      resetResultMeta()
      ElMessage.success('参考音频已选择')
    }

    const convertToVoice = async () => {
      if (!canConvert.value) {
        ElMessage.warning('请填写完整信息并上传音频文件')
        return
      }
      if (!ensureTtsAccess()) {
        return
      }

      try {
        status.value = 'processing'
        const formData = new FormData()
        formData.append('audio', audioFile.value!)
        formData.append('text', text.value.trim())
        formData.append('emotion', emotion.value)
        formData.append('language', language.value)
        formData.append('format', format.value)

        const response = await synthesizeTextToVoice(formData)
        if (response.code !== 200 || !response.data?.historyId) {
          throw new Error(response.msg || '转换失败')
        }

        const audioBlob = await fetchTtsResultAudioBlob(response.data.historyId)
        resetResultAudio()
        resultAudio.value = URL.createObjectURL(audioBlob)
        lastHistoryId.value = response.data.historyId
        lastResultFilename.value = response.data.resultFilename || `tts_output_${Date.now()}.wav`
        lastResultSize.value = audioBlob.size
        status.value = 'success'
        ElMessage.success('语音转换成功！')
      } catch (error: any) {
        status.value = 'error'
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(`转换失败: ${error?.message || '未知错误'}`)
      }
    }

    const downloadAudio = async () => {
      if (!lastHistoryId.value) {
        return
      }
      if (!ensureTtsAccess(false)) {
        return
      }
      try {
        const blob = await fetchTtsResultAudioBlob(lastHistoryId.value)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = lastResultFilename.value || `tts_output_${Date.now()}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
        ElMessage.success('下载开始')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载音频失败')
      }
    }

    onBeforeUnmount(() => {
      resetResultAudio()
    })

    return {
      text,
      emotion,
      language,
      format,
      status,
      audioFileName,
      audioFileSizeText,
      audioUploadRef,
      resultAudio,
      resultClass,
      resultTitle,
      resultDescription,
      historyDrawerVisible,
      lastHistoryId,
      lastResultFilename,
      resultAudioSizeText,
      canConvert,
      textLength,
      quickStartSteps,
      quickStartTips,
      statusTagType,
      statusTagText,
      statusHeadline,
      statusSubline,
      statusPanelHint,
      handleUploadTrigger,
      handleAudioChange,
      openHistoryDrawer,
      convertToVoice,
      downloadAudio
    }
  }
})
</script>

<style scoped>
.tts-page {
  padding-bottom: 24px;
}

.tts-card {
  position: relative;
  overflow: hidden;
  max-width: 1040px;
  margin: 0 auto;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74) 0%, rgba(246, 249, 255, 0.88) 100%);
  padding: 30px;
  box-shadow: 0 28px 68px rgba(19, 39, 78, 0.14);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.tts-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

.tts-card::after {
  content: '';
  position: absolute;
  left: -40px;
  top: -36px;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.14) 0%, rgba(124, 92, 255, 0) 72%);
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
  border-radius: 24px;
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
  min-width: 248px;
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

.hero-status-tag {
  align-self: flex-start;
  margin-top: 14px;
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.24);
  color: #fff;
}

.composer-panel,
.upload-panel,
.settings-panel {
  position: relative;
  padding: 22px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.composer-panel {
  margin-bottom: 20px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #6f7d97;
  font-size: 13px;
}

.section-title {
  margin: 0;
  color: #33415c;
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
}

.text-input :deep(.el-textarea__inner) {
  min-height: 220px;
  border-radius: 16px;
  border: 1px solid rgba(150, 134, 228, 0.18);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(244, 248, 255, 0.96) 100%);
  box-shadow: inset 0 1px 2px rgba(86, 105, 146, 0.06);
  padding: 18px 20px;
  font-size: 16px;
  line-height: 1.7;
  color: #2d3a54;
}

.text-input :deep(.el-textarea__inner:focus) {
  border-color: #7c5cff;
  box-shadow: 0 0 0 3px rgba(124, 92, 255, 0.14);
}

.workbench-grid {
  display: grid;
  grid-template-columns: 1.02fr 1fr;
  gap: 20px;
}

.voice-upload :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 220px;
  border-radius: 16px;
  border: 1px solid rgba(148, 128, 238, 0.2);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88) 0%, rgba(243, 248, 255, 0.94) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), inset 0 0 0 1px rgba(148, 128, 238, 0.08);
  transition: all 0.35s ease;
}

.voice-upload :deep(.el-upload-dragger:hover) {
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
  line-height: 1.6;
}

.file-meta {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f3f7ff;
  color: #395180;
  font-size: 14px;
}

.file-meta--empty {
  color: #95a2bb;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.setting-card {
  min-height: 128px;
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82) 0%, rgba(242, 247, 255, 0.94) 100%);
  border: 1px solid rgba(150, 134, 228, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.setting-label {
  display: block;
  margin-bottom: 12px;
  color: #6d7b96;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.setting-control {
  width: 100%;
}

.setting-control :deep(.el-input__wrapper) {
  border-radius: 12px;
  min-height: 42px;
}

.format-group {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.setting-hint {
  margin: 12px 0 0;
  color: #7f8fb4;
  font-size: 12px;
  line-height: 1.6;
}

.status-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-box p {
  margin: 0;
  color: #66758f;
  font-size: 13px;
  line-height: 1.7;
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

.history-btn,
.convert-btn {
  min-width: 140px;
  height: 46px;
  border-radius: 999px;
}

.history-btn {
  border-color: rgba(134, 116, 214, 0.22);
  background: rgba(255, 255, 255, 0.56);
  box-shadow: 0 14px 26px rgba(18, 35, 73, 0.08);
}

.convert-btn {
  border: none;
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

.result-panel--warning {
  background: rgba(255, 179, 71, 0.14);
  border-color: rgba(255, 179, 71, 0.22);
}

.result-panel--danger {
  background: rgba(255, 107, 107, 0.12);
  border-color: rgba(255, 107, 107, 0.2);
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

.audio-player {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.audio-element {
  width: 100%;
  max-width: 440px;
}

.download-actions {
  display: flex;
}

:global(html[data-auth-theme-mode='dark'] .tts-card) {
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
:global(html[data-auth-theme-mode='dark'] .result-header h4) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-text),
:global(html[data-auth-theme-mode='dark'] .panel-meta),
:global(html[data-auth-theme-mode='dark'] .upload-tip),
:global(html[data-auth-theme-mode='dark'] .setting-hint),
:global(html[data-auth-theme-mode='dark'] .status-box p),
:global(html[data-auth-theme-mode='dark'] .result-description),
:global(html[data-auth-theme-mode='dark'] .result-label),
:global(html[data-auth-theme-mode='dark'] .result-meta) {
  color: rgba(220, 230, 255, 0.84);
}

:global(html[data-auth-theme-mode='dark'] .composer-panel),
:global(html[data-auth-theme-mode='dark'] .upload-panel),
:global(html[data-auth-theme-mode='dark'] .settings-panel) {
  background: linear-gradient(145deg, rgba(14, 23, 43, 0.92) 0%, rgba(25, 25, 29, 0.9) 100%);
  border-color: rgba(140, 110, 245, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

:global(html[data-auth-theme-mode='dark'] .text-input) :deep(.el-textarea__inner) {
  background:
    radial-gradient(circle at 50% 16%, rgba(124, 92, 255, 0.08), transparent 20%),
    linear-gradient(145deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 100%);
  border-color: rgba(148, 128, 250, 0.28);
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .voice-upload .el-upload-dragger) {
  background:
    radial-gradient(circle at 50% 18%, rgba(124, 92, 255, 0.18), transparent 24%),
    linear-gradient(145deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 46%, rgba(20, 33, 61, 0.96) 100%);
  border-color: rgba(148, 128, 250, 0.28);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    inset 0 -1px 0 rgba(86, 119, 214, 0.08),
    0 18px 34px rgba(12, 12, 14, 0.26);
}

:global(html[data-auth-theme-mode='dark'] .voice-upload .el-upload-dragger:hover) {
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

:global(html[data-auth-theme-mode='dark'] .setting-card) {
  background: rgba(17, 28, 53, 0.92);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .setting-control .el-input__wrapper) {
  background: rgba(18, 18, 21, 0.94);
  box-shadow: 0 0 0 1px rgba(138, 112, 236, 0.18) inset !important;
}

:global(html[data-auth-theme-mode='dark'] .setting-control .el-input__inner) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .format-group .el-radio) {
  color: #dce8ff;
}

:global(html[data-auth-theme-mode='dark'] .setting-label) {
  color: rgba(220, 230, 255, 0.8);
}

:global(html[data-auth-theme-mode='dark'] .hint-chip) {
  background: rgba(31, 31, 35, 0.92);
  color: #a9c2ff;
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

@media (max-width: 960px) {
  .hero-section,
  .toolbar,
  .result-header,
  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-status {
    width: 100%;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .tts-card {
    padding: 18px;
  }

  .settings-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .history-btn,
  .convert-btn {
    width: 100%;
  }
}
</style>
