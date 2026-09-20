<template>
  <FlowBackgroundPanel>
    <main class="knowledge-page">
      <section class="knowledge-shell">
      <header class="page-heading">
        <p>RAG KNOWLEDGE BASE</p>
        <h1>资料库与引用依据</h1>
        <span>资料依次经历上传、审核、发布和索引；只有你有权限、处于有效期内的已发布资料会进入实时问答。</span>
      </header>

      <section class="knowledge-grid">
        <article v-if="canManageKnowledge" class="upload-card">
          <div class="card-heading"><strong>上传资料</strong><small>支持 TXT、MD、DOCX，单文件不超过 10MB</small></div>
          <el-form label-position="top">
            <el-form-item label="资料文件">
              <input class="file-input" type="file" accept=".txt,.md,.docx" @change="selectFile" />
            </el-form-item>
            <el-form-item label="标题"><el-input v-model="form.title" placeholder="例如：产品方案说明" /></el-form-item>
            <el-form-item label="发布单位 / 来源"><el-input v-model="form.source" placeholder="例如：项目组" /></el-form-item>
            <el-form-item label="可见范围">
              <el-select v-model="form.visibility" style="width: 100%">
                <el-option label="仅自己可见（PRIVATE）" value="PRIVATE" />
                <el-option label="指定成员可见（TEAM）" value="TEAM" />
                <el-option label="已发布后全体用户可见（PUBLIC）" value="PUBLIC" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.visibility === 'TEAM'" label="授权成员用户 ID"><el-input v-model="form.teamMemberIds" placeholder="例如：12, 25（逗号分隔）" /></el-form-item>
            <div class="form-row">
              <el-form-item label="版本"><el-input v-model="form.version" placeholder="v1.0" /></el-form-item>
              <el-form-item label="有效期截止"><el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="可不填" /></el-form-item>
            </div>
            <el-button type="primary" :loading="uploading" @click="upload">上传并切片</el-button>
          </el-form>
        </article>

        <article class="search-card" :class="{ 'search-card--wide': !canManageKnowledge }">
          <div class="card-heading"><strong>检索预览</strong><small>只预览已发布、有效且当前账号可访问的资料片段。</small></div>
          <el-input v-model="query" type="textarea" :rows="4" placeholder="输入一个问题，检查将会被引用的资料片段" />
          <div class="search-actions"><el-button :loading="searching" @click="search">检索资料</el-button></div>
          <div v-if="searchResults.length" class="search-results">
            <article v-for="item in searchResults" :key="item.id" class="citation-card">
              <strong>{{ item.id }} · {{ item.title }}</strong>
              <small>{{ item.source }} · {{ item.version }} · 第 {{ item.chunkNo }} 段</small>
              <p>{{ item.excerpt }}</p>
            </article>
          </div>
          <el-empty v-else-if="searched" description="没有找到可用资料；请先发布资料并确认有效期。" :image-size="58" />
        </article>
      </section>

      <section v-if="canManageKnowledge" class="documents-card">
        <div class="list-heading">
          <div><strong>资料管理</strong><small>本页当前账号即资料管理员：草稿 → 审核 → 发布；下架、过期和未审核资料均不参与默认检索。</small></div>
          <el-button text @click="loadDocuments">刷新</el-button>
        </div>
        <el-table :data="documents" v-loading="loading" empty-text="暂未上传资料">
          <el-table-column prop="title" label="资料" min-width="190" />
          <el-table-column prop="source" label="来源" min-width="120" />
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column label="范围" min-width="155"><template #default="scope"><el-tag effect="plain">{{ visibilityLabel(scope.row.visibility) }}</el-tag></template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="有效期" min-width="170"><template #default="scope">{{ formatValidity(scope.row) }}</template></el-table-column>
          <el-table-column prop="chunkCount" label="片段" width="80" />
          <el-table-column label="操作" min-width="220"><template #default="scope">
            <el-button v-if="scope.row.status === 'DRAFT'" type="primary" link :loading="actionId === scope.row.id" @click="submitReview(scope.row.id)">提交审核</el-button>
            <el-button v-else-if="scope.row.status === 'REVIEW'" type="primary" link :loading="actionId === scope.row.id" @click="approve(scope.row.id)">审核并发布</el-button>
            <template v-else-if="scope.row.status === 'PUBLISHED'">
              <span class="published-text">可用于问答</span>
              <el-button link :loading="actionId === scope.row.id" @click="reindex(scope.row.id)">重新索引</el-button>
              <el-button type="danger" link :loading="actionId === scope.row.id" @click="offline(scope.row.id)">下架</el-button>
            </template>
            <el-button v-else-if="scope.row.status === 'OFFLINE'" type="primary" link :loading="actionId === scope.row.id" @click="restoreDraft(scope.row.id)">恢复为草稿</el-button>
            <span v-else class="published-text">当前状态不可操作</span>
            <el-button link :loading="actionId === scope.row.id" @click="openPermissions(scope.row)">设置范围</el-button>
          </template></el-table-column>
        </el-table>
      </section>

      <el-dialog v-model="permissionDialogVisible" title="设置资料可见范围" width="460px" append-to-body destroy-on-close>
        <p class="dialog-hint">范围由后端检索接口强制校验，前端修改请求参数无法读取未授权资料。</p>
        <el-form label-position="top">
          <el-form-item label="可见范围"><el-select v-model="permissionScope" style="width: 100%"><el-option label="仅上传者（PRIVATE）" value="PRIVATE" /><el-option label="指定成员（TEAM）" value="TEAM" /><el-option label="已发布后全体用户（PUBLIC）" value="PUBLIC" /></el-select></el-form-item>
          <el-form-item v-if="permissionScope === 'TEAM'" label="授权成员用户 ID"><el-input v-model="permissionMembers" placeholder="例如：12, 25（逗号分隔）" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="permissionDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingPermissions" @click="savePermissions">保存</el-button></template>
      </el-dialog>
      </section>
    </main>
  </FlowBackgroundPanel>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import { KnowledgeCitation, KnowledgeDocument, approveKnowledgeDocument, getKnowledgeDocuments, offlineKnowledgeDocument, reindexKnowledgeDocument, restoreKnowledgeDocumentToDraft, searchKnowledge, submitKnowledgeReview, updateKnowledgeDocumentVisibility, uploadKnowledgeDocument } from '@/api/knowledge'
