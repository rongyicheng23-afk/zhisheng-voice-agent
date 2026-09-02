<template>
  <flow-background-panel>
    <div class="asr-page container-md" style="margin-top: 10px;">
      <div class="asr-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div class="hero-copy">
            <p class="hero-badge">智能转写</p>
            <h2>录音转文字</h2>
            <p class="hero-text">上传音频开始新的转写，也可以随时查看当前账号的历史记录与历史文本。</p>
          </div>
          <div class="hero-status">
            <span class="hero-status-label">当前准备</span>
            <strong>{{ uploadStateTitle }}</strong>
            <small>{{ uploadStateDescription }}</small>
          </div>
        </div>

        <quick-start-panel
          storage-key="asr-home"
          title="第一次用录音转文字，可以按这三步开始"
          subtitle="不需要复杂配置，选中文件后系统会自动进入转写流程，并把结果归档到历史记录。"
          :steps="quickStartSteps"
          :tips="quickStartTips"
        />

        <div class="toolbar">
          <div class="hint-list">
            <span class="hint-chip">批量上传</span>
            <span class="hint-chip">Java 调用 FunASR</span>
            <span class="hint-chip">历史文本可追溯</span>
          </div>
          <el-button type="primary" plain class="history-btn" @click="openHistoryDrawer">
            历史记录
          </el-button>
        </div>

        <el-upload
          ref="asrUploadRef"
          class="asr-upload"
          drag
          accept=".mp3,.wav"
          action="#"
          @click.capture="handleUploadTrigger"
          :on-change="handleChange"
          :on-remove="handleRemove"
          :file-list="fileList"
          :auto-upload="false"
          multiple
        >
          <div class="upload-content">
            <div class="upload-icon-wrapper">
              <div class="upload-emblem">
                <span class="upload-emblem-ring"></span>
                <span class="upload-emblem-dot"></span>
                <span class="upload-wave upload-wave--1"></span>
                <span class="upload-wave upload-wave--2"></span>
                <span class="upload-wave upload-wave--3"></span>
                <el-icon class="upload-emblem-icon"><Microphone /></el-icon>
              </div>
            </div>
            <div class="upload-text">
              <p class="main-text">将音频文件拖拽至此处，或点击下方「选择音频文件」按钮</p>
              <p class="support-text">支持 `.mp3`、`.wav`，上传后会进入转换页并保留待处理文件信息</p>
            </div>
            <div class="upload-button-wrapper">
              <el-button type="primary" class="select-btn">
                <el-icon class="el-icon--left"><Upload /></el-icon>选择音频文件
              </el-button>
            </div>
          </div>
        </el-upload>
      </div>
    </div>
  </flow-background-panel>

  <asr-history-drawer v-model="historyDrawerVisible" />
</template>

