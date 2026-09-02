<template>
  <div class="profile-page container-md">
    <div class="profile-card">
      <div class="profile-banner">
        <el-avatar :size="84" class="profile-avatar">
          {{ avatarText }}
        </el-avatar>
        <div>
          <h2>{{ displayName }}</h2>
          <p>{{ currentUser.description || '欢迎来到你的个人主页。' }}</p>
        </div>
      </div>

      <div class="profile-grid">
        <div class="info-item">
          <span>昵称</span>
          <strong>{{ currentUser.nickName || '-' }}</strong>
        </div>
        <div class="info-item">
          <span>账号</span>
          <strong>{{ currentUser.username || '-' }}</strong>
        </div>
        <div class="info-item">
          <span>状态</span>
          <strong>{{ currentUser.isLogin ? '已登录' : '未登录' }}</strong>
        </div>
        <div class="info-item">
          <span>历史记录</span>
          <strong>{{ historySummaryText }}</strong>
        </div>
      </div>
    </div>

    <section class="history-card workbench-card">
      <div class="section-header">
        <div>
          <h3>我的语音工作台</h3>
          <p>把录音转写、语音合成、声纹比对和智能纪要统一收在一个入口里，方便查看最近活跃情况与任务结果。</p>
        </div>
        <el-button type="primary" plain @click="loadWorkbenchData">刷新工作台</el-button>
      </div>

      <div class="stats-grid workbench-stats">
        <div class="stats-item">
          <span>总任务数</span>
          <strong>{{ totalModuleRecords }}</strong>
        </div>
        <div class="stats-item">
          <span>语音合成</span>
          <strong>{{ ttsHistory.length }}</strong>
        </div>
        <div class="stats-item">
          <span>声纹对比</span>
          <strong>{{ voiceprintHistory.length }}</strong>
        </div>
        <div class="stats-item">
          <span>智能纪要</span>
          <strong>{{ meetingStats.totalNotes || meetingHistory.length }}</strong>
        </div>
      </div>

      <div class="module-grid">
        <article v-for="card in workbenchCards" :key="card.key" class="module-card">
          <div class="module-card-head">
            <div>
              <span>{{ card.subtitle }}</span>
              <h4>{{ card.title }}</h4>
            </div>
            <strong>{{ card.count }}</strong>
          </div>
          <p class="module-card-text">{{ card.description }}</p>
          <div class="module-card-meta">
            <span>最近状态：{{ card.latestStatus }}</span>
            <span>最近时间：{{ card.latestTime }}</span>
          </div>
          <div class="module-card-actions">
            <el-button type="primary" plain @click="goTo(card.path)">{{ card.actionText }}</el-button>
            <el-button
              v-if="card.detailPath"
              type="success"
              plain
              @click="goTo(card.detailPath)"
            >
              查看最近结果
            </el-button>
          </div>
        </article>
      </div>

      <div class="activity-card">
        <div class="activity-header">
          <h4>最近动态</h4>
          <span>{{ recentActivities.length }} 条</span>
        </div>
        <el-empty v-if="!currentUser.isLogin" description="请先登录后查看统一工作台" />
        <el-empty v-else-if="!recentActivities.length" description="最近还没有新的语音任务" />
        <div v-else class="activity-list">
          <article v-for="item in recentActivities" :key="item.key" class="activity-item">
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <div class="activity-side">
              <el-tag :type="item.tagType">{{ item.status }}</el-tag>
              <span>{{ item.time }}</span>
            </div>
          </article>
        </div>
      </div>
    </section>

    <div class="history-section">
      <section class="history-card">
        <div class="section-header">
          <div>
            <h3>我的转写历史</h3>
            <p>这里展示数据库里已有索引的使用记录，包含识别文本、状态和对应的 MinIO 对象路径。</p>
          </div>
          <el-button type="primary" plain @click="loadHistoryData">刷新历史</el-button>
        </div>

        <div class="stats-grid">
          <div class="stats-item">
            <span>总记录</span>
            <strong>{{ audioHistory.length }}</strong>
          </div>
          <div class="stats-item">
            <span>成功</span>
            <strong>{{ successCount }}</strong>
          </div>
          <div class="stats-item">
            <span>失败</span>
            <strong>{{ failedCount }}</strong>
          </div>
          <div class="stats-item">
            <span>已存音频</span>
            <strong>{{ storedAudioCount }}</strong>
          </div>
        </div>

        <el-empty v-if="!currentUser.isLogin" description="请先登录后查看个人历史" />
        <el-empty v-else-if="!historyLoading && !audioHistory.length" description="还没有转写历史记录" />

        <div v-else class="history-list">
          <article v-for="item in audioHistory" :key="item.id" class="history-item">
            <div class="history-item-header">
              <div>
                <h4>{{ item.originalFilename }}</h4>
                <p>{{ item.createTime || '-' }}</p>
              </div>
              <el-tag :type="statusTagType(item.status)">{{ item.status || 'UNKNOWN' }}</el-tag>
            </div>

            <div class="meta-grid">
              <div class="meta-cell">
                <span>请求类型</span>
                <strong>{{ item.requestMode || '-' }}</strong>
              </div>
              <div class="meta-cell">
                <span>FunASR 模式</span>
                <strong>{{ item.funasrMode || '-' }}</strong>
              </div>
              <div class="meta-cell">
                <span>文件大小</span>
                <strong>{{ formatFileSize(item.fileSize) }}</strong>
              </div>
              <div class="meta-cell">
                <span>MinIO 路径</span>
                <strong class="mono">{{ item.object || '-' }}</strong>
              </div>
            </div>

            <div class="history-actions">
              <el-button
                type="primary"
                plain
                :disabled="!item.hasAudio"
                @click="previewHistoryAudio(item.id)"
              >
                试听音频
              </el-button>
              <el-button
                type="success"
                plain
                :disabled="!item.hasAudio"
                @click="downloadHistoryAudio(item.id, item.originalFilename)"
              >
                下载音频
              </el-button>
            </div>

            <audio
              v-if="historyAudioUrls[item.id]"
              class="audio-player"
              :src="historyAudioUrls[item.id]"
              controls
              preload="none"
            />

            <div class="text-block">
              <span>识别文本</span>
              <div class="text-content">
                {{ item.transcription || item.errorMessage || '暂无文本结果' }}
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="history-card">
        <div class="section-header">
          <div>
            <h3>我的 MinIO 音频文件</h3>
            <p>这里直接按 `user-audio/{userId}/...` 前缀扫描 MinIO。即使旧文件没有写入历史表，也能从这里看到。</p>
          </div>
          <el-button type="primary" plain @click="loadMinioData">刷新文件</el-button>
        </div>

        <el-empty v-if="!currentUser.isLogin" description="请先登录后查看 MinIO 文件" />
        <el-empty v-else-if="!minioLoading && !minioObjects.length" description="当前用户目录下还没有音频文件" />

        <div v-else class="history-list">
          <article v-for="item in minioObjects" :key="item.object" class="history-item">
            <div class="history-item-header">
              <div>
                <h4>{{ item.filename }}</h4>
                <p>{{ item.lastModified || '-' }}</p>
              </div>
              <el-tag type="info">{{ item.requestMode || 'unknown' }}</el-tag>
            </div>

            <div class="meta-grid">
              <div class="meta-cell">
                <span>Bucket</span>
                <strong>{{ item.bucket }}</strong>
              </div>
              <div class="meta-cell">
                <span>文件大小</span>
                <strong>{{ formatFileSize(item.size) }}</strong>
              </div>
              <div class="meta-cell">
                <span>类型</span>
                <strong>{{ item.contentType || '-' }}</strong>
              </div>
              <div class="meta-cell">
                <span>对象路径</span>
                <strong class="mono">{{ item.object }}</strong>
              </div>
            </div>

            <div class="history-actions">
              <el-button type="primary" plain @click="previewMinioAudio(item.object)">
                试听音频
              </el-button>
              <el-button type="success" plain @click="downloadMinioAudio(item.object, item.filename)">
                下载音频
              </el-button>
            </div>

            <audio
              v-if="minioAudioUrls[item.object]"
              class="audio-player"
              :src="minioAudioUrls[item.object]"
              controls
              preload="none"
            />
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { GlobalDataProps } from '@/store/types'
import {
  AudioHistoryItem,
  MinioAudioObjectItem,
  fetchHistoryAudioBlob,
  fetchMinioAudioBlob,
  getMyAudioHistory,
  getMyMinioAudioObjects
} from '@/api/history'
import { getMyTtsHistory, TtsHistoryItem } from '@/api/tts'
import { getMyVoiceprintHistory, VoiceprintHistoryItem } from '@/api/voiceprint'
import { getMeetingStats, getMyMeetingHistory, MeetingHistoryItem, MeetingStats } from '@/api/meeting'

