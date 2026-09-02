<template>
  <flow-background-panel>
    <div class="asr-result-page container-md" style="margin-top: 10px">
      <div class="asr-result-card voice-workbench-card">
        <router-link to="/" class="control-room-back">返回总控台</router-link>
        <div class="hero-section voice-workbench-hero">
          <div class="hero-copy">
            <p class="hero-badge">智能转写</p>
            <h2>录音转文字</h2>
            <p class="hero-text">上传完成后可以逐个转换，也可以批量转换；历史记录会保存在数据库和 MinIO 中。</p>
          </div>
          <div class="hero-status">
            <span class="hero-status-label">当前准备</span>
            <strong>{{ queueTitle }}</strong>
            <small>{{ queueDescription }}</small>
          </div>
        </div>

        <div class="toolbar">
          <div class="hint-list">
            <span class="hint-chip">Java 转发 FunASR</span>
            <span class="hint-chip">MinIO 音频留存</span>
            <span class="hint-chip">历史文本可回填</span>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" plain class="history-btn" @click="openHistoryDrawer">
              历史记录
            </el-button>
            <el-button type="primary" class="convert-btn" :disabled="!tableData.length" @click="handleClick">
              开始转换
            </el-button>
          </div>
        </div>

        <div class="table-section">
          <div class="section-head">
            <div>
              <h4>当前待转换文件</h4>
              <p>上传新音频后会出现在这里，历史记录可以随时从按钮里打开查看。</p>
            </div>
            <el-button class="clear-btn" @click="handleResetUpload">重新上传</el-button>
          </div>

          <empty-state v-if="!tableData.length" type="upload" />

          <el-table v-else :data="tableData" style="width: 100%" size="large" class="file-convert-table">
            <el-table-column prop="name" label="文件名" align="center" />
            <el-table-column prop="size" label="大小" align="center">
              <template #default="{ row }">
                {{ formatFileSize(row.size) }}
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" align="center" />
            <el-table-column label="操作" align="center" width="220">
              <template #default="{ row }">
                <el-button type="primary" size="small" class="convert-action" @click="handleTransform(row.name)">转换</el-button>
                <el-button type="danger" size="small" class="delete-action" @click="handleDelete(row.name)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="transcription-area" v-if="conversionText">
          <div class="result-header">
            <div>
              <span class="result-label">转换结果</span>
              <h4>本次转写文本</h4>
            </div>
          </div>
          <div class="transcription-content">
            <div class="transcription-text">
              {{ conversionText }}
            </div>
            <div class="transcription-actions">
              <el-button type="success" @click="copyText">
                <el-icon class="el-icon--left"><DocumentCopy /></el-icon>
                复制文本
              </el-button>
              <el-button type="warning" @click="clearText">
                <el-icon class="el-icon--left"><Delete /></el-icon>
                清空文本
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </flow-background-panel>

  <asr-history-drawer
    v-model="historyDrawerVisible"
    :show-use-text-action="true"
    @use-text="handleUseHistoryText"
  />
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElLoading, ElMessage } from 'element-plus'
import { DocumentCopy, Delete } from '@element-plus/icons-vue'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import AsrHistoryDrawer from '@/components/AsrHistoryDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'
import store from '@/store'
import { uploadFile } from '@/api/asr'
import type { AudioHistoryItem } from '@/api/history'
import { useAudioUploadState } from '@/state/audio-upload-state'

