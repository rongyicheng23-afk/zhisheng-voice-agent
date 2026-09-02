<template>
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    size="500px"
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
          <h4>声纹对比历史</h4>
          <p>这里会展示当前账号的两段音频、相似度分数、判定结果和对应的 MinIO 路径。</p>
        </div>
        <el-button type="primary" plain @click="loadHistoryData">刷新历史</el-button>
      </div>

      <empty-state v-if="!historyLoading && !historyList.length" type="voiceprint" />

      <div v-else class="history-list">
        <article v-for="item in historyList" :key="item.id" class="history-item">
          <div class="history-item-header">
            <div>
              <h5>{{ item.resultMessage || `记录 #${item.id}` }}</h5>
              <p>{{ item.createTime || '-' }}</p>
            </div>
            <el-tag :type="statusTagType(item.status, item.samePerson)">{{ item.status || 'UNKNOWN' }}</el-tag>
          </div>

          <div class="history-score">
            <div class="score-box">
              <span>相似度</span>
              <strong>{{ formatPercent(item.score) }}</strong>
            </div>
            <div class="score-box">
              <span>阈值</span>
              <strong>{{ formatPercent(item.thresholdValue) }}</strong>
            </div>
            <div class="score-box">
              <span>判定</span>
              <strong>{{ samePersonText(item.samePerson) }}</strong>
            </div>
          </div>

          <div class="history-files">
            <div class="file-card">
              <span>音频 A</span>
              <strong>{{ item.leftFilename || '-' }}</strong>
              <em>{{ formatFileSize(item.leftFileSize) }}</em>
              <small class="mono">{{ item.leftObject || '-' }}</small>
            </div>
            <div class="file-card">
              <span>音频 B</span>
              <strong>{{ item.rightFilename || '-' }}</strong>
              <em>{{ formatFileSize(item.rightFileSize) }}</em>
              <small class="mono">{{ item.rightObject || '-' }}</small>
            </div>
          </div>

          <div class="history-actions">
            <el-button type="primary" plain :disabled="!item.hasLeftAudio" @click="previewAudio(item.id, 'a')">
              试听音频 A
            </el-button>
            <el-button type="success" plain :disabled="!item.hasLeftAudio" @click="downloadAudio(item.id, 'a', item.leftFilename)">
              下载音频 A
            </el-button>
            <el-button type="primary" plain :disabled="!item.hasRightAudio" @click="previewAudio(item.id, 'b')">
              试听音频 B
            </el-button>
            <el-button type="success" plain :disabled="!item.hasRightAudio" @click="downloadAudio(item.id, 'b', item.rightFilename)">
              下载音频 B
            </el-button>
            <el-button type="danger" plain @click="deleteHistoryItem(item)">
              删除
            </el-button>
          </div>

          <audio
            v-if="leftAudioUrls[item.id]"
            class="audio-player"
            :src="leftAudioUrls[item.id]"
            controls
            preload="none"
          />

          <audio
            v-if="rightAudioUrls[item.id]"
            class="audio-player"
            :src="rightAudioUrls[item.id]"
            controls
            preload="none"
          />

          <div class="history-text">
            {{ item.resultMessage || item.errorMessage || '暂无结果描述' }}
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
  VoiceprintHistoryItem,
  fetchVoiceprintAudioBlob,
  getMyVoiceprintHistory,
  deleteVoiceprintHistory
} from '@/api/voiceprint'
import EmptyState from '@/components/EmptyState.vue'

