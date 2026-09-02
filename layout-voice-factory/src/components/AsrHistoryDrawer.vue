<template>
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    size="460px"
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
          <h4>历史记录</h4>
          <p>这里展示当前账号的录音转文字历史，可直接查看文本、试听或下载 MinIO 中的音频。</p>
        </div>
        <el-button type="primary" plain @click="loadHistoryData">刷新历史</el-button>
      </div>

      <empty-state v-if="!historyLoading && !historyList.length" type="asr" />

      <div v-else class="history-list">
        <article v-for="item in historyList" :key="item.id" class="history-item">
          <div class="history-item-header">
            <div>
              <h5>{{ item.originalFilename }}</h5>
              <p>{{ item.createTime || '-' }}</p>
            </div>
            <el-tag :type="statusTagType(item.status)">{{ item.status || 'UNKNOWN' }}</el-tag>
          </div>

          <div class="history-meta">
            <div class="meta-box">
              <span>模式</span>
              <strong>{{ item.requestMode || '-' }}</strong>
            </div>
            <div class="meta-box">
              <span>文件大小</span>
              <strong>{{ formatFileSize(item.fileSize) }}</strong>
            </div>
            <div class="meta-box meta-wide">
              <span>MinIO 路径</span>
              <strong class="mono">{{ item.object || '-' }}</strong>
            </div>
          </div>

          <div class="history-actions">
            <el-button v-if="showUseTextAction" type="primary" plain @click="handleUseHistoryText(item)">
              查看文本
            </el-button>
            <el-button v-if="showUseAudioAction" type="primary" :disabled="!item.hasAudio" @click="handleUseHistoryAudio(item)">
              生成纪要
            </el-button>
            <el-button type="primary" plain :disabled="!item.hasAudio" @click="previewHistoryAudio(item.id)">
              试听音频
            </el-button>
            <el-button type="success" plain :disabled="!item.hasAudio" @click="downloadHistoryAudio(item.id, item.originalFilename)">
              下载音频
            </el-button>
            <el-button type="danger" plain @click="deleteHistoryItem(item)">
              删除
            </el-button>
          </div>

          <audio
            v-if="historyAudioUrls[item.id]"
            class="audio-player"
            :src="historyAudioUrls[item.id]"
            controls
            preload="none"
          />

          <div class="history-text">
            {{ item.transcription || item.errorMessage || '暂无文本内容' }}
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
import { AudioHistoryItem, fetchHistoryAudioBlob, getMyAudioHistory, deleteAudioHistory } from '@/api/history'
import EmptyState from '@/components/EmptyState.vue'

