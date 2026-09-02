<template>
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    size="540px"
    :with-header="false"
    append-to-body
    :z-index="2600"
    modal-class="meeting-drawer-overlay"
    body-class="meeting-drawer-body-shell"
    class="meeting-drawer"
  >
    <div class="drawer-body" v-loading="historyLoading">
      <div class="drawer-head">
        <div>
          <h4>智能纪要历史</h4>
          <p>这里会展示当前账号生成过的会议/课堂纪要，包括摘要、关键词、待办事项、全文转写和原始音频回放入口。</p>
        </div>
        <el-button type="primary" plain @click="loadHistoryData">刷新历史</el-button>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="按标题、摘要、全文关键词搜索"
          @keyup.enter="loadHistoryData"
        />
        <el-select v-model="filters.sceneType" clearable placeholder="全部场景">
          <el-option label="会议模式" value="meeting" />
          <el-option label="课堂模式" value="classroom" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已上传" value="UPLOADED" />
          <el-option label="后台处理中" value="PROCESSING" />
          <el-option label="处理成功" value="SUCCESS" />
          <el-option label="处理失败" value="FAILED" />
        </el-select>
        <el-select v-model="filters.hasTodosOption" clearable placeholder="全部待办状态">
          <el-option label="仅看含待办" value="true" />
          <el-option label="仅看无待办" value="false" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          range-separator="至"
          unlink-panels
        />
        <el-button type="primary" @click="loadHistoryData">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <empty-state v-if="!historyLoading && !historyList.length" type="meeting" />

      <div v-else class="history-list">
        <article v-for="item in historyList" :key="item.id" class="history-item">
          <div class="history-item-header">
            <div>
              <h5>{{ item.title }}</h5>
              <p>{{ item.createTime || '-' }}</p>
            </div>
            <div class="header-tags">
              <el-tag type="info">{{ sceneLabel(item.sceneType) }}</el-tag>
              <el-tag :type="statusTagType(item.status)">{{ statusLabel(item.status) }}</el-tag>
            </div>
          </div>

          <div class="summary-card">
            <span>纪要摘要</span>
            <p>{{ item.summaryText || item.errorMessage || '暂无摘要' }}</p>
          </div>

          <div v-if="item.keywords?.length" class="keyword-list">
            <span class="section-label">关键词</span>
            <div class="tag-list">
              <el-tag v-for="keyword in item.keywords" :key="keyword" effect="plain">{{ keyword }}</el-tag>
            </div>
          </div>

          <div v-if="item.todos?.length" class="todo-list">
            <span class="section-label">待办事项</span>
            <ul>
              <li v-for="todo in item.todos" :key="todo">{{ todo }}</li>
            </ul>
          </div>

          <div class="transcript-card">
            <span>全文转写</span>
            <p>{{ item.fullTranscript || '暂无转写结果' }}</p>
          </div>

          <div class="history-actions">
            <el-button type="primary" @click="viewDetail(item.id)">
              查看详情
            </el-button>
            <el-button type="warning" plain @click="openExportDialog(item.id, item.title)">
              导出纪要
            </el-button>
            <el-button type="primary" plain :disabled="!item.hasRawAudio" @click="previewAudio(item.id)">
              试听原音
            </el-button>
            <el-button type="success" plain :disabled="!item.hasRawAudio" @click="downloadAudio(item.id, item.rawFilename)">
              下载原音
            </el-button>
          </div>

          <audio
            v-if="audioUrls[item.id]"
            class="audio-player"
            :src="audioUrls[item.id]"
            controls
            preload="none"
          />
        </article>
      </div>
    </div>
    <meeting-export-template-dialog
      v-model="exportDialogVisible"
      :template="exportTemplateConfig"
      default-format="docx"
      @confirm="handleExportConfirm"
    />
  </el-drawer>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import store from '@/store'
import MeetingExportTemplateDialog from '@/components/MeetingExportTemplateDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  fetchMeetingAudioBlob,
  fetchMeetingExportBlob,
  getMyMeetingHistory,
  MeetingExportTemplate,
  MeetingHistoryQuery,
  MeetingHistoryItem
} from '@/api/meeting'