export default defineComponent({
  name: 'VoiceprintHistoryDrawer',
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
    const historyList = ref<VoiceprintHistoryItem[]>([])
    const historyLoading = ref(false)
    const leftAudioUrls = ref<Record<number, string>>({})
    const rightAudioUrls = ref<Record<number, string>>({})

    const drawerVisible = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const getAuthToken = () => store.state.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''

    const hasHistoryAuth = () => Boolean(Number(store.state.user?._id) && store.state.user?.isLogin && getAuthToken())

    const revokeAudioUrls = () => {
      Object.values(leftAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      Object.values(rightAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      leftAudioUrls.value = {}
      rightAudioUrls.value = {}
    }

    const closeDrawer = () => {
      drawerVisible.value = false
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      closeDrawer()
      ElMessage.error('登录状态已失效，请重新登录后再查看声纹历史')
      router.push('/login')
    }

    const ensureHistoryAccess = (showMessage = true) => {
      if (hasHistoryAuth()) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再查看声纹历史')
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
        const res = await getMyVoiceprintHistory()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载声纹历史失败')
        }
        historyList.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载声纹历史失败')
      } finally {
        historyLoading.value = false
      }
    }

    const previewAudio = async (historyId: number, side: 'a' | 'b') => {
      if (!ensureHistoryAccess()) {
        return
      }
      if (side === 'a' && leftAudioUrls.value[historyId]) {
        return
      }
      if (side === 'b' && rightAudioUrls.value[historyId]) {
        return
      }
      try {
        const blob = await fetchVoiceprintAudioBlob(historyId, side)
        if (side === 'a') {
          leftAudioUrls.value = { ...leftAudioUrls.value, [historyId]: URL.createObjectURL(blob) }
        } else {
          rightAudioUrls.value = { ...rightAudioUrls.value, [historyId]: URL.createObjectURL(blob) }
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载历史音频失败')
      }
    }

    const downloadAudio = async (historyId: number, side: 'a' | 'b', filename?: string) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        const blob = await fetchVoiceprintAudioBlob(historyId, side)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename || `voiceprint-${side}-${historyId}.wav`
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

    const deleteHistoryItem = async (item: VoiceprintHistoryItem) => {
      if (!ensureHistoryAccess()) {
        return
      }
      try {
        await ElMessageBox.confirm(
          `确定要删除「${item.resultMessage || '#' + item.id}」的声纹对比记录吗？删除后两段音频和对比结果将无法恢复。`,
          '删除确认',
          { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return
      }
      try {
        const res = await deleteVoiceprintHistory(item.id)
        if (res.code !== 200) {
          throw new Error(res.msg || '删除失败')
        }
        if (leftAudioUrls.value[item.id]) {
          URL.revokeObjectURL(leftAudioUrls.value[item.id])
          const { [item.id]: _, ...rest } = leftAudioUrls.value
          leftAudioUrls.value = rest
        }
        if (rightAudioUrls.value[item.id]) {
          URL.revokeObjectURL(rightAudioUrls.value[item.id])
          const { [item.id]: _, ...rest } = rightAudioUrls.value
          rightAudioUrls.value = rest
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
      if (!size || size <= 0) return '0 B'
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(2)} MB`
    }

    const formatPercent = (score?: number) => {
      if (score === undefined || score === null) return '--'
      return `${(score * 100).toFixed(2)}%`
    }

    const samePersonText = (samePerson?: boolean) => {
      if (samePerson === undefined || samePerson === null) return '--'
      return samePerson ? '同一个人' : '不是同一个人'
    }

    const statusTagType = (status?: string, samePerson?: boolean) => {
      if (status === 'SUCCESS' && samePerson === true) return 'success'
      if (status === 'SUCCESS' && samePerson === false) return 'warning'
      if (status === 'FAILED') return 'danger'
      return 'info'
    }

    watch(
      () => props.modelValue,
      async visible => {
        if (!visible) return
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
      leftAudioUrls,
      rightAudioUrls,
      loadHistoryData,
      previewAudio,
      downloadAudio,
      deleteHistoryItem,
      formatFileSize,
      formatPercent,
      samePersonText,
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

.history-score {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.score-box,
.file-card {
  background: #f5f8ff;
  border-radius: 12px;
  padding: 12px 14px;
}

.score-box span,
.file-card span {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 6px;
}

.score-box strong,
.file-card strong {
  color: #223164;
  font-size: 16px;
  word-break: break-word;
}

.file-card em {
  display: block;
  margin-top: 8px;
  color: #536999;
  font-size: 12px;
  font-style: normal;
}

.file-card small {
  display: block;
  margin-top: 10px;
  color: #223164;
  line-height: 1.7;
}

.history-files {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
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

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  word-break: break-all;
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

  .history-score,
  .history-files {
    grid-template-columns: 1fr;
  }
}
</style>
