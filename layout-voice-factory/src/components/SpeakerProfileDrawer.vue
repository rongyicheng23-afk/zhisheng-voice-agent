<template>
  <el-drawer
    v-model="drawerVisible"
    direction="ltr"
    size="520px"
    :with-header="false"
    append-to-body
    :z-index="2600"
    modal-class="speaker-drawer-overlay"
    body-class="speaker-drawer-body-shell"
    class="speaker-drawer"
  >
    <div class="drawer-body" v-loading="profileLoading">
      <div class="drawer-head">
        <div>
          <h4>发言人档案</h4>
          <p>先为会议或课堂中的固定发言人注册样本音频，后续纪要生成时可以基于这些样本做发言人匹配。</p>
        </div>
        <div class="drawer-head-actions">
          <el-button type="primary" plain @click="loadProfileData">刷新列表</el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon class="el-icon--left"><Plus /></el-icon>
            新增档案
          </el-button>
        </div>
      </div>

      <empty-state v-if="!profileLoading && !profileList.length" type="speaker" />

      <div v-else class="profile-list">
        <article v-for="item in profileList" :key="item.id" class="profile-item">
          <div class="profile-item-header">
            <div>
              <h5>{{ item.speakerName }}</h5>
              <p>{{ item.createTime || '-' }}</p>
            </div>
            <el-tag type="info">{{ item.speakerRole || '未设置角色' }}</el-tag>
          </div>

          <div class="profile-meta-grid">
            <div class="meta-card">
              <span>发言人名称</span>
              <strong>{{ item.speakerName }}</strong>
            </div>
            <div class="meta-card">
              <span>文件大小</span>
              <strong>{{ formatFileSize(item.sampleFileSize) }}</strong>
            </div>
          </div>

          <div class="sample-card">
            <span>样本音频</span>
            <strong>{{ item.sampleFilename || '-' }}</strong>
            <small class="mono">{{ item.sampleObject || '-' }}</small>
          </div>

          <div class="profile-actions">
            <el-button type="primary" plain :disabled="!item.hasSampleAudio" @click="previewAudio(item.id)">
              试听样本
            </el-button>
            <el-button type="success" plain :disabled="!item.hasSampleAudio" @click="downloadAudio(item.id, item.sampleFilename)">
              下载样本
            </el-button>
            <el-button type="danger" plain @click="removeProfile(item.id, item.speakerName)">
              删除档案
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

    <el-dialog
      v-model="createDialogVisible"
      title="新增发言人档案"
      width="460px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="发言人名称" required>
          <el-input v-model="speakerForm.speakerName" maxlength="64" placeholder="例如：张老师、学生A、主持人" />
        </el-form-item>
        <el-form-item label="发言人角色">
          <el-input v-model="speakerForm.speakerRole" maxlength="64" placeholder="例如：教师、学生、主持人，可选" />
        </el-form-item>
        <el-form-item label="样本音频" required>
          <el-upload
            ref="sampleUploadRef"
            class="sample-upload"
            drag
            :auto-upload="false"
            :show-file-list="false"
            accept=".wav,.mp3,.ogg,.flac,.m4a"
            :limit="1"
            :on-change="handleSampleFileChange"
          >
            <el-icon class="upload-icon"><Microphone /></el-icon>
            <div class="upload-title">上传发言人样本音频</div>
            <div class="upload-tip">建议选择 3 到 20 秒的人声音频，语音清晰、背景噪音小</div>
          </el-upload>
          <div class="sample-file-name" :class="{ 'sample-file-name--empty': !speakerForm.sampleFileName }">
            {{ speakerForm.sampleFileName || '尚未选择样本音频' }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeCreateDialog">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitProfile">
            {{ submitLoading ? '保存中...' : '保存档案' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script lang="ts">
import { computed, defineComponent, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { Microphone, Plus } from '@element-plus/icons-vue'
import store from '@/store'
import {
  deleteSpeakerProfile,
  fetchSpeakerSampleAudioBlob,
  getMySpeakerProfiles,
  registerSpeakerProfile,
  SpeakerProfileItem
} from '@/api/speaker'
import EmptyState from '@/components/EmptyState.vue'

export default defineComponent({
  name: 'SpeakerProfileDrawer',
  components: {
    Microphone,
    Plus,
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
    const router = useRouter()
    const profileList = ref<SpeakerProfileItem[]>([])
    const profileLoading = ref(false)
    const submitLoading = ref(false)
    const createDialogVisible = ref(false)
    const sampleUploadRef = ref<UploadInstance>()
    const audioUrls = ref<Record<number, string>>({})

    const speakerForm = reactive({
      speakerName: '',
      speakerRole: '',
      sampleFile: null as File | null,
      sampleFileName: ''
    })

    const drawerVisible = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const getAuthToken = () => store.state.token || localStorage.getItem('token') || sessionStorage.getItem('token') || ''

    const hasSpeakerAuth = () => Boolean(Number(store.state.user?._id) && store.state.user?.isLogin && getAuthToken())

    const revokeAudioUrls = () => {
      Object.values(audioUrls.value).forEach(url => URL.revokeObjectURL(url))
      audioUrls.value = {}
    }

    const closeDrawer = () => {
      drawerVisible.value = false
    }

    const closeCreateDialog = () => {
      createDialogVisible.value = false
      speakerForm.speakerName = ''
      speakerForm.speakerRole = ''
      speakerForm.sampleFile = null
      speakerForm.sampleFileName = ''
      sampleUploadRef.value?.clearFiles()
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      closeCreateDialog()
      closeDrawer()
      ElMessage.error('登录状态已失效，请重新登录后再管理发言人档案')
      router.push('/login')
    }

    const ensureSpeakerAccess = (showMessage = true) => {
      if (hasSpeakerAuth()) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再管理发言人档案')
      }
      closeCreateDialog()
      closeDrawer()
      router.push('/login')
      return false
    }

    const loadProfileData = async () => {
      if (!ensureSpeakerAccess()) {
        return
      }
      profileLoading.value = true
      try {
        const res = await getMySpeakerProfiles()
        if (res.code !== 200) {
          throw new Error(res.msg || '加载发言人档案失败')
        }
        profileList.value = Array.isArray(res.data) ? res.data : []
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载发言人档案失败')
      } finally {
        profileLoading.value = false
      }
    }

    const openCreateDialog = () => {
      if (!ensureSpeakerAccess()) {
        return
      }
      closeCreateDialog()
      createDialogVisible.value = true
    }

    const handleSampleFileChange = (file: UploadFile) => {
      const raw = file.raw
      if (!raw) {
        return
      }
      if (raw.size > 50 * 1024 * 1024) {
        ElMessage.error('样本音频大小不能超过 50MB')
        sampleUploadRef.value?.clearFiles()
        return
      }
      speakerForm.sampleFile = raw
      speakerForm.sampleFileName = raw.name
      sampleUploadRef.value?.clearFiles()
    }

    const submitProfile = async () => {
      if (!ensureSpeakerAccess()) {
        return
      }
      if (!speakerForm.speakerName.trim()) {
        ElMessage.warning('请输入发言人名称')
        return
      }
      if (!speakerForm.sampleFile) {
        ElMessage.warning('请先上传样本音频')
        return
      }

      try {
        submitLoading.value = true
        const formData = new FormData()
        formData.append('speakerName', speakerForm.speakerName.trim())
        if (speakerForm.speakerRole.trim()) {
          formData.append('speakerRole', speakerForm.speakerRole.trim())
        }
        formData.append('sampleAudio', speakerForm.sampleFile)

        const res = await registerSpeakerProfile(formData)
        if (res.code !== 200) {
          throw new Error(res.msg || '保存发言人档案失败')
        }
        ElMessage.success('发言人档案已保存')
        closeCreateDialog()
        await loadProfileData()
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '保存发言人档案失败')
      } finally {
        submitLoading.value = false
      }
    }

    const previewAudio = async (profileId: number) => {
      if (!ensureSpeakerAccess()) {
        return
      }
      if (audioUrls.value[profileId]) {
        return
      }
      try {
        const blob = await fetchSpeakerSampleAudioBlob(profileId)
        audioUrls.value = { ...audioUrls.value, [profileId]: URL.createObjectURL(blob) }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载样本音频失败')
      }
    }

    const downloadAudio = async (profileId: number, filename?: string) => {
      if (!ensureSpeakerAccess()) {
        return
      }
      try {
        const blob = await fetchSpeakerSampleAudioBlob(profileId)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename || `speaker-sample-${profileId}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载样本音频失败')
      }
    }

    const removeProfile = async (profileId: number, speakerName: string) => {
      if (!ensureSpeakerAccess()) {
        return
      }
      try {
        await ElMessageBox.confirm(
          `确认删除发言人档案“${speakerName}”吗？后续会议纪要将不能再直接匹配这个样本。`,
          '删除确认',
          {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )
        const res = await deleteSpeakerProfile(profileId)
        if (res.code !== 200) {
          throw new Error(res.msg || '删除发言人档案失败')
        }
        if (audioUrls.value[profileId]) {
          URL.revokeObjectURL(audioUrls.value[profileId])
          const nextUrls = { ...audioUrls.value }
          delete nextUrls[profileId]
          audioUrls.value = nextUrls
        }
        ElMessage.success('发言人档案已删除')
        await loadProfileData()
      } catch (error: any) {
        if (error === 'cancel') {
          return
        }
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '删除发言人档案失败')
      }
    }

    const formatFileSize = (size?: number) => {
      if (!size || size <= 0) return '0 B'
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(2)} MB`
    }

    watch(
      () => props.modelValue,
      async visible => {
        if (!visible) {
          return
        }
        await loadProfileData()
      }
    )

    onBeforeUnmount(() => {
      revokeAudioUrls()
    })

    return {
      drawerVisible,
      profileList,
      profileLoading,
      submitLoading,
      createDialogVisible,
      sampleUploadRef,
      speakerForm,
      audioUrls,
      loadProfileData,
      openCreateDialog,
      closeCreateDialog,
      handleSampleFileChange,
      submitProfile,
      previewAudio,
      downloadAudio,
      removeProfile,
      formatFileSize
    }
  }
})
</script>

<style scoped>
:global(.speaker-drawer-overlay) {
  top: 60px;
  height: calc(100vh - 60px);
}

:global(.speaker-drawer-body-shell) {
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

.drawer-head-actions {
  display: flex;
  gap: 10px;
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

.profile-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-item {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(121, 154, 255, 0.18);
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 16px 36px rgba(72, 113, 197, 0.12);
}

.profile-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.profile-item-header h5 {
  margin: 0;
  font-size: 16px;
  color: #223164;
}

.profile-item-header p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #7c8ab2;
}

.profile-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.meta-card,
.sample-card {
  background: #f5f8ff;
  border-radius: 12px;
  padding: 12px 14px;
}

.meta-card span,
.sample-card span {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 6px;
}

.meta-card strong,
.sample-card strong {
  color: #223164;
  font-size: 16px;
  word-break: break-word;
}

.sample-card {
  margin-top: 12px;
}

.sample-card small {
  display: block;
  margin-top: 10px;
  color: #223164;
  line-height: 1.7;
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.audio-player {
  width: 100%;
  margin-top: 12px;
}

.sample-upload :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 180px;
  border-radius: 14px;
  border: 1px solid rgba(122, 152, 243, 0.22);
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82), inset 0 0 0 1px rgba(122, 152, 243, 0.08);
}

.upload-icon {
  font-size: 34px;
  color: #4b7dff;
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

.sample-file-name {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f3f7ff;
  color: #395180;
  font-size: 14px;
}

.sample-file-name--empty {
  color: #95a2bb;
}

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  word-break: break-all;
}

:global(html[data-auth-theme-mode='dark'] .speaker-drawer .drawer-body) {
  background: linear-gradient(180deg, rgba(9, 16, 31, 0.98) 0%, rgba(12, 21, 41, 0.98) 100%);
}

:global(html[data-auth-theme-mode='dark'] .drawer-head h4),
:global(html[data-auth-theme-mode='dark'] .profile-item-header h5),
:global(html[data-auth-theme-mode='dark'] .meta-card strong),
:global(html[data-auth-theme-mode='dark'] .sample-card strong),
:global(html[data-auth-theme-mode='dark'] .sample-card small),
:global(html[data-auth-theme-mode='dark'] .upload-title) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .drawer-head p),
:global(html[data-auth-theme-mode='dark'] .profile-item-header p),
:global(html[data-auth-theme-mode='dark'] .meta-card span),
:global(html[data-auth-theme-mode='dark'] .sample-card span),
:global(html[data-auth-theme-mode='dark'] .upload-tip) {
  color: rgba(220, 230, 255, 0.78);
}

:global(html[data-auth-theme-mode='dark'] .profile-item) {
  background: rgba(25, 25, 29, 0.92);
  border-color: rgba(255, 255, 255, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .meta-card),
:global(html[data-auth-theme-mode='dark'] .sample-card),
:global(html[data-auth-theme-mode='dark'] .sample-file-name) {
  background: rgba(20, 20, 23, 0.92);
  color: #d3e0ff;
}

:global(html[data-auth-theme-mode='dark'] .sample-file-name--empty) {
  color: rgba(208, 220, 255, 0.48);
}

:global(html[data-auth-theme-mode='dark'] .sample-upload .el-upload-dragger) {
  background:
    radial-gradient(circle at 50% 18%, rgba(79, 124, 255, 0.18), transparent 24%),
    linear-gradient(145deg, rgba(18, 18, 21, 0.98) 0%, rgba(22, 22, 26, 0.97) 46%, rgba(20, 33, 61, 0.96) 100%);
  border-color: rgba(120, 154, 255, 0.28);
}

@media (max-width: 768px) {
  :global(.speaker-drawer-overlay) {
    top: 60px;
    height: calc(100vh - 60px);
  }

  .drawer-body {
    padding: 18px 14px;
  }

  .drawer-head,
  .drawer-head-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .profile-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