export default defineComponent({
  name: 'Profile',
  setup () {
    const router = useRouter()
    const store = useStore<GlobalDataProps>()
    const currentUser = computed(() => store.state.user)
    const displayName = computed(() => currentUser.value.nickName || currentUser.value.username || '未命名用户')
    const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
    const audioHistory = ref<AudioHistoryItem[]>([])
    const minioObjects = ref<MinioAudioObjectItem[]>([])
    const ttsHistory = ref<TtsHistoryItem[]>([])
    const voiceprintHistory = ref<VoiceprintHistoryItem[]>([])
    const meetingHistory = ref<MeetingHistoryItem[]>([])
    const meetingStats = ref<MeetingStats>({
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
    const historyLoading = ref(false)
    const minioLoading = ref(false)
    const historyAudioUrls = ref<Record<number, string>>({})
    const minioAudioUrls = ref<Record<string, string>>({})

    const successCount = computed(() => audioHistory.value.filter(item => item.status === 'SUCCESS').length)
    const failedCount = computed(() => audioHistory.value.filter(item => item.status === 'FAILED').length)
    const storedAudioCount = computed(() => audioHistory.value.filter(item => item.hasAudio).length)
    const totalModuleRecords = computed(() => {
      return audioHistory.value.length + ttsHistory.value.length + voiceprintHistory.value.length + meetingHistory.value.length
    })
    const historySummaryText = computed(() => {
      if (!currentUser.value.isLogin) {
        return '请先登录'
      }
      return totalModuleRecords.value ? `${totalModuleRecords.value} 条综合记录` : '暂无记录'
    })
    const workbenchCards = computed(() => {
      const latestTts = ttsHistory.value[0]
      const latestVoiceprint = voiceprintHistory.value[0]
      const latestMeeting = meetingHistory.value[0]
      const latestAsr = audioHistory.value[0]
      return [
        {
          key: 'asr',
          subtitle: '语音识别',
          title: '录音转文字',
          description: '上传音频转文字，支持历史追溯与 MinIO 原音回放。',
          count: audioHistory.value.length,
          latestStatus: normalizeStatusLabel(latestAsr?.status),
          latestTime: latestAsr?.createTime || '暂无',
          path: '/HomeResult',
          detailPath: latestAsr ? '/HomeResult' : '',
          actionText: '进入转写页'
        },
        {
          key: 'tts',
          subtitle: '语音生成',
          title: '文字转语音',
          description: '输入文本并结合参考音频生成目标语音，保留源音频与结果音频。',
          count: ttsHistory.value.length,
          latestStatus: normalizeStatusLabel(latestTts?.status),
          latestTime: latestTts?.createTime || '暂无',
          path: '/TextToVoice',
          detailPath: '',
          actionText: '进入合成页'
        },
        {
          key: 'voiceprint',
          subtitle: '身份分析',
          title: '声纹对比',
          description: '对比两段语音的说话人相似度，适合身份核验与访谈核对。',
          count: voiceprintHistory.value.length,
          latestStatus: normalizeStatusLabel(latestVoiceprint?.status),
          latestTime: latestVoiceprint?.createTime || '暂无',
          path: '/VoicePrintCompare',
          detailPath: '',
          actionText: '进入声纹页'
        },
        {
          key: 'meeting',
          subtitle: '结构化纪要',
          title: '智能纪要',
          description: '自动摘要、提取待办并支持人工校正、时间轴联动和文档导出。',
          count: meetingStats.value.totalNotes || meetingHistory.value.length,
          latestStatus: normalizeStatusLabel(latestMeeting?.status),
          latestTime: latestMeeting?.createTime || '暂无',
          path: '/MeetingNotes',
          detailPath: latestMeeting?.id ? `/MeetingNotes/${latestMeeting.id}` : '',
          actionText: '进入纪要页'
        }
      ]
    })
    const recentActivities = computed(() => {
      const items = [
        ...audioHistory.value.slice(0, 3).map(item => ({
          key: `asr-${item.id}`,
          title: `录音转文字：${item.originalFilename}`,
          description: item.transcription || item.errorMessage || '等待查看识别结果',
          status: normalizeStatusLabel(item.status),
          tagType: statusTagType(item.status),
          time: item.createTime || '-',
          timestamp: parseTime(item.createTime)
        })),
        ...ttsHistory.value.slice(0, 3).map(item => ({
          key: `tts-${item.id}`,
          title: `文字转语音：${item.resultFilename || item.sourceFilename || '语音合成任务'}`,
          description: item.inputText || item.errorMessage || '等待查看合成结果',
          status: normalizeStatusLabel(item.status),
          tagType: statusTagType(item.status),
          time: item.createTime || '-',
          timestamp: parseTime(item.createTime)
        })),
        ...voiceprintHistory.value.slice(0, 3).map(item => ({
          key: `voiceprint-${item.id}`,
          title: `声纹对比：${item.leftFilename || '左音频'} vs ${item.rightFilename || '右音频'}`,
          description: item.resultMessage || item.errorMessage || '等待查看比对结果',
          status: normalizeStatusLabel(item.status),
          tagType: statusTagType(item.status),
          time: item.createTime || '-',
          timestamp: parseTime(item.createTime)
        })),
        ...meetingHistory.value.slice(0, 3).map(item => ({
          key: `meeting-${item.id}`,
          title: `智能纪要：${item.title}`,
          description: item.summaryText || item.errorMessage || '等待生成纪要内容',
          status: normalizeStatusLabel(item.status),
          tagType: statusTagType(item.status),
          time: item.createTime || '-',
          timestamp: parseTime(item.createTime)
        }))
      ]

      return items
        .sort((left, right) => right.timestamp - left.timestamp)
        .slice(0, 8)
    })

    const revokeAllUrls = () => {
      Object.values(historyAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      Object.values(minioAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      historyAudioUrls.value = {}
      minioAudioUrls.value = {}
    }

    const ensureLoggedIn = () => {
      if (!currentUser.value.isLogin) {
        ElMessage.warning('请先登录后查看历史记录')
        router.push('/login')
        return false
      }
      return true
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      ElMessage.error('登录状态已失效，请重新登录')
      router.push('/login')
    }

    const loadHistoryData = async () => {
      if (!ensureLoggedIn()) {
        return
      }
      historyLoading.value = true
      try {
        const res = await getMyAudioHistory()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载转写历史失败')
        }
        audioHistory.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载转写历史失败')
      } finally {
        historyLoading.value = false
      }
    }

    const loadMinioData = async () => {
      if (!ensureLoggedIn()) {
        return
      }
      minioLoading.value = true
      try {
        const res = await getMyMinioAudioObjects()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载 MinIO 文件失败')
        }
        minioObjects.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载 MinIO 文件失败')
      } finally {
        minioLoading.value = false
      }
    }

    const loadWorkbenchData = async () => {
      if (!ensureLoggedIn()) {
        return
      }
      try {
        const [ttsRes, voiceprintRes, meetingRes, meetingStatsRes] = await Promise.all([
          getMyTtsHistory(),
          getMyVoiceprintHistory(),
          getMyMeetingHistory(),
          getMeetingStats()
        ])

        ttsHistory.value = ttsRes.code === 200 && Array.isArray(ttsRes.data) ? ttsRes.data : []
        voiceprintHistory.value = voiceprintRes.code === 200 && Array.isArray(voiceprintRes.data) ? voiceprintRes.data : []
        meetingHistory.value = meetingRes.code === 200 && Array.isArray(meetingRes.data) ? meetingRes.data : []
        if (meetingStatsRes.code === 200 && meetingStatsRes.data) {
          meetingStats.value = meetingStatsRes.data
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载语音工作台失败')
      }
    }

    const saveBlob = (blob: Blob, filename: string) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    }

    const previewHistoryAudio = async (historyId: number) => {
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
        ElMessage.error(error?.message || '加载历史音频失败')
      }
    }

    const downloadHistoryAudio = async (historyId: number, filename: string) => {
      try {
        const blob = await fetchHistoryAudioBlob(historyId)
        saveBlob(blob, filename || `history-${historyId}.wav`)
      } catch (error: any) {
        ElMessage.error(error?.message || '下载历史音频失败')
      }
    }

    const previewMinioAudio = async (object: string) => {
      if (minioAudioUrls.value[object]) {
        return
      }
      try {
        const blob = await fetchMinioAudioBlob(object)
        minioAudioUrls.value = {
          ...minioAudioUrls.value,
          [object]: URL.createObjectURL(blob)
        }
      } catch (error: any) {
        ElMessage.error(error?.message || '加载 MinIO 音频失败')
      }
    }

    const downloadMinioAudio = async (object: string, filename: string) => {
      try {
        const blob = await fetchMinioAudioBlob(object)
        saveBlob(blob, filename || 'audio-file')
      } catch (error: any) {
        ElMessage.error(error?.message || '下载 MinIO 音频失败')
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
      if (status === 'PROCESSING') return 'warning'
      if (status === 'UPLOADED') return 'warning'
      return 'info'
    }

    const normalizeStatusLabel = (status?: string) => {
      if (status === 'SUCCESS') return '处理成功'
      if (status === 'FAILED') return '处理失败'
      if (status === 'PROCESSING') return '处理中'
      if (status === 'UPLOADED') return '已上传'
      if (status === 'PENDING') return '待处理'
      return '暂无'
    }

    const parseTime = (value?: string) => {
      if (!value) {
        return 0
      }
      const timestamp = new Date(value).getTime()
      return Number.isNaN(timestamp) ? 0 : timestamp
    }

    const goTo = (path: string) => {
      if (!path) {
        return
      }
      router.push(path)
    }

    onMounted(async () => {
      if (!currentUser.value.isLogin) {
        return
      }
      await Promise.all([loadHistoryData(), loadMinioData(), loadWorkbenchData()])
    })

    onBeforeUnmount(() => {
      revokeAllUrls()
    })

    return {
      currentUser,
      displayName,
      avatarText,
      audioHistory,
      minioObjects,
      ttsHistory,
      voiceprintHistory,
      meetingHistory,
      meetingStats,
      historyLoading,
      minioLoading,
      historyAudioUrls,
      minioAudioUrls,
      successCount,
      failedCount,
      storedAudioCount,
      totalModuleRecords,
      historySummaryText,
      workbenchCards,
      recentActivities,
      loadHistoryData,
      loadMinioData,
      loadWorkbenchData,
      previewHistoryAudio,
      downloadHistoryAudio,
      previewMinioAudio,
      downloadMinioAudio,
      formatFileSize,
      statusTagType,
      goTo
    }
  }
})
</script>

<style scoped>
.profile-page {
  padding-top: 20px;
  padding-bottom: 36px;
}

.profile-card,
.history-card {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto 24px;
  background: #fff;
  border: 2px dashed #e0e0e0;
  border-radius: 18px;
  padding: 28px;
}

.profile-banner {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 24px;
  border-radius: 18px;
  background: linear-gradient(135deg, #f5f9ff 0%, #edf4ff 100%);
  margin-bottom: 24px;
}

.profile-banner h2,
.section-header h3,
.history-item h4 {
  margin: 0;
  color: #303133;
}

.profile-banner p,
.section-header p,
.history-item-header p {
  margin: 6px 0 0;
  color: #606266;
}

.profile-avatar {
  background: linear-gradient(135deg, #409EFF 0%, #2979ff 100%);
  color: #fff;
}

.profile-grid,
.stats-grid,
.meta-grid {
  display: grid;
  gap: 16px;
}

.profile-grid,
.stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.meta-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.info-item,
.stats-item,
.meta-cell {
  padding: 18px 20px;
  border-radius: 14px;
  background: #fafcff;
  border: 1px solid #ecf5ff;
}

.info-item span,
.stats-item span,
.meta-cell span,
.text-block span {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.info-item strong,
.stats-item strong,
.meta-cell strong {
  font-size: 16px;
  color: #303133;
}

.history-section {
  display: grid;
  gap: 24px;
}

.workbench-card {
  margin-bottom: 24px;
}

.workbench-stats {
  margin-top: 18px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.module-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.module-card {
  padding: 20px;
  border-radius: 18px;
  border: 1px solid #e9eef8;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.module-card-head,
.activity-header,
.activity-item,
.module-card-actions,
.module-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.module-card-head span {
  display: block;
  font-size: 13px;
  color: #7d8aa6;
  margin-bottom: 8px;
}

.module-card-head h4 {
  margin: 0;
  color: #303133;
  font-size: 22px;
}

.module-card-head strong {
  font-size: 26px;
  color: #376cf6;
}

.module-card-text {
  margin: 12px 0 14px;
  color: #5f6f8b;
  line-height: 1.75;
}

.module-card-meta {
  color: #7d8aa6;
  font-size: 13px;
}

.module-card-actions {
  justify-content: flex-start;
  margin-top: 16px;
}

.activity-card {
  margin-top: 22px;
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #e9eef8;
}

.activity-header h4 {
  margin: 0;
  color: #303133;
  font-size: 18px;
}

.activity-header span {
  color: #7d8aa6;
  font-size: 13px;
}

.activity-list {
  margin-top: 16px;
  display: grid;
  gap: 12px;
}

.activity-item {
  padding: 14px 16px;
  border-radius: 14px;
  background: #f7faff;
  border: 1px solid #e7eefc;
  align-items: flex-start;
}

.activity-item strong {
  display: block;
  color: #303133;
  margin-bottom: 6px;
}

.activity-item p {
  margin: 0;
  color: #5f6f8b;
  line-height: 1.6;
}

.activity-side {
  min-width: 110px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.activity-side span {
  color: #7d8aa6;
  font-size: 12px;
}

.section-header,
.history-item-header,
.history-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.history-list {
  margin-top: 20px;
  display: grid;
  gap: 18px;
}

.history-item {
  border: 1px solid #e9eef8;
  border-radius: 16px;
  padding: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.history-actions {
  justify-content: flex-start;
  margin-top: 16px;
}

.audio-player {
  width: 100%;
  margin-top: 14px;
}

.text-block {
  margin-top: 16px;
}

.text-content {
  padding: 14px 16px;
  border-radius: 12px;
  background: #f7faff;
  border: 1px solid #e7eefc;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.mono {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  word-break: break-all;
}

:global(html[data-auth-theme-mode='dark'] .profile-card),
:global(html[data-auth-theme-mode='dark'] .history-card) {
  background: linear-gradient(145deg, rgba(18, 27, 50, 0.94) 0%, rgba(25, 38, 68, 0.92) 100%);
  border-color: rgba(109, 143, 241, 0.24);
  box-shadow: 0 28px 60px rgba(5, 10, 24, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .profile-banner),
:global(html[data-auth-theme-mode='dark'] .history-item),
:global(html[data-auth-theme-mode='dark'] .module-card),
:global(html[data-auth-theme-mode='dark'] .activity-card) {
  background: linear-gradient(135deg, rgba(34, 50, 89, 0.96) 0%, rgba(47, 68, 116, 0.92) 100%);
}

:global(html[data-auth-theme-mode='dark'] .profile-banner h2),
:global(html[data-auth-theme-mode='dark'] .section-header h3),
:global(html[data-auth-theme-mode='dark'] .history-item h4),
:global(html[data-auth-theme-mode='dark'] .module-card-head h4),
:global(html[data-auth-theme-mode='dark'] .activity-header h4),
:global(html[data-auth-theme-mode='dark'] .activity-item strong),
:global(html[data-auth-theme-mode='dark'] .info-item strong),
:global(html[data-auth-theme-mode='dark'] .stats-item strong),
:global(html[data-auth-theme-mode='dark'] .meta-cell strong),
:global(html[data-auth-theme-mode='dark'] .text-content) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .profile-banner p),
:global(html[data-auth-theme-mode='dark'] .section-header p),
:global(html[data-auth-theme-mode='dark'] .history-item-header p),
:global(html[data-auth-theme-mode='dark'] .module-card-text),
:global(html[data-auth-theme-mode='dark'] .module-card-head span),
:global(html[data-auth-theme-mode='dark'] .module-card-meta),
:global(html[data-auth-theme-mode='dark'] .activity-item p),
:global(html[data-auth-theme-mode='dark'] .activity-header span),
:global(html[data-auth-theme-mode='dark'] .activity-side span),
:global(html[data-auth-theme-mode='dark'] .info-item span),
:global(html[data-auth-theme-mode='dark'] .stats-item span),
:global(html[data-auth-theme-mode='dark'] .meta-cell span),
:global(html[data-auth-theme-mode='dark'] .text-block span) {
  color: rgba(220, 230, 255, 0.82);
}

:global(html[data-auth-theme-mode='dark'] .info-item),
:global(html[data-auth-theme-mode='dark'] .stats-item),
:global(html[data-auth-theme-mode='dark'] .meta-cell),
:global(html[data-auth-theme-mode='dark'] .text-content),
:global(html[data-auth-theme-mode='dark'] .activity-item) {
  background: rgba(20, 20, 23, 0.84);
  border-color: rgba(109, 143, 241, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .module-card-head strong) {
  color: #8fb4ff;
}

@media (max-width: 900px) {
  .profile-grid,
  .stats-grid,
  .module-grid,
  .meta-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .profile-grid,
  .stats-grid,
  .module-grid,
  .meta-grid {
    grid-template-columns: 1fr;
  }

  .profile-banner,
  .section-header,
  .history-item-header,
  .history-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
