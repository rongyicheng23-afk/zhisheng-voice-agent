<template>
  <section class="voice-reply-panel">
    <h3>语音问答</h3>
    <p>识别后可检查提问。回答可采用增量合成或分段合成；实际模式由服务器显示。点击“打断”或重新开始录音可停止旧回答。</p>
    <el-checkbox v-model="knowledgeMode" :disabled="state.busy">资料模式：检索我的有效资料（默认只朗读原文，不调用 DeepSeek）</el-checkbox>
    <el-checkbox v-if="knowledgeMode" v-model="groundedMode" :disabled="state.busy">依据资料归纳：将问题及检索摘录发送给已配置的 DeepSeek</el-checkbox>
    <p v-if="knowledgeMode && groundedMode">先生成归纳并检查引用，再开始朗读，因此等待更久。引用存在不代表结论一定正确，请核对原文。本选项仅在当前页面生效。</p>
    <router-link to="/Knowledge">管理资料库</router-link>
    <el-checkbox v-model="autoReply">本次页面中，录音识别结束后按所选模式自动回答并朗读</el-checkbox>
    <el-input v-model="question" type="textarea" :rows="3" maxlength="4000" placeholder="输入问题，或使用上方识别结果" />
    <div class="reply-actions">
      <el-button :disabled="recording || recognizing || !prompt" @click="question = prompt">使用识别结果</el-button>
      <el-button type="primary" :disabled="recording || recognizing || !question.trim()" @click="ask">发送并朗读</el-button>
      <el-button type="danger" :disabled="!state.busy" @click="reply.stop()">打断回答</el-button>
    </div>
    <p role="status">{{ state.status }}</p>
    <p v-if="state.synthesisMode">语音模式：{{ state.synthesisMode === 'streaming' ? '增量合成' : '分段合成' }}
      <span v-if="state.busy"> · 缓冲约 {{ state.bufferedMs || 0 }} ms · 欠载 {{ state.underflowCount || 0 }} 次</span>
    </p>
    <p v-if="state.firstTokenMs != null">首次文字：{{ state.firstTokenMs }} ms
      <span v-if="state.firstTtsAudioMs != null"> · 首块合成音频：{{ state.firstTtsAudioMs }} ms</span>
      <span v-if="state.firstAudioMs != null"> · 播放启动：{{ state.firstAudioMs }} ms</span>
      （从发送提问开始计时）
    </p>
    <div class="reply-text">{{ state.text }}</div>
    <article v-for="(claim, index) in state.claims || []" :key="'claim-' + index" class="reply-source">
      <h4>归纳 {{ index + 1 }}：{{ claim.text }}</h4>
      <blockquote v-for="ref in claim.evidence" :key="ref.sourceId">
        来源 {{ (state.citations || []).findIndex(source => source.id === ref.sourceId) + 1 }} 原文：{{ ref.quote }}
      </blockquote>
    </article>
    <article v-for="(source, index) in state.citations || []" :key="source.id" class="reply-source"
      :class="{ 'reply-source--active': state.activeCitationIds?.includes(source.id) }">
      <h4>来源 {{ index + 1 }}：{{ source.title }} · {{ source.sourceVersion }}</h4>
      <p v-if="state.activeCitationIds?.includes(source.id)">正在播放引用此来源的段落</p>
      <p>{{ source.publisher }} · 有效期 {{ source.validFrom }} 至 {{ source.validUntil }} · 第 {{ source.paragraph }} 段</p>
      <blockquote>{{ source.quote }}</blockquote>
      <a v-if="/^https?:\/\//.test(source.sourceUrl)" :href="source.sourceUrl" target="_blank" rel="noopener noreferrer">查看发布来源</a>
    </article>
  </section>
</template>
<script lang="ts">
import { defineComponent, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { VoiceReply, ReplyUpdate } from '@/libs/voice-reply'
export default defineComponent({
  props: {
    prompt: { type: String, default: '' },
    completion: { type: Number, default: 0 },
    recording: { type: Boolean, default: false },
    recognizing: { type: Boolean, default: false }
  },
  setup(props) {
    const question = ref('')
    const autoReply = ref(false)
    const knowledgeMode = ref(false)
    const groundedMode = ref(false)
    const answerMode = () => knowledgeMode.value ? (groundedMode.value ? 'grounded' : 'knowledge') : 'general'
    const state = reactive<ReplyUpdate>({ busy: false, text: '', status: '等待提问（分段语音合成）' })
    const reply = new VoiceReply(update => Object.assign(state, update))
    watch(() => props.recording, value => {
      if (value) reply.stop()
    }, { flush: 'sync' })
    watch(() => props.completion, () => {
      if (autoReply.value && props.prompt.trim() && !props.recording && !props.recognizing) {
        question.value = props.prompt
        void reply.start(question.value, answerMode())
      }
    }, { flush: 'post' })
    const ask = () => { void reply.start(question.value, answerMode()) }
    onBeforeUnmount(() => reply.stop(false))
    return { question, autoReply, knowledgeMode, groundedMode, state, reply, ask }
  }
})
</script>
<style scoped>
.voice-reply-panel { margin-top: 24px; padding: 20px; border: 1px solid #ccd6e0; border-radius: 14px; }
.reply-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.reply-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.reply-source { margin-top: 12px; padding: 12px; border: 2px solid #d7dfe7; border-radius: 8px; overflow-wrap: anywhere; }
.reply-source--active { border-color: #176bc1; background: #edf6ff; }
</style>