<script lang="ts">
import { computed, defineComponent, ref } from 'vue'
import { Microphone, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles, UploadInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import AsrHistoryDrawer from '@/components/AsrHistoryDrawer.vue'
import QuickStartPanel from '@/components/QuickStartPanel.vue'
import { useAudioUploadState } from '@/state/audio-upload-state'
import store from '@/store'

export default defineComponent({
  name: 'AsrWorkspace',
  components: {
    Upload,
    Microphone,
    FlowBackgroundPanel,
    AsrHistoryDrawer,
    QuickStartPanel
  },
  setup () {
    const router = useRouter()
    const historyDrawerVisible = ref(false)
    const asrUploadRef = ref<UploadInstance>()
    const { uploadFiles, setSelectedFiles } = useAudioUploadState()
    const isLoggedIn = computed(() => Boolean(store.state.token && store.state.user?.isLogin && Number(store.state.user?._id)))

    const uploadStateTitle = computed(() => uploadFiles.value.length ? `已选 ${uploadFiles.value.length} 个文件` : '等待上传音频')
    const uploadStateDescription = computed(() => uploadFiles.value.length ? '可以继续补充文件，进入下一页后开始转写。' : '支持批量选择音频，系统会在下一步统一处理。')
    const quickStartSteps = [
      {
        title: '选择音频文件',
        description: '支持拖拽或点击上传 `.mp3`、`.wav`，批量文件也可以一起进入转写流程。'
      },
      {
        title: '进入结果页等待转写',
        description: '选中文件后会自动进入结果页，后端统一调用语音识别服务开始处理。'
      },
      {
        title: '查看文本与历史',
        description: '转写完成后可查看全文、打开历史记录、试听原音，后续还能复用到智能纪要。'
      }
    ]
    const quickStartTips = ['建议先登录后再上传', '历史记录支持回看与导出', '长音频可转到智能纪要继续整理']

    const redirectToLogin = () => {
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }

    const ensureAsrAccess = (showMessage = true) => {
      if (isLoggedIn.value) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再上传音频或查看历史记录')
      }
      redirectToLogin()
      return false
    }

    const handleUploadTrigger = (event?: Event) => {
      if (ensureAsrAccess()) {
        return
      }
      event?.preventDefault()
      event?.stopPropagation()
    }

    const handleChange = (file: UploadFile, files: UploadFiles) => {
      if (!ensureAsrAccess(false)) {
        asrUploadRef.value?.clearFiles()
        setSelectedFiles([])
        return
      }

      const nextFiles = [...files]
      setSelectedFiles(nextFiles)

      if (nextFiles.length > 0) {
        router.push({ path: '/HomeResult' })
      }
    }

    const handleRemove = (file: UploadFile, files: UploadFiles) => {
      setSelectedFiles([...files])
    }

    const openHistoryDrawer = () => {
      if (!ensureAsrAccess()) {
        return
      }
      historyDrawerVisible.value = true
    }

    return {
      historyDrawerVisible,
      asrUploadRef,
      fileList: uploadFiles,
      uploadStateTitle,
      uploadStateDescription,
      quickStartSteps,
      quickStartTips,
      openHistoryDrawer,
      handleUploadTrigger,
      handleChange,
      handleRemove
    }
  }
})
</script>

<style scoped>
.asr-page {
  padding-bottom: 24px;
}

.asr-card {
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

.asr-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.42) 0%, rgba(255, 255, 255, 0) 100%);
  pointer-events: none;
}

.asr-card::after {
  content: '';
  position: absolute;
  right: -40px;
  top: -36px;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.16) 0%, rgba(124, 92, 255, 0) 72%);
  pointer-events: none;
}

.hero-section {
  position: relative;
  overflow: hidden;
  align-items: stretch;
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
  min-width: 240px;
  border-radius: 18px;
  padding: 20px 22px 20px 28px;
  background: linear-gradient(155deg, rgba(106, 79, 224, 0.94) 0%, rgba(134, 110, 248, 0.84) 100%);
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
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
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
  border-color: rgba(134, 116, 214, 0.22);
  background: rgba(255, 255, 255, 0.56);
  box-shadow: 0 14px 26px rgba(18, 35, 73, 0.08);
}

.asr-upload :deep(.el-upload) {
  width: 100%;
}

.asr-upload :deep(.el-upload-dragger) {
  position: relative;
  overflow: hidden;
  width: 100%;
  min-height: 520px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88) 0%, rgba(243, 248, 255, 0.94) 100%);
  border: 1px solid rgba(148, 128, 238, 0.2);
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), inset 0 0 0 1px rgba(148, 128, 238, 0.08);
  transition: all 0.35s ease;
}

.asr-upload :deep(.el-upload-dragger:hover) {
  border-color: #7c5cff;
  background: linear-gradient(180deg, rgba(250, 252, 255, 0.94) 0%, rgba(236, 245, 255, 0.98) 100%);
  box-shadow: 0 20px 40px rgba(106, 79, 224, 0.12);
}

.asr-upload :deep(.el-upload-dragger)::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 50% 22%, rgba(138, 110, 250, 0.08), transparent 26%),
    radial-gradient(circle at 22% 78%, rgba(124, 92, 255, 0.06), transparent 24%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.14) 0%, rgba(255, 255, 255, 0) 40%);
  pointer-events: none;
}

.upload-content {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.upload-icon-wrapper {
  margin-bottom: 32px;
}

.upload-emblem {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(69, 118, 255, 0.14) 0%, rgba(69, 118, 255, 0.26) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.84), 0 22px 40px rgba(37, 76, 184, 0.14);
  overflow: hidden;
}

.upload-emblem-ring {
  position: absolute;
  inset: 14px;
  border-radius: 18px;
  border: 1.5px solid rgba(69, 118, 255, 0.22);
}

