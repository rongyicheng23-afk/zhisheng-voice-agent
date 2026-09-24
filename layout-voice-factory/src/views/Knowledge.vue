<template>
  <main class="knowledge-page">
    <h1>我的资料库</h1>
    <p>仅自己可见。请录入有权使用的资料；草稿必须手动发布才参与检索。当前为关键词检索和原文展示，不是模型推理或事实核验。</p>
    <p role="status" class="status">{{ status }}</p>
    <section>
      <h2>{{ form.seriesId ? '录入新版本' : '录入资料' }}</h2>
      <p>正文不可覆盖修改。发布新版本会下架同一资料的旧版本，旧版本仍保留供核对。</p>
      <fieldset :disabled="busy">
        <label>标题 <input v-model="form.title" maxlength="160" /></label>
        <label>发布单位 <input v-model="form.publisher" maxlength="160" /></label>
        <label>来源链接（可选） <input v-model="form.sourceUrl" maxlength="1000" placeholder="https://…" /></label>
        <label>版本号 <input v-model="form.sourceVersion" maxlength="80" placeholder="如：2026-09-18版" /></label>
        <label>生效日期 <input v-model="form.validFrom" type="date" /></label>
        <label>截止日期（含当天，北京时间） <input v-model="form.validUntil" type="date" /></label>
        <label>正文（最多20000字；按换行定位原文段落） <textarea v-model="form.content" rows="9" maxlength="20000" /></label>
        <label>也可导入 UTF-8 文本（不超过100 KB，不支持PDF/Word） <input type="file" accept=".txt,text/plain" @change="importText" /></label>
        <button @click="saveDraft">保存为草稿</button> <button @click="newDocument">切换为新资料</button>
      </fieldset>
    </section>
    <section>
      <h2>检索有效资料</h2>
      <p>未发布、未生效、已过期和已下架版本不会命中。匹配不到时不会让模型编造补充。</p>
      <label>问题 <input v-model="question" maxlength="1000" placeholder="例如：报名材料需要什么" /></label>
      <button :disabled="busy || !question.trim()" @click="search">检索</button>
      <p>{{ searchMessage }}</p>
      <article v-for="(hit, index) in hits" :key="hit.id">
        <h3>来源 {{ index + 1 }}：{{ hit.title }} · {{ hit.sourceVersion }}</h3>
        <p>{{ hit.publisher }} · {{ hit.validFrom }} 至 {{ hit.validUntil }} · 第 {{ hit.paragraph }} 段</p>
        <blockquote>{{ hit.quote }}</blockquote>
        <a v-if="/^https?:\/\//.test(hit.sourceUrl)" :href="hit.sourceUrl" target="_blank" rel="noopener noreferrer">查看发布来源</a>
      </article>
      <router-link to="/RealtimeVoice">前往实时语音，勾选“资料模式”可朗读原文</router-link>
    </section>
    <section>
      <h2>版本对比</h2>
      <p>选择同一资料的两个版本，核对正文、来源和有效期变化；对比不会修改任何资料。</p>
      <fieldset :disabled="busy">
        <label>对照版本 <select v-model="beforeId" @change="afterId = ''; comparison = null">
          <option value="">请选择</option><option v-for="doc in documents" :key="doc.id" :value="doc.id">{{ doc.title }} · {{ doc.sourceVersion }}</option>
        </select></label>
        <label>目标版本 <select v-model="afterId" @change="comparison = null">
          <option value="">请选择同一资料的另一版本</option><option v-for="doc in comparisonCandidates" :key="doc.id" :value="doc.id">{{ doc.title }} · {{ doc.sourceVersion }}</option>
        </select></label>
        <button :disabled="!beforeId || !afterId" @click="compareVersions">查看差异</button>
      </fieldset>
      <KnowledgeDiff v-if="comparison" :value="comparison" />
    </section>
    <section v-if="publicationReview" aria-labelledby="publication-title">
      <h2 id="publication-title">发布前核对：{{ publicationReview.candidate.title }} · {{ publicationReview.candidate.sourceVersion }}</h2>
      <p>服务器核对日期：{{ publicationReview.checkedOn }}（北京时间）。{{ publicationReview.message }}</p>
      <p>此操作是你本人确认，不代表系统核验了资料真实性。</p>
      <p>发布单位：{{ publicationReview.candidate.publisher }} · 有效期：{{ publicationReview.candidate.validFrom }} 至 {{ publicationReview.candidate.validUntil }}（含当天）</p>
      <p>来源链接：{{ publicationReview.candidate.sourceUrl || '未填写，请自行核实原始发布来源' }}</p>
      <ul v-if="publicationReview.replaced.length"><li v-for="doc in publicationReview.replaced" :key="doc.id">将下架：{{ doc.title }} · {{ doc.sourceVersion }}（修订 {{ doc.revision }}）</li></ul>
      <KnowledgeDiff v-if="publicationReview.comparison" :value="publicationReview.comparison" />
      <details><summary>核对待发布完整原文</summary><pre>{{ publicationReview.candidate.content }}</pre></details>
      <button :disabled="busy || !publicationReview.eligible" @click="confirmPublication">我已核对，确认发布及替换</button>
      <button :disabled="busy" @click="publicationReview = null">取消发布</button>
    </section>
    <section>
      <h2>资料与版本（{{ documents.length }} / 100）</h2>
      <button :disabled="busy" @click="refresh">刷新列表</button>
      <label>筛选标题、发布单位或版本号 <input v-model="filterText" maxlength="160" /></label>
      <label>状态 <select v-model="filterState"><option value="ALL">全部版本</option><option value="ACTIVE">有效且已发布</option><option value="DRAFT">草稿</option><option value="WITHDRAWN">已下架</option><option value="EXPIRED">已过期（任意发布状态）</option><option value="FUTURE">未生效（任意发布状态）</option></select></label>
      <p>显示 {{ visibleDocuments.length }} 个版本。日期筛选按 {{ checkedDate }} 北京时间计算；跨天请刷新，发布资格由服务器再次核对。</p>
      <p v-if="!documents.length">尚无资料，或尚未登录/初始化资料库。</p>
      <p v-else-if="!visibleDocuments.length">没有符合筛选条件的资料。</p>
      <article v-for="doc in visibleDocuments" :key="doc.id">
        <h3>{{ doc.title }} · {{ doc.sourceVersion }}</h3>
        <p>{{ doc.status === 'DRAFT' ? '草稿' : doc.status === 'PUBLISHED' ? '已发布（检索时仍核对有效期）' : '已下架' }} · 修订 {{ doc.revision }}</p>
        <p>{{ doc.publisher }} · {{ doc.validFrom }} 至 {{ doc.validUntil }}</p>
        <details><summary>查看原文</summary><pre>{{ doc.content }}</pre></details>
        <button :disabled="busy" @click="newVersion(doc)">基于此资料创建新版本</button>
        <button v-if="doc.status !== 'PUBLISHED'" :disabled="busy" @click="previewPublication(doc)">预览发布与替换影响</button>
        <button v-else :disabled="busy" @click="changeStatus(doc, 'WITHDRAWN')">下架</button>
      </article>
    </section>
  </main>