export default defineComponent({
  name: 'HomeResult',
  components: {
    FlowBackgroundPanel,
    AsrHistoryDrawer,
    EmptyState,
    DocumentCopy,
    Delete
  },
  setup () {
    const router = useRouter()
    const {
      fileMetadata,
      uploadFiles,
      conversionText,
      setConversionResult,
      getRawFileByName,
      removeFileByName,
      clearFiles
    } = useAudioUploadState()

    const historyDrawerVisible = ref(false)
    const tableData = computed(() => fileMetadata.value)
    const queueTitle = computed(() => tableData.value.length ? `${tableData.value.length} 个文件待转换` : '等待上传音频')
    const queueDescription = computed(() => tableData.value.length ? '可以逐个试转，也可以直接批量完成。' : '回到上传页添加音频后，这里会显示待处理队列。')

    const ensureLoggedIn = () => {
      if (!store.getters.isLoggedIn) {
        ElMessage.warning('请先登录后再使用语音转文字')
        router.push('/login')
        return false
      }
      return true
    }

    const openHistoryDrawer = () => {
      if (!ensureLoggedIn()) {
        return
      }
      historyDrawerVisible.value = true
    }

    const convertByName = async (fileName: string) => {
      if (!ensureLoggedIn()) {
        return null
      }

      const rawFile = getRawFileByName(fileName)
      if (!rawFile) {
        throw new Error('当前页面没有原始音频数据，请重新上传后再转换')
      }

      const res = await uploadFile({
        file: rawFile,
        batchSizeS: 200,
        hotword: ''
      })

      if (res.code !== 200) {
        throw new Error(res.msg || '语音转写失败')
      }

      return {
        historyId: res.data?.historyId || null,
        transcription: res.data?.transcription || ''
      }
    }

    const handleClick = async () => {
      if (!tableData.value.length) {
        ElMessage.warning('当前没有待转换文件，你可以手动打开历史记录查看过往内容')
        return
      }

      const loading = ElLoading.service({
        lock: true,
        text: '批量转换中...',
        background: 'rgba(0, 0, 0, 0.7)'
      })

      try {
        const resultBlocks: string[] = []
        let latestHistoryId: number | null = null

        for (const item of tableData.value) {
          const result = await convertByName(item.name)
          if (!result) {
            continue
          }
          latestHistoryId = result.historyId
          if (result.transcription) {
            resultBlocks.push(`${item.name}\n${result.transcription}`)
          }
        }

        setConversionResult(resultBlocks.join('\n\n'), latestHistoryId)
        ElMessage.success('转换完成')
      } catch (error: any) {
        ElMessage.error(error?.message || '转换失败')
      } finally {
        loading.close()
      }
    }

    const handleTransform = async (fileName: string) => {
      const loading = ElLoading.service({
        lock: true,
        text: '转换中...',
        background: 'rgba(0, 0, 0, 0.7)'
      })

      try {
        const result = await convertByName(fileName)
        if (!result) {
          return
        }
        setConversionResult(result.transcription, result.historyId)
        ElMessage.success('转换成功')
      } catch (error: any) {
        ElMessage.error(error?.message || '转换失败')
      } finally {
        loading.close()
      }
    }

    const handleDelete = (fileName: string) => {
      removeFileByName(fileName)
      if (!fileMetadata.value.length) {
        ElMessage.info('当前没有待转换文件，你可以查看历史记录或重新上传')
      }
    }

    const handleResetUpload = () => {
      clearFiles()
      router.push('/asr')
    }

    const copyText = async () => {
      try {
        await navigator.clipboard.writeText(conversionText.value)
        ElMessage.success('文本已复制到剪贴板')
      } catch (error) {
        ElMessage.error('复制失败，请手动复制')
      }
    }

    const clearText = () => {
      setConversionResult('')
      ElMessage.success('文本已清空')
    }

    const handleUseHistoryText = (item: AudioHistoryItem) => {
      setConversionResult(item.transcription || item.errorMessage || '', item.id)
      ElMessage.success('已加载历史文本')
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

    onMounted(() => {
      if (fileMetadata.value.length > 0 && uploadFiles.value.length === 0) {
        ElMessage.warning('检测到页面刷新，原始音频未保留，请重新上传后再转换')
      }
    })

    return {
      tableData,
      queueTitle,
      queueDescription,
      conversionText,
      historyDrawerVisible,
      openHistoryDrawer,
      handleClick,
      handleTransform,
      handleDelete,
      handleResetUpload,
      copyText,
      clearText,
      handleUseHistoryText,
      formatFileSize
    }
  }
})
</script>

<style scoped>
.asr-result-page {
  padding-bottom: 24px;
}

.asr-result-card {
  max-width: 1040px;
  margin: 0 auto;
  border: 1px solid rgba(148, 128, 238, 0.2);
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.96) 0%, rgba(247, 250, 255, 0.94) 100%);
  padding: 28px;
  box-shadow: 0 22px 48px rgba(30, 61, 122, 0.08), inset 0 0 0 1px rgba(148, 128, 238, 0.06);
}

.hero-section {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f5f9ff 0%, #eef4ff 100%);
}

.hero-copy {
  max-width: 640px;
}

.hero-badge {
  margin: 0 0 8px;
  color: #7c5cff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.hero-section h2 {
  margin: 0 0 10px;
  color: #27324a;
  font-size: 34px;
}

.hero-text {
  margin: 0;
  color: #5f6c84;
  line-height: 1.7;
}

