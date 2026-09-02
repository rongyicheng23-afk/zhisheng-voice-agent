<template>
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    size="480px"
    :with-header="false"
    append-to-body
    :z-index="2600"
    modal-class="history-drawer-overlay"
    body-class="history-drawer-body-shell"
    class="history-drawer"
  >
    <div class="drawer-body" v-loading="historyLoading">
      <div class="drawer-head">
        <div>
          <h4>语音合成历史</h4>
          <p>这里会展示当前账号的文本、参考音频、生成音频和对应的 MinIO 路径。</p>
        </div>
        <el-button type="primary" plain @click="loadHistoryData">刷新历史</el-button>
      </div>

      <empty-state v-if="!historyLoading && !historyList.length" type="tts" />

      <div v-else class="history-list">
        <article v-for="item in historyList" :key="item.id" class="history-item">
          <div class="history-item-header">
            <div>
              <h5>{{ item.resultFilename || item.sourceFilename || `记录 #${item.id}` }}</h5>
              <p>{{ item.createTime || '-' }}</p>
            </div>
            <el-tag :type="statusTagType(item.status)">{{ item.status || 'UNKNOWN' }}</el-tag>
          </div>

          <div class="history-meta">
            <div class="meta-box">
              <span>情绪</span>
              <strong>{{ item.emotion || '-' }}</strong>
            </div>
            <div class="meta-box">
              <span>语言</span>
              <strong>{{ item.language || '-' }}</strong>
            </div>
            <div class="meta-box">
              <span>请求格式</span>
              <strong>{{ item.requestedFormat || '-' }}</strong>
            </div>
            <div class="meta-box">
              <span>参考音频</span>
              <strong>{{ formatFileSize(item.sourceFileSize) }}</strong>
            </div>
            <div class="meta-box">
              <span>生成音频</span>
              <strong>{{ formatFileSize(item.resultFileSize) }}</strong>
            </div>
            <div class="meta-box meta-wide">
              <span>参考音频 MinIO</span>
              <strong class="mono">{{ item.sourceObject || '-' }}</strong>
            </div>
            <div class="meta-box meta-wide">
              <span>生成音频 MinIO</span>
              <strong class="mono">{{ item.resultObject || '-' }}</strong>
            </div>
          </div>

          <div class="history-actions">
            <el-button type="primary" plain :disabled="!item.hasSourceAudio" @click="previewSourceAudio(item.id)">
              试听参考音频
            </el-button>
            <el-button type="success" plain :disabled="!item.hasSourceAudio" @click="downloadSourceAudio(item.id, item.sourceFilename)">
              下载参考音频
            </el-button>
            <el-button type="primary" plain :disabled="!item.hasResultAudio" @click="previewResultAudio(item.id)">
              试听生成音频
            </el-button>
            <el-button type="success" plain :disabled="!item.hasResultAudio" @click="downloadResultAudio(item.id, item.resultFilename)">
              下载生成音频
            </el-button>
            <el-button type="danger" plain @click="deleteHistoryItem(item)">
              删除
            </el-button>
          </div>

          <audio
            v-if="sourceAudioUrls[item.id]"
            class="audio-player"
            :src="sourceAudioUrls[item.id]"
            controls
            preload="none"
          />

          <audio
            v-if="resultAudioUrls[item.id]"
            class="audio-player"
            :src="resultAudioUrls[item.id]"
            controls
            preload="none"
          />

          <div class="history-text">
            {{ item.inputText || item.errorMessage || '暂无文本内容' }}
          </div>
        </article>
      </div>
    </div>
  </el-drawer>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import store from '@/store'
import {
  TtsHistoryItem,
  fetchTtsResultAudioBlob,
  fetchTtsSourceAudioBlob,
  getMyTtsHistory,
  deleteTtsHistory
} from '@/api/tts'
import EmptyState from '@/components/EmptyState.vue'

