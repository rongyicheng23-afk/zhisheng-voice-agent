<template>
  <section class="voice-reply-panel">
    <h3>语音问答</h3>
    <p>识别后可检查提问，再发送给 DeepSeek。回答逐段合成播放；点击“打断”或重新开始录音可停止旧回答。</p>
    <el-checkbox v-model="knowledgeMode" :disabled="state.busy">资料模式：只检索并朗读我的有效资料，不调用 DeepSeek</el-checkbox>
    <router-link to="/Knowledge">管理资料库</router-link>
    <el-checkbox v-model="autoReply">本次页面中，录音识别结束后按所选模式自动回答并朗读</el-checkbox>
    <el-input v-model="question" type="textarea" :rows="3" maxlength="4000" placeholder="输入问题，或使用上方识别结果" />
    <div class="reply-actions">
      <el-button :disabled="recording || recognizing || !prompt" @click="question = prompt">使用识别结果</el-button>
      <el-button type="primary" :disabled="recording || recognizing || !question.trim()" @click="ask">发送并朗读</el-button>
      <el-button type="danger" :disabled="!state.busy" @click="reply.stop()">打断回答</el-button>
    </div>
    <p role="status">{{ state.status }}</p>
    <p v-if="state.firstTokenMs != null">首次文字：{{ state.firstTokenMs }} ms
      <span v-if="state.firstAudioMs != null"> · 首次出声：{{ state.firstAudioMs }} ms</span>
      （从发送提问开始计时）
    </p>
    <div class="reply-text">{{ state.text }}</div>
    <article v-for="(source, index) in state.citations || []" :key="source.id" class="reply-source">
      <h4>来源 {{ index + 1 }}：{{ source.title }} · {{ source.sourceVersion }}</h4>
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
    const state = reactive<ReplyUpdate>({ busy: false, text: '', status: '等待提问（分段语音合成）' })
    const reply = new VoiceReply(update => Object.assign(state, update))
    watch(() => props.recording, value => {
      if (value) reply.stop()
    }, { flush: 'sync' })
    watch(() => props.completion, () => {
      if (autoReply.value && props.prompt.trim() && !props.recording && !props.recognizing) {
        question.value = props.prompt
        void reply.start(question.value, knowledgeMode.value ? 'knowledge' : 'general')
      }
    }, { flush: 'post' })
    const ask = () => { void reply.start(question.value, knowledgeMode.value ? 'knowledge' : 'general') }
    onBeforeUnmount(() => reply.stop(false))
    return { question, autoReply, knowledgeMode, state, reply, ask }
  }
})
</script>
<style scoped>
.voice-reply-panel { margin-top: 24px; padding: 20px; border: 1px solid #ccd6e0; border-radius: 14px; }
.reply-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.reply-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
</style>
