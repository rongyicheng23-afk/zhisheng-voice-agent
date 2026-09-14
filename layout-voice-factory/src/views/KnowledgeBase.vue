<template>
  <main class="knowledge-page">
    <FlowBackgroundPanel />
    <section class="knowledge-shell">
      <header class="page-heading">
        <p>RAG KNOWLEDGE BASE</p>
        <h1>资料库与引用依据</h1>
        <span>资料先经过上传、切片和发布索引；只有处于有效期内的已发布资料会进入实时问答。</span>
      </header>

      <section class="knowledge-grid">
        <article class="upload-card">
          <div class="card-heading"><strong>上传资料</strong><small>支持 TXT、MD、DOCX，单文件不超过 10MB</small></div>
          <el-form label-position="top">
            <el-form-item label="资料文件">
              <input class="file-input" type="file" accept=".txt,.md,.docx" @change="selectFile" />
            </el-form-item>
            <el-form-item label="标题"><el-input v-model="form.title" placeholder="例如：产品方案说明" /></el-form-item>
            <el-form-item label="发布单位 / 来源"><el-input v-model="form.source" placeholder="例如：项目组" /></el-form-item>
            <div class="form-row">
              <el-form-item label="版本"><el-input v-model="form.version" placeholder="v1.0" /></el-form-item>
              <el-form-item label="有效期截止"><el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="可不填" /></el-form-item>
            </div>
            <el-button type="primary" :loading="uploading" @click="upload">上传并切片</el-button>
          </el-form>
        </article>

        <article class="search-card">
          <div class="card-heading"><strong>检索预览</strong><small>发布前后都可检查资料内容；实时对话只检索已发布且有效的资料。</small></div>
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

      <section class="documents-card">
        <div class="list-heading">
          <div><strong>资料管理</strong><small>草稿需点击“发布索引”后才会参与回答；到期资料将自动被排除。</small></div>
          <el-button text @click="loadDocuments">刷新</el-button>
        </div>
        <el-table :data="documents" v-loading="loading" empty-text="暂未上传资料">
          <el-table-column prop="title" label="资料" min-width="190" />
          <el-table-column prop="source" label="来源" min-width="120" />
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'warning'" effect="plain">{{ scope.row.status === 'PUBLISHED' ? '已发布' : '待发布' }}</el-tag></template></el-table-column>
          <el-table-column label="有效期" min-width="170"><template #default="scope">{{ formatValidity(scope.row) }}</template></el-table-column>
          <el-table-column prop="chunkCount" label="片段" width="80" />
          <el-table-column label="操作" width="130"><template #default="scope"><el-button v-if="scope.row.status !== 'PUBLISHED'" type="primary" link :loading="publishingId === scope.row.id" @click="publish(scope.row.id)">发布索引</el-button><span v-else class="published-text">可用于问答</span></template></el-table-column>
        </el-table>
      </section>
    </section>
  </main>
</template>

<script lang="ts">
import { defineComponent, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import { KnowledgeCitation, KnowledgeDocument, getKnowledgeDocuments, publishKnowledgeDocument, searchKnowledge, uploadKnowledgeDocument } from '@/api/knowledge'

export default defineComponent({
  name: 'KnowledgeBase',
  components: { FlowBackgroundPanel },
  setup () {
    const documents = ref<KnowledgeDocument[]>([])
    const loading = ref(false)
    const uploading = ref(false)
    const publishingId = ref<number | null>(null)
    const searching = ref(false)
    const searched = ref(false)
    const query = ref('')
    const selectedFile = ref<File | null>(null)
    const searchResults = ref<Array<KnowledgeCitation & { id: string }>>([])
    const form = reactive({ title: '', source: '', version: 'v1.0', validUntil: '' })

    const unwrap = <T,>(response: { code: number, msg: string, data: T }): T => {
      if (response.code !== 200) throw new Error(response.msg || '请求失败')
      return response.data
    }
    const loadDocuments = async () => {
      loading.value = true
      try { documents.value = unwrap(await getKnowledgeDocuments()) } catch (error) { ElMessage.error(error instanceof Error ? error.message : '加载资料失败') } finally { loading.value = false }
    }
    const selectFile = (event: Event) => { selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null }
    const upload = async () => {
      if (!selectedFile.value) { ElMessage.warning('请先选择资料文件'); return }
      const data = new FormData()
      data.append('file', selectedFile.value)
      if (form.title.trim()) data.append('title', form.title.trim())
      if (form.source.trim()) data.append('source', form.source.trim())
      if (form.version.trim()) data.append('version', form.version.trim())
      if (form.validUntil) data.append('validUntil', form.validUntil)
      uploading.value = true
      try {
        unwrap(await uploadKnowledgeDocument(data))
        ElMessage.success('已上传并完成切片，请点击“发布索引”后用于问答')
        selectedFile.value = null
        await loadDocuments()
      } catch (error) { ElMessage.error(error instanceof Error ? error.message : '资料上传失败') } finally { uploading.value = false }
    }
    const publish = async (id: number) => {
      publishingId.value = id
      try { unwrap(await publishKnowledgeDocument(id)); ElMessage.success('索引发布成功，实时问答将引用这份资料'); await loadDocuments() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '发布索引失败') } finally { publishingId.value = null }
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
    onMounted(loadDocuments)
    return { documents, loading, uploading, publishingId, searching, searched, query, form, searchResults, selectFile, upload, publish, search, loadDocuments, formatValidity }
  }
})
</script>

<style scoped>
.knowledge-page { position: relative; min-height: calc(100vh - 60px); padding: 48px 24px 72px; overflow: hidden; }
.knowledge-shell { position: relative; z-index: 1; max-width: 1180px; margin: 0 auto; }
.page-heading { max-width: 700px; margin-bottom: 30px; }.page-heading p { margin: 0 0 8px; color: #476ef7; letter-spacing: .14em; font-size: 12px; font-weight: 700; }.page-heading h1 { margin: 0 0 10px; color: #1c2d50; font-size: 34px; }.page-heading span, small { color: #71819e; line-height: 1.6; }
.knowledge-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }.upload-card, .search-card, .documents-card { border: 1px solid rgba(112, 103, 255, .2); border-radius: 20px; background: rgba(255,255,255,.82); box-shadow: 0 18px 45px rgba(85, 109, 172, .1); padding: 24px; backdrop-filter: blur(14px); }.card-heading, .list-heading { display: flex; flex-direction: column; gap: 5px; margin-bottom: 18px; }.card-heading strong, .list-heading strong { color: #273959; font-size: 18px; }.form-row { display: grid; grid-template-columns: 1fr 1.2fr; gap: 12px; }.file-input { width: 100%; color: #61708b; }.search-actions { margin-top: 12px; }.search-results { margin-top: 14px; max-height: 252px; overflow: auto; display: grid; gap: 10px; }.citation-card { padding: 12px; border-radius: 12px; background: #f6f8ff; border-left: 3px solid #6f67ff; }.citation-card strong, .citation-card small { display: block; }.citation-card p { margin: 7px 0 0; color: #4b5d7d; font-size: 13px; line-height: 1.6; }.documents-card { margin-top: 18px; }.list-heading { flex-direction: row; justify-content: space-between; align-items: center; }.list-heading div { display: flex; flex-direction: column; gap: 4px; }.published-text { color: #2ca56d; font-size: 13px; } @media (max-width: 760px) { .knowledge-page { padding: 28px 14px 44px; }.knowledge-grid { grid-template-columns: 1fr; }.form-row { grid-template-columns: 1fr; } }
</style>