.hero-status {
  min-width: 230px;
  border-radius: 18px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-shadow: 0 16px 34px rgba(106, 79, 224, 0.22);
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
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef4ff;
  color: #5270a8;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.history-btn,
.convert-btn {
  min-width: 140px;
  height: 46px;
  border-radius: 999px;
}

.convert-btn {
  border: none;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  box-shadow: 0 14px 30px rgba(106, 79, 224, 0.22);
}

.table-section,
.transcription-area {
  margin-bottom: 20px;
  padding: 24px;
  background: #fbfdff;
  border-radius: 20px;
  border: 1px solid #e8f0ff;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-head h4 {
  margin: 0;
  color: #33415c;
  font-size: 20px;
}

.section-head p {
  margin: 8px 0 0;
  color: #7d8ba6;
  font-size: 14px;
}

.file-convert-table {
  border-radius: 16px;
  overflow: hidden;
}

.file-convert-table ::v-deep .el-table__header-wrapper th {
  background-color: #f5f9ff !important;
  font-weight: 600;
  color: #5b6c87;
  border-bottom: 1px solid #e1ebff;
}

.file-convert-table ::v-deep .el-table__row {
  transition: background-color 0.3s;
}

.file-convert-table ::v-deep .el-table__row:hover {
  background-color: #f7fbff !important;
}

.convert-action {
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  border: none;
  color: white;
}

.convert-action:hover {
  opacity: 0.92;
}

.delete-action {
  background-color: #f56c6c;
  border-color: #f56c6c;
  color: white;
}

.delete-action:hover {
  background-color: #f78989;
  border-color: #f78989;
}

.clear-btn {
  color: #f56c6c;
  border-color: #f56c6c;
}

.clear-btn:hover {
  color: #fff;
  background-color: #f56c6c;
}

.transcription-content {
  max-width: 100%;
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

.transcription-text {
  min-height: 120px;
  max-height: 400px;
  overflow-y: auto;
  padding: 18px;
  background: #f7faff;
  border: 1px solid #e4ecff;
  border-radius: 16px;
  color: #303133;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.transcription-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

:global(html[data-auth-theme-mode='dark'] .table-section),
:global(html[data-auth-theme-mode='dark'] .transcription-area) {
  background: linear-gradient(145deg, rgba(14, 23, 43, 0.92) 0%, rgba(25, 25, 29, 0.9) 100%);
  border-color: rgba(140, 110, 245, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.02);
}

:global(html[data-auth-theme-mode='dark'] .asr-result-card) {
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
:global(html[data-auth-theme-mode='dark'] .section-head h4),
:global(html[data-auth-theme-mode='dark'] .result-header h4),
:global(html[data-auth-theme-mode='dark'] .transcription-text) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .hero-text),
:global(html[data-auth-theme-mode='dark'] .section-head p),
:global(html[data-auth-theme-mode='dark'] .result-label) {
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
  color: #f4f7ff;
}

:global(html[data-auth-theme-mode='dark'] .transcription-text) {
  background: rgba(18, 18, 21, 0.94);
  border-color: rgba(138, 112, 236, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .clear-btn) {
  background: rgba(25, 25, 29, 0.92);
  border-color: rgba(255, 126, 126, 0.48);
  color: #ffb4b4;
}

:global(html[data-auth-theme-mode='dark'] .clear-btn:hover) {
  background: rgba(126, 45, 57, 0.92);
  border-color: rgba(255, 126, 126, 0.64);
  color: #fff2f2;
}

:global(html[data-auth-theme-mode='dark'] .file-convert-table) ::v-deep(.el-table) {
  --el-table-tr-bg-color: rgba(20, 20, 23, 0.84);
  --el-table-bg-color: rgba(20, 20, 23, 0.84);
  --el-table-border-color: rgba(138, 112, 236, 0.16);
  --el-table-header-bg-color: rgba(25, 25, 29, 0.96);
  --el-table-header-text-color: #c7d7fb;
  --el-table-text-color: #e8eefc;
}

:global(html[data-auth-theme-mode='dark'] .file-convert-table) ::v-deep(.el-table th.el-table__cell) {
  background: rgba(25, 25, 29, 0.96) !important;
}

:global(html[data-auth-theme-mode='dark'] .file-convert-table) ::v-deep(.el-table tr) {
  background-color: rgba(20, 20, 23, 0.84) !important;
}

:global(html[data-auth-theme-mode='dark'] .file-convert-table) ::v-deep(.el-table__row:hover > td.el-table__cell) {
  background: rgba(31, 31, 35, 0.92) !important;
}

@media (max-width: 1024px) {
  .hero-section,
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .section-head,
  .toolbar-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .transcription-actions {
    justify-content: flex-start;
  }

  .history-btn,
  .convert-btn {
    width: 100%;
  }
}
</style>
