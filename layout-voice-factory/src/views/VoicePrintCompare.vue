<template>
  <flow-background-panel>
    <div class="voiceprint-page container-md" style="margin-top: 10px;">
      <div class="voiceprint-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div>
            <p class="hero-badge">声纹识别</p>
            <h2>声纹对比</h2>
            <p class="hero-text">用于验证两段音频是否来自同一说话人，结果会同步写入历史记录，方便复核与留档。</p>
          </div>
          <div class="hero-score">
            <span>相似度</span>
            <strong>{{ similarityText }}</strong>
            <small v-if="thresholdText !== '--'">阈值 {{ thresholdText }}</small>
          </div>
        </div>

        <quick-start-panel
          storage-key="voiceprint-home"
          title="第一次用声纹对比，三步就能看懂结果"
          subtitle="上传两段音频后系统会自动计算相似度，并把原始音频和比对结果一起保存到历史。"
          :steps="quickStartSteps"
          :tips="quickStartTips"
        />

        <div class="compare-grid">
          <section class="upload-panel">
            <h4 class="section-title">音频 A</h4>
            <el-upload
              ref="leftUploadRef"
              class="voice-upload"
              drag
              :auto-upload="false"
              :show-file-list="false"
              accept=".wav,.mp3,.ogg,.flac,.m4a"
              :limit="1"
              @click.capture="handleUploadTrigger"
              :on-change="file => handleFileChange(file, 'left')"
            >
              <el-icon class="upload-icon"><Microphone /></el-icon>
              <div class="upload-title">上传第一段参考音频</div>
              <div class="upload-tip">支持 WAV、MP3、OGG、FLAC、M4A，建议人声清晰、时长大于 3 秒</div>
            </el-upload>
            <div class="file-meta" :class="{ 'file-meta--empty': !leftFileName }">
              {{ leftFileName || '尚未选择音频文件' }}
            </div>
          </section>

          <section class="upload-panel">
            <h4 class="section-title">音频 B</h4>
            <el-upload
              ref="rightUploadRef"
              class="voice-upload"
              drag
              :auto-upload="false"
              :show-file-list="false"
              accept=".wav,.mp3,.ogg,.flac,.m4a"
              :limit="1"
              @click.capture="handleUploadTrigger"
              :on-change="file => handleFileChange(file, 'right')"
            >
              <el-icon class="upload-icon"><Headset /></el-icon>
              <div class="upload-title">上传第二段待比对音频</div>
              <div class="upload-tip">建议采样环境相近，避免背景噪音过大</div>
            </el-upload>
            <div class="file-meta" :class="{ 'file-meta--empty': !rightFileName }">
              {{ rightFileName || '尚未选择音频文件' }}
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
            <el-button type="primary" plain class="speaker-btn" @click="openSpeakerDrawer">
              <el-icon class="el-icon--left"><UserFilled /></el-icon>
              发言人档案
            </el-button>
            <el-button type="primary" plain class="history-btn" @click="openHistoryDrawer">
              <el-icon class="el-icon--left"><Clock /></el-icon>
              历史记录
            </el-button>
            <el-button
              type="primary"
              class="compare-btn"
              :disabled="!canCompare || compareLoading"
              :loading="compareLoading"
              @click="handleCompare"
            >
              <el-icon class="el-icon--left"><DataAnalysis /></el-icon>
              {{ compareLoading ? '对比中...' : '开始对比' }}
            </el-button>
          </div>
        </div>

        <div class="result-panel" :class="resultClass">
          <div class="result-header">
            <div>
              <span class="result-label">比对结果</span>
              <h4>{{ resultTitle }}</h4>
            </div>
            <el-tag :type="resultTagType">{{ resultTagText }}</el-tag>
          </div>
          <p class="result-description">{{ resultDescription }}</p>
          <div v-if="latestHistoryId" class="result-meta">
            <span>历史编号：#{{ latestHistoryId }}</span>
            <span v-if="leftFileName">音频 A：{{ leftFileName }}</span>
            <span v-if="rightFileName">音频 B：{{ rightFileName }}</span>
          </div>
        </div>
      </div>
    </div>

    <speaker-profile-drawer v-model="speakerDrawerVisible" />
    <voiceprint-history-drawer v-model="historyDrawerVisible" />
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, DataAnalysis, Headset, Microphone, UserFilled } from '@element-plus/icons-vue'
import type { UploadFile, UploadInstance } from 'element-plus'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import VoiceprintHistoryDrawer from '@/components/VoiceprintHistoryDrawer.vue'
import SpeakerProfileDrawer from '@/components/SpeakerProfileDrawer.vue'
import QuickStartPanel from '@/components/QuickStartPanel.vue'
import { compareVoiceprint } from '@/api/voiceprint'
import store from '@/store'