export default defineComponent({
  name: 'AsrHistoryDrawer',
  components: { EmptyState },
  props: {
    modelValue: {
      type: Boolean,
      required: true
    },
    showUseTextAction: {
      type: Boolean,
      default: false
    },
    showUseAudioAction: {
      type: Boolean,
      default: false
    }
  },
  emits: ['update:modelValue', 'use-text', 'use-audio'],
  setup (props, { emit }) {
    const router = useRouter()
    const historyList = ref<AudioHistoryItem[]>([])
    const historyLoading = ref(false)
    const historyAudioUrls = ref<Record<number, string>>({})

    const drawerVisible = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const getAuthToken = () => store.state.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''

    const hasHistoryAuth = () => Boolean(Number(store.state.user?._id) && store.state.user?.isLogin && getAuthToken())

    const revokeHistoryAudioUrls = () => {
      Object.values(historyAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      historyAudioUrls.value = {}
    }

    const closeDrawer = () => {
      drawerVisible.value = false
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      closeDrawer()
      ElMessage.error('登录状态已失效，请重新登录后再查看历史记录')
      router.push('/login')
    }

    const ensureHistoryAccess = (showMessage = true) => {
      if (hasHistoryAuth()) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再查看历史记录')
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
        const res = await getMyAudioHistory()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载历史记录失败')
        }
        historyList.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载历史记录失败')
      } finally {
        historyLoading.value = false
      }
    }

    const previewHistoryAudio = async (historyId: number) => {
      if (!ensureHistoryAccess()) {
        return
      }
      if (historyAudioUrls.value[historyId]) {
        return
      }
      try {
        const blob = await fetchHistoryAudioBlob(historyId)
        historyAudioUrls.value = {
          ...historyAudioUrls.value,
          [historyId]: URL.createObjectURL(blob)
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载历史音频失败')
      }
    }

    const downloadHistoryAudio = async (historyId: number, filename: string) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        const blob = await fetchHistoryAudioBlob(historyId)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename || `history-${historyId}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载历史音频失败')
      }
    }

    const handleUseHistoryText = (item: AudioHistoryItem) => {
      emit('use-text', item)
      closeDrawer()
    }

    const handleUseHistoryAudio = (item: AudioHistoryItem) => {
      emit('use-audio', item)
      closeDrawer()
    }

    const deleteHistoryItem = async (item: AudioHistoryItem) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        await ElMessageBox.confirm(
          `确定要删除「${item.originalFilename}」的历史记录吗？删除后音频文件和转写文本将无法恢复。`,
          '删除确认',
          { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return
      }
      try {
        const res = await deleteAudioHistory(item.id)
        if (res.code !== 200) {
          throw new Error(res.msg || '删除失败')
        }
        // 清理已缓存的音频 URL
        if (historyAudioUrls.value[item.id]) {
          URL.revokeObjectURL(historyAudioUrls.value[item.id])
          const { [item.id]: _, ...rest } = historyAudioUrls.value
          historyAudioUrls.value = rest
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
      if (status === 'UPLOADED') return 'warning'
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
      revokeHistoryAudioUrls()
    })

    return {
      drawerVisible,
      historyList,
      historyLoading,
      historyAudioUrls,
      loadHistoryData,
      previewHistoryAudio,
      downloadHistoryAudio,
      handleUseHistoryText,
      handleUseHistoryAudio,
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
  padding: 20px 16px 24px;
  overflow-y: auto;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 100%);
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.drawer-head h4 {
  margin: 0;
  color: #303133;
}

.drawer-head p {
  margin: 8px 0 0;
  color: #909399;
  font-size: 14px;
  line-height: 1.6;
}

.history-list {
  display: grid;
  gap: 16px;
}

.history-item {
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  border: 1px solid #e7eef9;
}

.history-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.history-item-header h5 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.history-item-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}

.history-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.meta-box {
  padding: 14px;
  border-radius: 12px;
  background: #f8fbff;
  border: 1px solid #ebf2ff;
}

.meta-wide {
  grid-column: span 1;
}

.meta-box span {
  display: block;
  margin-bottom: 8px;
  color: #909399;
  font-size: 13px;
}

.meta-box strong {
  color: #303133;
  font-size: 14px;
}

.history-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.audio-player {
  width: 100%;
  margin-top: 14px;
}

.history-text {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7faff;
  border: 1px solid #e7eefc;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.mono {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}

:global(html[data-auth-theme-mode='dark'] .drawer-body) {
  background: linear-gradient(180deg, rgba(11, 19, 38, 0.98) 0%, rgba(17, 28, 52, 0.98) 100%);
}

:global(html[data-auth-theme-mode='dark'] .drawer-head h4),
:global(html[data-auth-theme-mode='dark'] .history-item-header h5),
:global(html[data-auth-theme-mode='dark'] .meta-box strong),
:global(html[data-auth-theme-mode='dark'] .history-text) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .drawer-head p),
:global(html[data-auth-theme-mode='dark'] .history-item-header p),
:global(html[data-auth-theme-mode='dark'] .meta-box span) {
  color: rgba(220, 230, 255, 0.82);
}

:global(html[data-auth-theme-mode='dark'] .history-item),
:global(html[data-auth-theme-mode='dark'] .meta-box),
:global(html[data-auth-theme-mode='dark'] .history-text) {
  background: rgba(20, 20, 23, 0.84);
  border-color: rgba(109, 143, 241, 0.18);
}

@media (max-width: 768px) {
:global(.history-drawer-overlay) {
    top: 60px;
    height: calc(100vh - 60px);
  }

  .drawer-head,
  .history-item-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .history-meta {
    grid-template-columns: 1fr;
  }

  .history-actions {
    justify-content: flex-start;
  }
}
</style>