export default defineComponent({
  name: 'MeetingHistoryDrawer',
  components: {
    MeetingExportTemplateDialog,
    EmptyState
  },
  props: {
    modelValue: {
      type: Boolean,
      required: true
    }
  },
  emits: ['update:modelValue'],
  setup (props, { emit }) {
    type ExportFormat = 'txt' | 'md' | 'docx'
    interface ExportConfirmPayload {
      format: ExportFormat
      template: MeetingExportTemplate
    }

    const router = useRouter()
    const historyList = ref<MeetingHistoryItem[]>([])
    const historyLoading = ref(false)
    const audioUrls = ref<Record<number, string>>({})
    const exportDialogVisible = ref(false)
    const exportTemplateConfig = ref<MeetingExportTemplate>({
      includeMeta: true,
      includeSummary: true,
      includeKeywords: true,
      includeStructuredSections: true,
      includeRoleInsights: true,
      includeTodoChains: true,
      includeDecisionInsights: true,
      includeTodos: true,
      includeSpeakerTranscript: true,
      includeSpeakerBlocks: true,
      includeFullTranscript: true
    })
    const exportTarget = ref<{ meetingId: number, title?: string } | null>(null)
    const filters = ref<MeetingHistoryQuery & { hasTodosOption?: '' | 'true' | 'false', dateRange?: string[] }>({
      keyword: '',
      sceneType: '',
      status: '',
      hasTodosOption: '',
      dateRange: []
    })
    const exportTemplateStorageKey = computed(() => `meeting-export-template:${Number(store.state.user?._id) || 0}`)

    const drawerVisible = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const getAuthToken = () => store.state.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''

    const hasMeetingAuth = () => Boolean(Number(store.state.user?._id) && store.state.user?.isLogin && getAuthToken())

    const revokeAudioUrls = () => {
      Object.values(audioUrls.value).forEach(url => URL.revokeObjectURL(url))
      audioUrls.value = {}
    }

    const closeDrawer = () => {
      drawerVisible.value = false
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      closeDrawer()
      ElMessage.error('登录状态已失效，请重新登录后再查看纪要历史')
      router.push('/login')
    }

    const ensureMeetingAccess = (showMessage = true) => {
      if (hasMeetingAuth()) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再查看纪要历史')
      }
      closeDrawer()
      router.push('/login')
      return false
    }

    const loadHistoryData = async () => {
      if (!ensureMeetingAccess()) {
        return
      }
      historyLoading.value = true
      try {
        const res = await getMyMeetingHistory({
          keyword: filters.value.keyword?.trim() || undefined,
          sceneType: filters.value.sceneType || undefined,
          status: filters.value.status || undefined,
          hasTodos: filters.value.hasTodosOption === '' ? undefined : filters.value.hasTodosOption === 'true',
          dateFrom: filters.value.dateRange?.[0] || undefined,
          dateTo: filters.value.dateRange?.[1] || undefined
        })
        if (res.code !== 200) {
          throw new Error(res.msg || '加载纪要历史失败')
        }
        historyList.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载纪要历史失败')
      } finally {
        historyLoading.value = false
      }
    }

    const resetFilters = async () => {
      filters.value = {
        keyword: '',
        sceneType: '',
        status: '',
        hasTodosOption: '',
        dateRange: []
      }
      await loadHistoryData()
    }

    const previewAudio = async (meetingId: number) => {
      if (!ensureMeetingAccess()) {
        return
      }
      if (audioUrls.value[meetingId]) {
        return
      }
      try {
        const blob = await fetchMeetingAudioBlob(meetingId)
        audioUrls.value = { ...audioUrls.value, [meetingId]: URL.createObjectURL(blob) }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载原始音频失败')
      }
    }

    const downloadAudio = async (meetingId: number, filename?: string) => {
      if (!ensureMeetingAccess()) {
        return
      }
      try {
        const blob = await fetchMeetingAudioBlob(meetingId)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename || `meeting-note-${meetingId}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载原始音频失败')
      }
    }

    const loadExportTemplate = () => {
      try {
        const raw = localStorage.getItem(exportTemplateStorageKey.value)
        if (!raw) return
        const parsed = JSON.parse(raw) as MeetingExportTemplate
        exportTemplateConfig.value = { ...exportTemplateConfig.value, ...(parsed || {}) }
      } catch (error) {
        console.warn('load export template failed', error)
      }
    }

    const saveExportTemplate = (template: MeetingExportTemplate) => {
      exportTemplateConfig.value = { ...exportTemplateConfig.value, ...template }
      localStorage.setItem(exportTemplateStorageKey.value, JSON.stringify(exportTemplateConfig.value))
    }

    const exportMeeting = async (
      meetingId: number,
      title: string | undefined,
      format: ExportFormat,
      template?: MeetingExportTemplate
    ) => {
      if (!ensureMeetingAccess()) {
        return
      }
      try {
        const blob = await fetchMeetingExportBlob(meetingId, format, template || exportTemplateConfig.value)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        const safeTitle = (title || `meeting-note-${meetingId}`).replace(/[\\/:*?"<>|]/g, '_')
        link.download = `${safeTitle}.${format}`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '导出纪要失败')
      }
    }

    const openExportDialog = (meetingId: number, title?: string) => {
      if (!ensureMeetingAccess()) {
        return
      }
      exportTarget.value = { meetingId, title }
      exportDialogVisible.value = true
    }

    const handleExportConfirm = async (payload: ExportConfirmPayload) => {
      if (!exportTarget.value) {
        ElMessage.warning('当前未选择导出目标')
        return
      }
      saveExportTemplate(payload.template)
      await exportMeeting(exportTarget.value.meetingId, exportTarget.value.title, payload.format, payload.template)
    }

    const viewDetail = (meetingId: number) => {
      closeDrawer()
      router.push(`/MeetingNotes/${meetingId}`)
    }

    const statusTagType = (status?: string) => {
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILED') return 'danger'
      if (status === 'PROCESSING') return 'warning'
      return 'info'
    }

    const statusLabel = (status?: string) => {
      if (status === 'PENDING') return '待处理'
      if (status === 'UPLOADED') return '已上传'
      if (status === 'PROCESSING') return '处理中'
      if (status === 'SUCCESS') return '处理成功'
      if (status === 'FAILED') return '处理失败'
      return status || '未知状态'
    }

    const sceneLabel = (sceneType?: string) => {
      return sceneType === 'classroom' ? '课堂模式' : '会议模式'
    }

    watch(
      () => props.modelValue,
      async visible => {
        if (!visible) return
        loadExportTemplate()
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
      audioUrls,
      exportDialogVisible,
      exportTemplateConfig,
      filters,
      loadHistoryData,
      resetFilters,
      previewAudio,
      downloadAudio,
      exportMeeting,
      openExportDialog,
      handleExportConfirm,
      viewDetail,
      statusTagType,
      statusLabel,
      sceneLabel
    }
  }
})
</script>

<style scoped>
:global(.meeting-drawer-overlay) {
  top: 60px;
  height: calc(100vh - 60px);
}

:global(.meeting-drawer-body-shell) {
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

.filter-bar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
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

.header-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-card,
.transcript-card {
  background: #f5f8ff;
  border-radius: 12px;
  padding: 14px;
}

.summary-card span,
.transcript-card span,
.section-label {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-card p,
.transcript-card p {
  margin: 0;
  color: #223164;
  line-height: 1.8;
  white-space: pre-wrap;
}

.transcript-card {
  margin-top: 12px;
}

.keyword-list,
.todo-list {
  margin-top: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.todo-list ul {
  margin: 0;
  padding-left: 18px;
  color: #243362;
  line-height: 1.8;
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

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .drawer-body) {
  background: linear-gradient(180deg, rgba(9, 16, 31, 0.98) 0%, rgba(12, 21, 41, 0.98) 100%);
}

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .drawer-head h4),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .history-item-header h5),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .summary-card p),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .transcript-card p),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .todo-list ul) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .drawer-head p),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .history-item-header p),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .summary-card span),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .transcript-card span),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .section-label) {
  color: rgba(220, 230, 255, 0.78);
}

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .history-item) {
  background: rgba(25, 25, 29, 0.92);
  border-color: rgba(255, 255, 255, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .summary-card),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .transcript-card) {
  background: rgba(20, 20, 23, 0.92);
}

:global(html[data-auth-theme-mode='dark'] .meeting-drawer .filter-bar .el-input__wrapper),
:global(html[data-auth-theme-mode='dark'] .meeting-drawer .filter-bar .el-select__wrapper) {
  background: rgba(16, 28, 52, 0.92);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
}

@media (max-width: 768px) {
  :global(.meeting-drawer-overlay) {
    top: 60px;
    height: calc(100vh - 60px);
  }

  .drawer-body {
    padding: 18px 14px;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }

  .drawer-head,
  .history-item-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