export default defineComponent({
  name: 'VoicePrintCompare',
  components: {
    FlowBackgroundPanel,
    VoiceprintHistoryDrawer,
    SpeakerProfileDrawer,
    QuickStartPanel,
    Microphone,
    Headset,
    DataAnalysis,
    Clock,
    UserFilled
  },
  setup () {
    const router = useRouter()
    const leftFile = ref<File | null>(null)
    const rightFile = ref<File | null>(null)
    const leftUploadRef = ref<UploadInstance>()
    const rightUploadRef = ref<UploadInstance>()
    const leftFileName = ref('')
    const rightFileName = ref('')
    const score = ref<number | null>(null)
    const threshold = ref<number | null>(null)
    const samePerson = ref<boolean | null>(null)
    const resultMessage = ref('')
    const compareLoading = ref(false)
    const historyDrawerVisible = ref(false)
    const speakerDrawerVisible = ref(false)
    const latestHistoryId = ref<number | null>(null)

    const quickStartSteps = [
      {
        title: '上传音频 A 与音频 B',
        description: '准备两段待比较的音频，建议环境相近、人声清晰，这样相似度结果会更稳定。'
      },
      {
        title: '点击开始对比',
        description: '后端会统一调用声纹服务计算相似度分数，并给出是否为同一人的判断。'
      },
      {
        title: '查看结果与历史',
        description: '你可以直接看相似度、阈值和判定结果，也能在历史记录里继续回放和下载原始音频。'
      }
    ]

    const quickStartTips = [
      '首次使用建议先准备两段短音频',
      '分数接近阈值时可结合历史复核',
      '支持在发言人档案中沉淀样本'
    ]

    const canCompare = computed(() => Boolean(leftFile.value && rightFile.value))
    const isLoggedIn = computed(() => Boolean(store.state.token && store.state.user?.isLogin && Number(store.state.user?._id)))

    const similarityText = computed(() => {
      if (score.value === null) return '--'
      return `${(score.value * 100).toFixed(2)}%`
    })

    const thresholdText = computed(() => {
      if (threshold.value === null) return '--'
      return `${(threshold.value * 100).toFixed(2)}%`
    })

    const resultTagType = computed(() => {
      if (samePerson.value === null) return 'info'
      return samePerson.value ? 'success' : score.value !== null && threshold.value !== null && score.value >= threshold.value * 0.8 ? 'warning' : 'danger'
    })

    const resultTagText = computed(() => {
      if (samePerson.value === null) return '待开始'
      return samePerson.value ? '同一人' : score.value !== null && threshold.value !== null && score.value >= threshold.value * 0.8 ? '需要复核' : '差异明显'
    })

    const resultTitle = computed(() => {
      if (samePerson.value === null) return '等待上传两段音频'
      return samePerson.value ? '两段音频声纹高度接近' : '两段音频声纹差异较大'
    })

    const resultDescription = computed(() => {
      if (samePerson.value === null) {
        return '登录后上传两段音频，即可得到真实的声纹比对结果，并保留历史记录。'
      }
      return resultMessage.value || '声纹对比已完成。'
    })

    const resultClass = computed(() => {
      if (samePerson.value === null) return 'result-panel--idle'
      if (samePerson.value) return 'result-panel--success'
      return score.value !== null && threshold.value !== null && score.value >= threshold.value * 0.8
        ? 'result-panel--warning'
        : 'result-panel--danger'
    })

    const ensureCompareAccess = (showMessage = true) => {
      if (isLoggedIn.value) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再使用声纹对比和历史记录')
      }
      router.push('/login')
      return false
    }

    const handleUploadTrigger = (event?: Event) => {
      if (ensureCompareAccess()) {
        return
      }
      event?.preventDefault()
      event?.stopPropagation()
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      ElMessage.error('登录状态已失效，请重新登录后再使用声纹对比')
      router.push('/login')
    }

    const handleFileChange = (file: UploadFile, side: 'left' | 'right') => {
      if (!ensureCompareAccess(false)) {
        leftUploadRef.value?.clearFiles()
        rightUploadRef.value?.clearFiles()
        return
      }

      const raw = file.raw
      if (!raw) return
      if (raw.size > 50 * 1024 * 1024) {
        ElMessage.error('文件大小不能超过 50MB')
        return
      }
      if (side === 'left') {
        leftFile.value = raw
        leftFileName.value = raw.name
        leftUploadRef.value?.clearFiles()
      } else {
        rightFile.value = raw
        rightFileName.value = raw.name
        rightUploadRef.value?.clearFiles()
      }
      ElMessage.success('音频已选择')
    }

    const openHistoryDrawer = () => {
      if (!ensureCompareAccess()) {
        return
      }
      historyDrawerVisible.value = true
    }

    const openSpeakerDrawer = () => {
      if (!ensureCompareAccess()) {
        return
      }
      speakerDrawerVisible.value = true
    }

    const handleCompare = async () => {
      if (!canCompare.value) {
        ElMessage.warning('请先上传两段音频')
        return
      }
      if (!ensureCompareAccess()) {
        return
      }

      try {
        compareLoading.value = true
        const formData = new FormData()
        formData.append('file1', leftFile.value!)
        formData.append('file2', rightFile.value!)

        const res = await compareVoiceprint(formData)
        if (res.code !== 200 || !res.data?.historyId) {
          throw new Error(res.msg || '声纹对比失败')
        }

        latestHistoryId.value = res.data.historyId
        score.value = typeof res.data.score === 'number' ? res.data.score : null
        threshold.value = typeof res.data.threshold === 'number' ? res.data.threshold : null
        samePerson.value = typeof res.data.samePerson === 'boolean' ? res.data.samePerson : null
        resultMessage.value = res.data.message || '声纹对比完成'
        ElMessage.success('声纹对比完成')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '声纹对比失败')
      } finally {
        compareLoading.value = false
      }
    }

    return {
      leftFileName,
      rightFileName,
      leftUploadRef,
      rightUploadRef,
      canCompare,
      similarityText,
      thresholdText,
      resultTagType,
      resultTagText,
      resultTitle,
      resultDescription,
      resultClass,
      compareLoading,
      historyDrawerVisible,
      speakerDrawerVisible,
      latestHistoryId,
      quickStartSteps,
      quickStartTips,
      handleUploadTrigger,
      handleFileChange,
      openSpeakerDrawer,
      openHistoryDrawer,
      handleCompare
    }
  }
})
</script>