import { getAdminProfile } from '@/api/admin'

export default defineComponent({
  name: 'KnowledgeBase',
  components: { FlowBackgroundPanel },
  setup () {
    const documents = ref<KnowledgeDocument[]>([])
    const loading = ref(false)
    const uploading = ref(false)
    const actionId = ref<number | null>(null)
    const searching = ref(false)
    const searched = ref(false)
    const query = ref('')
    const selectedFile = ref<File | null>(null)
    const canManageKnowledge = ref(false)
    const searchResults = ref<Array<KnowledgeCitation & { id: string }>>([])
    const permissionDialogVisible = ref(false)
    const savingPermissions = ref(false)
    const selectedDocument = ref<KnowledgeDocument | null>(null)
    const permissionScope = ref('PRIVATE')
    const permissionMembers = ref('')
    const form = reactive({ title: '', source: '', version: 'v1.0', validUntil: '', visibility: 'PRIVATE', teamMemberIds: '' })

    const unwrap = <T,>(response: { code: number, msg: string, data: T }): T => {
      if (response.code !== 200) throw new Error(response.msg || '请求失败')
      return response.data
    }
    const loadDocuments = async () => {
      loading.value = true
      try { documents.value = unwrap(await getKnowledgeDocuments()) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '加载资料失败') } finally { loading.value = false }
    }
    const selectFile = (event: Event) => { selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null }
    const parseTeamMemberIds = (value: string) => Array.from(new Set(value.split(',').map(item => Number(item.trim())).filter(item => Number.isInteger(item) && item > 0)))
    const upload = async () => {
      if (!selectedFile.value) { ElMessage.warning('请先选择资料文件'); return }
      const data = new FormData()
      data.append('file', selectedFile.value)
      if (form.title.trim()) data.append('title', form.title.trim())
      if (form.source.trim()) data.append('source', form.source.trim())
      if (form.version.trim()) data.append('version', form.version.trim())
      if (form.validUntil) data.append('validUntil', form.validUntil)
      const memberIds = parseTeamMemberIds(form.teamMemberIds)
      if (form.visibility === 'TEAM' && !memberIds.length) { ElMessage.warning('TEAM 范围至少需要填写一名授权成员用户 ID'); return }
      data.append('visibility', form.visibility)
      memberIds.forEach(id => data.append('teamMemberId', String(id)))
      uploading.value = true
      try {
        unwrap(await uploadKnowledgeDocument(data))
        ElMessage.success('已上传并完成切片，请点击“发布索引”后用于问答')
        selectedFile.value = null
        await loadDocuments()
      } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料上传失败') } finally { uploading.value = false }
    }
    const withAction = async (id: number, request: () => Promise<any>, success: string) => {
      actionId.value = id
      try { unwrap(await request()); ElMessage.success(success); await loadDocuments() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料操作失败') } finally { actionId.value = null }
    }
    const submitReview = (id: number) => withAction(id, () => submitKnowledgeReview(id), '已提交审核；审核通过后才能参与问答')
    const approve = (id: number) => withAction(id, () => approveKnowledgeDocument(id), '审核并发布成功，实时问答将引用这份资料')
    const reindex = (id: number) => withAction(id, () => reindexKnowledgeDocument(id), '重新索引成功')
    const offline = (id: number) => withAction(id, () => offlineKnowledgeDocument(id), '资料已下架，不再参与默认检索')
    const restoreDraft = (id: number) => withAction(id, () => restoreKnowledgeDocumentToDraft(id), '资料已恢复为草稿，请重新提交审核')
    const openPermissions = (document: KnowledgeDocument) => {
      selectedDocument.value = document
      permissionScope.value = document.visibility || 'PRIVATE'
      permissionMembers.value = (document.teamMemberIds || []).join(', ')
      permissionDialogVisible.value = true
    }
    const savePermissions = async () => {
      if (!selectedDocument.value) return
      const members = parseTeamMemberIds(permissionMembers.value)
      if (permissionScope.value === 'TEAM' && !members.length) { ElMessage.warning('TEAM 范围至少需要填写一名授权成员用户 ID'); return }
      savingPermissions.value = true
      try {
        unwrap(await updateKnowledgeDocumentVisibility(selectedDocument.value.id, permissionScope.value, members))
        ElMessage.success('资料可见范围已更新')
        permissionDialogVisible.value = false
        await loadDocuments()
      } catch (error) { ElMessage.error(error instanceof Error ? error.message : '更新可见范围失败') } finally { savingPermissions.value = false }
    }
    const search = async () => {
      if (!query.value.trim()) { ElMessage.warning('请输入要检索的问题'); return }
      searching.value = true; searched.value = true
      try {
        const result = unwrap(await searchKnowledge(query.value.trim()))
        searchResults.value = Object.entries(result.citations || {}).map(([id, citation]) => ({ id, ...citation }))
      } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料检索失败') } finally { searching.value = false }
    }
    const formatValidity = (item: KnowledgeDocument) => item.validUntil ? `至 ${item.validUntil}` : '长期有效'
    const statusLabel = (status: string) => ({ DRAFT: '草稿', REVIEW: '待审核', PUBLISHED: '已发布', OFFLINE: '已下架' }[status] || status)
    const statusType = (status: string) => ({ PUBLISHED: 'success', REVIEW: 'warning', OFFLINE: 'info', DRAFT: 'info' }[status] || 'info')
    const visibilityLabel = (visibility: string) => ({ PRIVATE: '仅自己可见', TEAM: '指定成员', PUBLIC: '全体用户' }[visibility] || '仅自己可见')
    onMounted(async () => {
      try {
        const profile = await getAdminProfile()
        canManageKnowledge.value = profile.code === 200 && profile.data.canManageKnowledge
        if (canManageKnowledge.value) await loadDocuments()
      } catch { canManageKnowledge.value = false }
    })
    return { documents, loading, uploading, actionId, searching, searched, query, form, searchResults, selectFile, upload, submitReview, approve, reindex, offline, restoreDraft, search, loadDocuments, formatValidity, statusLabel, statusType, visibilityLabel, canManageKnowledge, permissionDialogVisible, savingPermissions, permissionScope, permissionMembers, openPermissions, savePermissions }
  }
})
</script>