</template>
<script lang="ts">
import { defineComponent, reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { knowledgeRequest, KnowledgeDocument, KnowledgeCitation, KnowledgeComparison, PublicationReview } from '@/api/knowledge'
import KnowledgeDiff from '@/components/KnowledgeDiff.vue'
export default defineComponent({
  components: { KnowledgeDiff },
  setup() {
    const empty = () => ({ title: '', sourceUrl: '', publisher: '', sourceVersion: '', validFrom: '', validUntil: '', content: '', seriesId: '' })
    const form = reactive(empty())
    const busy = ref(false), status = ref(''), question = ref(''), searchMessage = ref('')
    const documents = ref<KnowledgeDocument[]>([]), hits = ref<KnowledgeCitation[]>([])
    const beforeId = ref(''), afterId = ref(''), comparison = ref<KnowledgeComparison | null>(null)
    const publicationReview = ref<PublicationReview | null>(null)
    const filterText = ref(''), filterState = ref('ALL')
    const beijingDate = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
    const checkedDate = ref(beijingDate())
    const comparisonCandidates = computed(() => {
      const before = documents.value.find(d => d.id === beforeId.value)
      return before ? documents.value.filter(d => d.seriesId === before.seriesId && d.id !== before.id) : []
    })
    const visibleDocuments = computed(() => documents.value.filter(doc => {
      const query = filterText.value.trim().toLocaleLowerCase()
      if (query && ![doc.title, doc.publisher, doc.sourceVersion].some(s => s.toLocaleLowerCase().includes(query))) return false
      switch (filterState.value) {
        case 'ACTIVE': return doc.status === 'PUBLISHED' && doc.validFrom <= checkedDate.value && doc.validUntil >= checkedDate.value
        case 'EXPIRED': return doc.validUntil < checkedDate.value
        case 'FUTURE': return doc.validFrom > checkedDate.value
        case 'DRAFT': case 'WITHDRAWN': return doc.status === filterState.value
        default: return true
      }
    }))
    let alive = true, fileAttempt = 0
    let pendingSave: { fingerprint: string, requestId: string } | null = null
    const requestId = () => {
      const bytes = crypto.getRandomValues(new Uint8Array(16))
      bytes[6] = (bytes[6] & 15) | 64
      bytes[8] = (bytes[8] & 63) | 128
      const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
      return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
    }
    const report = (e: unknown) => { if (alive) status.value = e instanceof Error ? e.message : '操作失败' }
    const refresh = async () => {
      if (busy.value) return
      busy.value = true
      try {
        const data = await knowledgeRequest<KnowledgeDocument[]>('/documents')
        if (alive) { documents.value = data; checkedDate.value = beijingDate(); comparison.value = null; publicationReview.value = null }
      }
      catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const newDocument = () => { if (!busy.value) { ++fileAttempt; pendingSave = null; Object.assign(form, empty()) } }
    const newVersion = (doc: KnowledgeDocument) => {
      if (busy.value) return
      pendingSave = null
      ++fileAttempt
      Object.assign(form, { title: doc.title, sourceUrl: doc.sourceUrl, publisher: doc.publisher,
        validFrom: doc.validFrom, validUntil: doc.validUntil, content: doc.content, sourceVersion: '', seriesId: doc.seriesId })
      status.value = '已复制原文，请修改正文、版本和日期；保存后仍需手动发布'
    }
    const saveDraft = async () => {
      if (busy.value) return
      if (![form.title, form.publisher, form.sourceVersion, form.validFrom, form.validUntil, form.content].every(x => x.trim())
          || form.validUntil < form.validFrom) { status.value = '请填写必填项和有效的日期范围'; return }
      busy.value = true
      try {
        const fingerprint = JSON.stringify(form)
        if (!pendingSave || pendingSave.fingerprint !== fingerprint)
          pendingSave = { fingerprint, requestId: requestId() }
        const doc = await knowledgeRequest<KnowledgeDocument>('/documents', { ...form, requestId: pendingSave.requestId })
        if (!alive) return
        ++fileAttempt
        documents.value = [doc, ...documents.value.filter(item => item.id !== doc.id)]
        pendingSave = null
        Object.assign(form, empty())
        status.value = doc.status === 'DRAFT' ? '草稿已保存，核对后点击“发布”才参与检索' : '资料已保存；重试返回已有版本，保留其当前发布状态'
      } catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const changeStatus = async (doc: KnowledgeDocument, next: string) => {
      if (busy.value) return
      busy.value = true
      try {
        await knowledgeRequest('/documents/' + encodeURIComponent(doc.id) + '/status', { status: next, revision: doc.revision })
        if (!alive) return
        hits.value = []; searchMessage.value = ''
        publicationReview.value = null
        status.value = '状态已保存'
        try { documents.value = await knowledgeRequest<KnowledgeDocument[]>('/documents') }
        catch (_) { status.value = '状态已保存，但列表刷新失败，请刷新查看；不要重复提交' }
      } catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const compareVersions = async () => {
      if (busy.value || !comparisonCandidates.value.some(d => d.id === afterId.value)) return
      busy.value = true; comparison.value = null
      try {
        const result = await knowledgeRequest<KnowledgeComparison>('/compare', { beforeId: beforeId.value, afterId: afterId.value })
        if (alive) comparison.value = result
      } catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const previewPublication = async (doc: KnowledgeDocument) => {
      if (busy.value) return
      busy.value = true; publicationReview.value = null
      try {
        const result = await knowledgeRequest<PublicationReview>('/documents/' + encodeURIComponent(doc.id) + '/publication-preview')
        if (alive) { publicationReview.value = result; status.value = '已生成发布预览，请核对上方差异和原文，再确认发布' }
      } catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const confirmPublication = async () => {
      const review = publicationReview.value
      if (busy.value || !review?.eligible) return
      busy.value = true
      try {
        await knowledgeRequest('/documents/' + encodeURIComponent(review.candidate.id) + '/publish-reviewed', { reviewToken: review.reviewToken })
        if (!alive) return
        publicationReview.value = null; hits.value = []; searchMessage.value = ''; status.value = '发布成功，替换影响已确认'
        try {
          const data = await knowledgeRequest<KnowledgeDocument[]>('/documents')
          if (alive) { documents.value = data; checkedDate.value = beijingDate() }
        } catch (_) { if (alive) status.value = '发布已成功，但列表刷新失败；请刷新查看，不要重复提交' }
      } catch (e) { if (alive) publicationReview.value = null; report(e) }
      finally { if (alive) busy.value = false }
    }
    const search = async () => {
      if (busy.value || !question.value.trim()) return
      busy.value = true; hits.value = []; searchMessage.value = ''
      try {
        const result = await knowledgeRequest<{ message: string, citations: KnowledgeCitation[] }>('/search', { question: question.value })
        if (alive) { hits.value = result.citations; searchMessage.value = result.message }
      } catch (e) { report(e) } finally { if (alive) busy.value = false }
    }
    const importText = async (event: Event) => {
      if (busy.value) return
      const attempt = ++fileAttempt
      const file = (event.target as HTMLInputElement).files?.[0]
      if (!file) return
      try {
        if (!file.name.toLowerCase().endsWith('.txt') || file.size > 100 * 1024) throw new Error('仅支持100 KB以内的UTF-8 TXT文件')
        const content = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
        if (!content.trim() || content.length > 20000) throw new Error('文本为空或超过20000字')
        if (alive && attempt === fileAttempt && !busy.value) form.content = content
      } catch (e) { report(e) }
    }
    onMounted(refresh)
    onBeforeUnmount(() => { alive = false; ++fileAttempt })
    return { form, busy, status, question, searchMessage, documents, hits, refresh, newDocument, newVersion, saveDraft, changeStatus, search, importText,
      beforeId, afterId, comparison, comparisonCandidates, compareVersions, publicationReview, previewPublication, confirmPublication,
      filterText, filterState, checkedDate, visibleDocuments }
  }
})
</script>
<style scoped>
.knowledge-page { max-width: 1050px; margin: 30px auto; padding: 0 20px; }
section { border: 1px solid #ccd6e0; border-radius: 14px; padding: 22px; margin: 20px 0; }
fieldset { border: 0; padding: 0; } label { display: block; margin: 12px 0; }
input, textarea, select { display: block; width: 100%; box-sizing: border-box; padding: 8px; border: 1px solid #aab9cc; border-radius: 6px; }
button { padding: 8px 12px; margin: 6px; cursor: pointer; } article { border-top: 1px solid #ddd; padding: 12px 0; }
pre, blockquote { white-space: pre-wrap; overflow-wrap: anywhere; } .status { color: #9d4219; }
</style>