.upload-emblem-dot {
  position: absolute;
  right: 18px;
  top: 18px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(135deg, #7fa9ff 0%, #7c5cff 100%);
  box-shadow: 0 0 0 6px rgba(124, 92, 255, 0.08);
}

.upload-wave {
  position: absolute;
  bottom: 20px;
  width: 7px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(124, 92, 255, 0.16) 0%, rgba(124, 92, 255, 0.92) 100%);
}

.upload-wave--1 {
  left: 24px;
  height: 18px;
}

.upload-wave--2 {
  left: 36px;
  height: 28px;
}

.upload-wave--3 {
  left: 48px;
  height: 22px;
}

.upload-emblem-icon {
  font-size: 46px;
  color: #7058e6;
  z-index: 1;
}

.upload-text {
  text-align: center;
  max-width: 620px;
  margin-bottom: 40px;
}

.main-text {
  font-size: 24px;
  color: #2a3856;
  line-height: 1.7;
  font-weight: 700;
  margin-bottom: 15px;
}

.support-text {
  font-size: 15px;
  color: #7d8ba6;
  line-height: 1.7;
}

.upload-button-wrapper {
  width: 100%;
  text-align: center;
}

.select-btn {
  border: none;
  border-radius: 999px;
  padding: 18px 42px;
  font-size: 18px;
  font-weight: 600;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  box-shadow: 0 18px 34px rgba(106, 79, 224, 0.24);
}

:global(html[data-auth-theme-mode='dark'] .asr-card) {
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
:global(html[data-auth-theme-mode='dark'] .main-text) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-text),
:global(html[data-auth-theme-mode='dark'] .support-text) {
  color: rgba(220, 230, 255, 0.84);
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
  color: #c8dcff;
}

:global(html[data-auth-theme-mode='dark'] .asr-upload .el-upload-dragger) {
  background:
    radial-gradient(circle at 50% 18%, rgba(124, 92, 255, 0.2), transparent 24%),
    linear-gradient(145deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 46%, rgba(20, 33, 61, 0.96) 100%);
  border-color: rgba(138, 110, 250, 0.42);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.03),
    inset 0 -1px 0 rgba(84, 121, 219, 0.08),
    0 24px 42px rgba(10, 10, 12, 0.38);
}

:global(html[data-auth-theme-mode='dark'] .asr-upload .el-upload-dragger:hover) {
  background:
    radial-gradient(circle at 50% 18%, rgba(130, 100, 248, 0.24), transparent 24%),
    linear-gradient(145deg, rgba(12, 22, 42, 0.99) 0%, rgba(18, 30, 56, 0.98) 46%, rgba(26, 42, 74, 0.97) 100%);
  border-color: rgba(146, 128, 250, 0.7);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    inset 0 -1px 0 rgba(138, 110, 250, 0.12),
    0 28px 48px rgba(10, 10, 12, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .asr-upload .el-upload-dragger::before) {
  background:
    radial-gradient(circle at 50% 18%, rgba(124, 92, 255, 0.2), transparent 24%),
    radial-gradient(circle at 20% 78%, rgba(67, 106, 219, 0.16), transparent 22%),
    radial-gradient(circle at 84% 28%, rgba(123, 98, 255, 0.12), transparent 20%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.02) 0%, rgba(255, 255, 255, 0) 26%);
}

:global(html[data-auth-theme-mode='dark'] .upload-emblem) {
  background: linear-gradient(135deg, rgba(60, 102, 224, 0.18) 0%, rgba(40, 63, 128, 0.42) 100%);
  box-shadow:
    0 18px 34px rgba(8, 16, 34, 0.34),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

:global(html[data-auth-theme-mode='dark'] .upload-emblem-ring) {
  border-color: rgba(255, 255, 255, 0.2);
}

:global(html[data-auth-theme-mode='dark'] .upload-emblem-icon) {
  color: #8db2ff;
}

:global(html[data-auth-theme-mode='dark'] .select-btn) {
  background: linear-gradient(135deg, #7c5cff 0%, #9b87ff 100%);
  box-shadow: 0 18px 34px rgba(42, 89, 210, 0.34);
}

@media (max-width: 900px) {
  .hero-section,
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-status {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .asr-card {
    padding: 18px;
  }

  .history-btn {
    width: 100%;
  }

  .asr-upload :deep(.el-upload-dragger) {
    min-height: 420px;
    padding: 28px 20px;
  }

  .main-text {
    font-size: 18px;
  }
}
</style>