<style scoped>
.knowledge-page { position: relative; min-height: calc(100vh - 60px); padding: 48px 24px 72px; overflow: hidden; }.dialog-hint { margin: 0 0 16px; color: #71819e; font-size: 13px; line-height: 1.65; }
.knowledge-shell { position: relative; z-index: 1; max-width: 1180px; margin: 0 auto; }
.page-heading { max-width: 700px; margin-bottom: 30px; }.page-heading p { margin: 0 0 8px; color: #476ef7; letter-spacing: .14em; font-size: 12px; font-weight: 700; }.page-heading h1 { margin: 0 0 10px; color: #1c2d50; font-size: 34px; }.page-heading span, small { color: #71819e; line-height: 1.6; }
.knowledge-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }.upload-card, .search-card, .documents-card { border: 1px solid rgba(112, 103, 255, .2); border-radius: 20px; background: rgba(255,255,255,.82); box-shadow: 0 18px 45px rgba(85, 109, 172, .1); padding: 24px; backdrop-filter: blur(14px); }.search-card--wide { grid-column: 1 / -1; }.card-heading, .list-heading { display: flex; flex-direction: column; gap: 5px; margin-bottom: 18px; }.card-heading strong, .list-heading strong { color: #273959; font-size: 18px; }.form-row { display: grid; grid-template-columns: 1fr 1.2fr; gap: 12px; }.file-input { width: 100%; color: #61708b; }.search-actions { margin-top: 12px; }.search-results { margin-top: 14px; max-height: 252px; overflow: auto; display: grid; gap: 10px; }.citation-card { padding: 12px; border-radius: 12px; background: #f6f8ff; border-left: 3px solid #6f67ff; }.citation-card strong, .citation-card small { display: block; }.citation-card p { margin: 7px 0 0; color: #4b5d7d; font-size: 13px; line-height: 1.6; }.documents-card { margin-top: 18px; }.list-heading { flex-direction: row; justify-content: space-between; align-items: center; }.list-heading div { display: flex; flex-direction: column; gap: 4px; }.published-text { color: #2ca56d; font-size: 13px; } @media (max-width: 760px) { .knowledge-page { padding: 28px 14px 44px; }.knowledge-grid { grid-template-columns: 1fr; }.form-row { grid-template-columns: 1fr; } }
</style>
