<template>
  <div class="knowledge-diff">
    <p>正文新增 {{ value.added }} 行，移除 {{ value.removed }} 行。仅比较文本，不判断政策冲突或哪一版正确。</p>
    <p v-if="value.coarse">行数较多，已使用块级对比：共同首尾保留，中间整块列为移除/新增，并非最小修改行数。</p>
    <table v-if="value.fields.length">
      <caption>资料信息变化</caption>
      <thead><tr><th>项目</th><th>对照版本</th><th>目标版本</th></tr></thead>
      <tbody><tr v-for="field in value.fields" :key="field.field"><th>{{ field.field }}</th><td>{{ field.before || '（空）' }}</td><td>{{ field.after || '（空）' }}</td></tr></tbody>
    </table>
    <p v-else>资料信息未变化。</p>
    <details>
      <summary>展开逐行正文对比（{{ value.lines.length }} 行）</summary>
      <p>行号按原文换行计算，含空行；与检索片段的段号不是同一编号。</p>
      <p>第 {{ page + 1 }} / {{ pageCount }} 页，每页最多 100 行。</p>
      <button :disabled="page === 0" @click="page--">上一页</button>
      <button :disabled="page + 1 >= pageCount" @click="page++">下一页</button>
      <ol>
        <li v-for="(line, index) in visibleLines" :key="page * 100 + index" :class="line.kind">
          <span>{{ line.kind === 'ADDED' ? '＋新增' : line.kind === 'REMOVED' ? '−移除' : '＝保留' }} · 原 {{ line.beforeLine ?? '—' }} / 新 {{ line.afterLine ?? '—' }}</span>
          <pre>{{ line.text || '（空行）' }}</pre>
        </li>
      </ol>
    </details>
  </div>
</template>
<script lang="ts">
import { defineComponent, PropType, ref, computed, watch } from 'vue'
import { KnowledgeComparison } from '@/api/knowledge'
export default defineComponent({
  props: { value: { type: Object as PropType<KnowledgeComparison>, required: true } },
  setup(props) {
    const page = ref(0)
    watch(() => props.value, () => { page.value = 0 })
    const pageCount = computed(() => Math.max(1, Math.ceil(props.value.lines.length / 100)))
    const visibleLines = computed(() => props.value.lines.slice(page.value * 100, (page.value + 1) * 100))
    return { page, pageCount, visibleLines }
  }
})
</script>
<style scoped>
table { width: 100%; border-collapse: collapse; table-layout: fixed; }
th, td { border: 1px solid #ccd6e0; padding: 8px; overflow-wrap: anywhere; text-align: left; }
ol { list-style: none; padding: 0; max-height: 560px; overflow: auto; }
li { border-left: 4px solid #abb5bf; padding: 8px; margin: 4px 0; }
.ADDED { background: #eaf8ee; border-color: #287e3d; }
.REMOVED { background: #fff0ee; border-color: #ab3d2b; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 6px 0; }
summary { cursor: pointer; }
</style>