export default defineComponent({
  name: 'TtsHistoryDrawer',
  components: { EmptyState },
  props: {
    modelValue: {
      type: Boolean,
      required: true
    }
  },
  emits: ['update:modelValue'],
  setup (props, { emit }) {
    const router = useRouter()
    const historyList = ref<TtsHistoryItem[]>([])
    const historyLoading = ref(false)
    const sourceAudioUrls = ref<Record<number, string>>({})
    const resultAudioUrls = ref<Record<number, string>>({})

    const drawerVisible = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const getAuthToken = () => store.state.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''

    const hasHistoryAuth = () => Boolean(Number(store.state.user?._id) && store.state.user?.isLogin && getAuthToken())

    const revokeAudioUrls = () => {
      Object.values(sourceAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      Object.values(resultAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      sourceAudioUrls.value = {}
      resultAudioUrls.value = {}
    }

    const closeDrawer = () => {
      drawerVisible.value = false
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      closeDrawer()
      ElMessage.error('登录状态已失效，请重新登录后再查看语音合成历史')
      router.push('/login')
    }

    const ensureHistoryAccess = (showMessage = true) => {
      if (hasHistoryAuth()) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再查看语音合成历史')
      }
      closeDrawer()
      router.push('/login')
      return false
    }

    const loadHistoryData = async () => {
      if (!ensureHistoryAccess()) {
        return
      }
      historyLoading.value = true
      try {
        const res = await getMyTtsHistory()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载语音合成历史失败')
        }
        historyList.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载语音合成历史失败')
      } finally {
        historyLoading.value = false
      }
    }

    const previewSourceAudio = async (historyId: number) => {
      if (!ensureHistoryAccess()) {
        return
      }
      if (sourceAudioUrls.value[historyId]) {
        return
      }
      try {
        const blob = await fetchTtsSourceAudioBlob(historyId)
        sourceAudioUrls.value = {
          ...sourceAudioUrls.value,
          [historyId]: URL.createObjectURL(blob)
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载参考音频失败')
      }
    }

    const previewResultAudio = async (historyId: number) => {
      if (!ensureHistoryAccess()) {
        return
      }
      if (resultAudioUrls.value[historyId]) {
        return
      }
      try {
        const blob = await fetchTtsResultAudioBlob(historyId)
        resultAudioUrls.value = {
          ...resultAudioUrls.value,
          [historyId]: URL.createObjectURL(blob)
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载生成音频失败')
      }
    }

    const downloadSourceAudio = async (historyId: number, filename?: string) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        const blob = await fetchTtsSourceAudioBlob(historyId)
        triggerDownload(blob, filename || `tts-source-${historyId}.wav`)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载参考音频失败')
      }
    }

    const downloadResultAudio = async (historyId: number, filename?: string) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        const blob = await fetchTtsResultAudioBlob(historyId)
        triggerDownload(blob, filename || `tts-result-${historyId}.wav`)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载生成音频失败')
      }
    }

    const triggerDownload = (blob: Blob, filename: string) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    }

    const deleteHistoryItem = async (item: TtsHistoryItem) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        await ElMessageBox.confirm(
          `确定要删除「${item.resultFilename || item.sourceFilename || '#' + item.id}」的历史记录吗？删除后参考音频和生成音频将无法恢复。`,
          '删除确认',
          { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return
      }
      try {
        const res = await deleteTtsHistory(item.id)
        if (res.code !== 200) {
          throw new Error(res.msg || '删除失败')
        }
        if (sourceAudioUrls.value[item.id]) {
          URL.revokeObjectURL(sourceAudioUrls.value[item.id])
          const { [item.id]: _, ...rest } = sourceAudioUrls.value
          sourceAudioUrls.value = rest
        }
        if (resultAudioUrls.value[item.id]) {
          URL.revokeObjectURL(resultAudioUrls.value[item.id])
          const { [item.id]: _, ...rest } = resultAudioUrls.value
          resultAudioUrls.value = rest
        }
        historyList.value = historyList.value.filter(h => h.id !== item.id)
        ElMessage.success('已删除')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '删除失败')
      }
    }

    const formatFileSize = (size?: number) => {
      if (!size || size <= 0) {
        return '0 B'
      }
      if (size < 1024) {
        return `${size} B`
      }
      if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} KB`
      }
      return `${(size / 1024 / 1024).toFixed(2)} MB`
    }

    const statusTagType = (status?: string) => {
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED') return 'danger'
      if (status === 'SOURCE_UPLOADED') return 'warning'
      return 'info'
    }

    watch(
      () => props.modelValue,
      async visible => {
        if (!visible) {
          return
        }
        await loadHistoryData()
      }
    )

    onBeforeUnmount(() => {
      revokeAudioUrls()
    })

    return {
      drawerVisible,
      historyList,
      historyLoading,
      sourceAudioUrls,
      resultAudioUrls,
      loadHistoryData,
      previewSourceAudio,
      previewResultAudio,
      downloadSourceAudio,
      downloadResultAudio,
      deleteHistoryItem,
      formatFileSize,
      statusTagType
    }
  }
})
</script>

<style scoped>
:global(.history-drawer-overlay) {
  top: 60px;
  height: calc(100vh - 60px);
}

:global(.history-drawer-body-shell) {
  padding: 0 !important;
  overflow: hidden !important;
}

.drawer-body {
  height: 100%;
  padding: 24px 20px;
  overflow-y: auto;
  background: linear-gradient(180deg, #f7fbff 0%, #eef6ff 100%);
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.drawer-head h4 {
  margin: 0;
  font-size: 20px;
  color: #1d2a57;
}

.drawer-head p {
  margin: 8px 0 0;
  color: #5d6b94;
  line-height: 1.6;
  font-size: 13px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.history-item {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(121, 154, 255, 0.18);
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 16px 36px rgba(72, 113, 197, 0.12);
}

.history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.history-item-header h5 {
  margin: 0;
  font-size: 16px;
  color: #223164;
}

.history-item-header p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #7c8ab2;
}

.history-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.meta-box {
  background: #f5f8ff;
  border-radius: 12px;
  padding: 10px 12px;
  min-width: 0;
}

.meta-box span {
  display: block;
  font-size: 12px;
  color: #7c8ab2;
  margin-bottom: 6px;
}

.meta-box strong {
  color: #223164;
  font-size: 13px;
  word-break: break-word;
}

.meta-wide {
  grid-column: 1 / -1;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.audio-player {
  width: 100%;
  margin-top: 12px;
}

.history-text {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f8ff 0%, #edf5ff 100%);
  color: #243362;
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 768px) {
:global(.history-drawer-overlay) {
    top: 60px;
    height: calc(100vh - 60px);
  }

  .drawer-body {
    padding: 18px 14px;
  }

  .drawer-head {
    flex-direction: column;
    align-items: stretch;
  }

  .history-meta {
    grid-template-columns: 1fr;
  }
}
</style>