<style scoped>
.voiceprint-page {
  padding-bottom: 24px;
}

.voiceprint-card {
  position: relative;
  overflow: hidden;
  max-width: 980px;
  margin: 0 auto;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74) 0%, rgba(246, 249, 255, 0.88) 100%);
  padding: 30px;
  box-shadow: 0 28px 68px rgba(19, 39, 78, 0.14);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.voiceprint-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

.voiceprint-card::after {
  content: '';
  position: absolute;
  right: -48px;
  top: -42px;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(143, 88, 246, 0.14) 0%, rgba(143, 88, 246, 0) 72%);
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
  background: radial-gradient(circle, rgba(143, 88, 246, 0.18) 0%, rgba(143, 88, 246, 0) 72%);
  pointer-events: none;
}

.hero-badge {
  margin: 0 0 8px;
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(143, 88, 246, 0.08);
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
  color: #5f6c84;
  line-height: 1.7;
  max-width: 620px;
}

.hero-score {
  position: relative;
  z-index: 1;
  min-width: 184px;
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

.hero-score::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 18px;
  bottom: 18px;
  width: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
}

.hero-score::after {
  content: '';
  position: absolute;
  right: -34px;
  bottom: -46px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, rgba(255, 255, 255, 0) 72%);
}

.hero-score span,
.hero-score small {
  font-size: 13px;
  opacity: 0.82;
}

.hero-score strong {
  margin-top: 10px;
  font-size: 34px;
  line-height: 1;
}

.hero-score small {
  margin-top: 10px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

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

.speaker-btn,
.history-btn,
.compare-btn {
  min-width: 140px;
  height: 46px;
  border-radius: 999px;
}

.speaker-btn,
.history-btn {
  border-color: rgba(134, 116, 214, 0.22);
  background: rgba(255, 255, 255, 0.56);
  box-shadow: 0 14px 26px rgba(18, 35, 73, 0.08);
}

.compare-btn {
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

:global(html[data-auth-theme-mode='dark'] .voiceprint-card) {
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
:global(html[data-auth-theme-mode='dark'] .upload-tip),
:global(html[data-auth-theme-mode='dark'] .result-description),
:global(html[data-auth-theme-mode='dark'] .result-label),
:global(html[data-auth-theme-mode='dark'] .result-meta) {
  color: rgba(220, 230, 255, 0.84);
}

:global(html[data-auth-theme-mode='dark'] .upload-panel) {
  background: linear-gradient(145deg, rgba(14, 23, 43, 0.92) 0%, rgba(25, 25, 29, 0.9) 100%);
  border-color: rgba(140, 110, 245, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
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

:global(html[data-auth-theme-mode='dark'] .hint-chip) {
  background: rgba(31, 31, 35, 0.92);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .speaker-btn),
:global(html[data-auth-theme-mode='dark'] .history-btn) {
  background: rgba(25, 25, 29, 0.92);
  border-color: rgba(138, 108, 250, 0.3);
  color: #dce8ff;
  box-shadow: 0 12px 24px rgba(13, 13, 16, 0.28);
}

:global(html[data-auth-theme-mode='dark'] .speaker-btn:hover),
:global(html[data-auth-theme-mode='dark'] .history-btn:hover) {
  background: rgba(31, 31, 35, 0.96);
  border-color: rgba(138, 108, 250, 0.46);
  color: #f4f7ff;
}

:global(html[data-auth-theme-mode='dark'] .result-panel--idle) {
  background: rgba(20, 20, 23, 0.84);
  border-color: rgba(140, 110, 245, 0.16);
}

@media (max-width: 900px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }

  .hero-section,
  .toolbar,
  .result-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-score {
    width: 100%;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .toolbar-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .history-btn,
  .speaker-btn,
  .compare-btn {
    width: 100%;
  }
}
</style>
