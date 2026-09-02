<template>
  <div class="empty-state" :class="`empty-state--${type}`">
    <div class="empty-illustration" :aria-hidden="true">
      <span class="empty-glow"></span>
      <span class="empty-orbit empty-orbit--1"></span>
      <span class="empty-orbit empty-orbit--2"></span>
      <span class="empty-spark empty-spark--1"></span>
      <span class="empty-spark empty-spark--2"></span>
      <span class="empty-spark empty-spark--3"></span>

      <!-- ASR / 录音转文字 -->
      <svg v-if="type === 'asr'" class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-asr-card" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#ffffff" stop-opacity="0.96" />
            <stop offset="100%" stop-color="#e9f5e7" stop-opacity="0.94" />
          </linearGradient>
          <linearGradient id="empty-asr-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#84d36a" />
            <stop offset="100%" stop-color="#3fa024" />
          </linearGradient>
        </defs>
        <rect x="32" y="34" width="136" height="96" rx="18" fill="url(#empty-asr-card)" stroke="rgba(98,187,68,0.28)" stroke-width="1.4" />
        <rect x="48" y="56" width="78" height="8" rx="4" fill="rgba(98,187,68,0.32)" />
        <rect x="48" y="72" width="104" height="6" rx="3" fill="rgba(120,140,170,0.22)" />
        <rect x="48" y="86" width="92" height="6" rx="3" fill="rgba(120,140,170,0.18)" />
        <rect x="48" y="100" width="64" height="6" rx="3" fill="rgba(120,140,170,0.16)" />
        <circle cx="148" cy="44" r="22" fill="url(#empty-asr-accent)" opacity="0.18" />
        <circle cx="148" cy="44" r="14" fill="url(#empty-asr-accent)" />
        <path d="M148 38v12M144 41v6M152 41v6" stroke="white" stroke-width="2" stroke-linecap="round" />
      </svg>

      <!-- TTS / 文字转语音 -->
      <svg v-else-if="type === 'tts'" class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-tts-card" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#ffffff" stop-opacity="0.96" />
            <stop offset="100%" stop-color="#e6efff" stop-opacity="0.94" />
          </linearGradient>
          <linearGradient id="empty-tts-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#6aa5ff" />
            <stop offset="100%" stop-color="#2f6cf6" />
          </linearGradient>
        </defs>
        <rect x="28" y="40" width="100" height="80" rx="16" fill="url(#empty-tts-card)" stroke="rgba(59,140,255,0.26)" stroke-width="1.4" />
        <rect x="42" y="58" width="68" height="6" rx="3" fill="rgba(59,140,255,0.4)" />
        <rect x="42" y="72" width="56" height="6" rx="3" fill="rgba(120,140,170,0.22)" />
        <rect x="42" y="86" width="72" height="6" rx="3" fill="rgba(120,140,170,0.18)" />
        <path d="M132 80 L156 64 L156 100 Z" fill="url(#empty-tts-accent)" />
        <path d="M164 70c4 4 4 16 0 20M170 64c8 6 8 26 0 32" stroke="url(#empty-tts-accent)" stroke-width="2.4" stroke-linecap="round" fill="none" />
      </svg>

      <!-- Meeting / 智能纪要 -->
      <svg v-else-if="type === 'meeting'" class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-meeting-card" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#ffffff" stop-opacity="0.96" />
            <stop offset="100%" stop-color="#e3f6f3" stop-opacity="0.94" />
          </linearGradient>
          <linearGradient id="empty-meeting-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#4ed1c4" />
            <stop offset="100%" stop-color="#16978a" />
          </linearGradient>
        </defs>
        <rect x="30" y="32" width="140" height="100" rx="18" fill="url(#empty-meeting-card)" stroke="rgba(34,179,167,0.26)" stroke-width="1.4" />
        <circle cx="58" cy="60" r="10" fill="url(#empty-meeting-accent)" opacity="0.85" />
        <circle cx="86" cy="60" r="10" fill="rgba(34,179,167,0.45)" />
        <circle cx="114" cy="60" r="10" fill="rgba(34,179,167,0.32)" />
        <circle cx="142" cy="60" r="10" fill="rgba(34,179,167,0.22)" />
        <rect x="48" y="88" width="104" height="6" rx="3" fill="rgba(34,179,167,0.35)" />
        <rect x="48" y="102" width="88" height="6" rx="3" fill="rgba(120,140,170,0.22)" />
        <rect x="48" y="116" width="70" height="6" rx="3" fill="rgba(120,140,170,0.18)" />
      </svg>

      <!-- Voiceprint / 声纹对比 -->
      <svg v-else-if="type === 'voiceprint'" class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-vp-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#b896ff" />
            <stop offset="100%" stop-color="#7138ec" />
          </linearGradient>
        </defs>
        <circle cx="100" cy="80" r="58" fill="rgba(143,88,246,0.08)" />
        <circle cx="100" cy="80" r="40" fill="rgba(143,88,246,0.14)" />
        <g stroke="url(#empty-vp-accent)" stroke-width="3" stroke-linecap="round">
          <line x1="64" y1="80" x2="64" y2="80" />
          <line x1="74" y1="68" x2="74" y2="92" />
          <line x1="84" y1="58" x2="84" y2="102" />
          <line x1="94" y1="48" x2="94" y2="112" />
          <line x1="104" y1="58" x2="104" y2="102" />
          <line x1="114" y1="64" x2="114" y2="96" />
          <line x1="124" y1="54" x2="124" y2="106" />
          <line x1="134" y1="70" x2="134" y2="90" />
        </g>
      </svg>

      <!-- Speaker / 发言人档案 -->
      <svg v-else-if="type === 'speaker'" class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-sp-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#4ed1c4" />
            <stop offset="100%" stop-color="#16978a" />
          </linearGradient>
        </defs>
        <rect x="40" y="38" width="120" height="92" rx="18" fill="rgba(255,255,255,0.96)" stroke="rgba(34,179,167,0.28)" stroke-width="1.4" />
        <circle cx="80" cy="76" r="18" fill="url(#empty-sp-accent)" opacity="0.85" />
        <path d="M58 116c4-14 14-22 22-22s18 8 22 22" fill="rgba(34,179,167,0.3)" />
        <rect x="112" y="66" width="38" height="6" rx="3" fill="rgba(34,179,167,0.4)" />
        <rect x="112" y="80" width="30" height="5" rx="2.5" fill="rgba(120,140,170,0.28)" />
        <rect x="112" y="92" width="34" height="5" rx="2.5" fill="rgba(120,140,170,0.22)" />
      </svg>

      <!-- Upload / 未上传 -->
      <svg v-else class="empty-svg" viewBox="0 0 200 160" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="empty-up-accent" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#7fa9ff" />
            <stop offset="100%" stop-color="#3a6cf0" />
          </linearGradient>
        </defs>
        <rect x="40" y="46" width="120" height="80" rx="18" fill="rgba(255,255,255,0.96)" stroke="rgba(75,125,255,0.28)" stroke-width="1.4" stroke-dasharray="6 6" />
        <path d="M100 70v32M88 82l12-12 12 12" stroke="url(#empty-up-accent)" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round" fill="none" />
        <rect x="80" y="108" width="40" height="6" rx="3" fill="rgba(75,125,255,0.32)" />
      </svg>
    </div>

    <h4 class="empty-title">{{ title || defaultTitle }}</h4>
    <p v-if="description || defaultDescription" class="empty-description">
      {{ description || defaultDescription }}
    </p>

    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, PropType } from 'vue'

type EmptyType = 'asr' | 'tts' | 'meeting' | 'voiceprint' | 'speaker' | 'upload'

const TITLE_MAP: Record<EmptyType, string> = {
  asr: '还没有录音转文字记录',
  tts: '还没有文字转语音记录',
  meeting: '还没有纪要历史',
  voiceprint: '还没有声纹对比记录',
  speaker: '还没有发言人档案',
  upload: '当前没有待处理文件'
}

const DESC_MAP: Record<EmptyType, string> = {
  asr: '上传一段音频开始你的第一次转写，结果会自动归档到这里。',
  tts: '输入文本并上传参考音频，生成结果会自动出现在这里。',
  meeting: '上传会议或课堂音频生成第一份纪要，之后可以随时回看。',
  voiceprint: '上传两段音频开始对比，比对结果会保留在这里。',
  speaker: '为常用发言人建一份档案，后续会议可以自动匹配。',
  upload: '把音频文件拖进来或点击上传按钮，开始下一步处理。'
}

export default defineComponent({
  name: 'EmptyState',
  props: {
    type: {
      type: String as PropType<EmptyType>,
      default: 'upload'
    },
    title: { type: String, default: '' },
    description: { type: String, default: '' }
  },
  setup (props) {
    const defaultTitle = computed(() => TITLE_MAP[props.type] || '暂无数据')
    const defaultDescription = computed(() => DESC_MAP[props.type] || '')
    return { defaultTitle, defaultDescription }
  }
})
</script>

<style scoped>
.empty-state {
  --empty-accent: #4b7dff;
  --empty-accent-soft: rgba(75, 125, 255, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 32px 24px 28px;
  gap: 14px;
}

.empty-state--asr {
  --empty-accent: #62bb44;
  --empty-accent-soft: rgba(98, 187, 68, 0.18);
}
.empty-state--tts {
  --empty-accent: #3b8cff;
  --empty-accent-soft: rgba(59, 140, 255, 0.18);
}
.empty-state--meeting,
.empty-state--speaker {
  --empty-accent: #22b3a7;
  --empty-accent-soft: rgba(34, 179, 167, 0.18);
}
.empty-state--voiceprint {
  --empty-accent: #8f58f6;
  --empty-accent-soft: rgba(143, 88, 246, 0.18);
}
.empty-state--upload {
  --empty-accent: #4b7dff;
  --empty-accent-soft: rgba(75, 125, 255, 0.18);
}

.empty-illustration {
  position: relative;
  width: 220px;
  height: 170px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: empty-float 5s ease-in-out infinite;
}

.empty-glow {
  position: absolute;
  inset: 12px 14px 18px 14px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--empty-accent-soft) 0%, transparent 70%);
  filter: blur(6px);
  pointer-events: none;
}

.empty-orbit {
  position: absolute;
  border-radius: 50%;
  border: 1px dashed var(--empty-accent-soft);
  opacity: 0.7;
  pointer-events: none;
}

.empty-orbit--1 {
  width: 200px;
  height: 200px;
  animation: empty-orbit-spin 22s linear infinite;
}

.empty-orbit--2 {
  width: 138px;
  height: 138px;
  border-style: dotted;
  animation: empty-orbit-spin 14s linear infinite reverse;
}

.empty-spark {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--empty-accent);
  opacity: 0.85;
  box-shadow: 0 0 12px var(--empty-accent-soft);
  animation: empty-spark-pulse 2.6s ease-in-out infinite;
}

.empty-spark--1 { top: 22px; left: 26px; animation-delay: 0s; }
.empty-spark--2 { bottom: 28px; right: 30px; animation-delay: 0.8s; width: 4px; height: 4px; }
.empty-spark--3 { top: 50%; right: 14px; animation-delay: 1.6s; width: 5px; height: 5px; }

.empty-svg {
  position: relative;
  z-index: 1;
  width: 200px;
  height: 160px;
  filter: drop-shadow(0 14px 22px rgba(20, 38, 76, 0.12));
}

.empty-title {
  margin: 4px 0 0;
  font-size: 17px;
  color: #22324f;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.empty-description {
  margin: 0;
  max-width: 340px;
  font-size: 13.5px;
  line-height: 1.7;
  color: #7d8ba6;
}

.empty-action {
  margin-top: 6px;
}

@keyframes empty-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

@keyframes empty-orbit-spin {
  to { transform: rotate(360deg); }
}

@keyframes empty-spark-pulse {
  0%, 100% { transform: scale(0.8); opacity: 0.45; }
  50% { transform: scale(1.15); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .empty-illustration,
  .empty-orbit,
  .empty-spark {
    animation: none;
  }
}

:global(html[data-auth-theme-mode='dark'] .empty-title) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .empty-description) {
  color: rgba(220, 230, 255, 0.7);
}

:global(html[data-auth-theme-mode='dark'] .empty-svg) {
  filter: drop-shadow(0 16px 26px rgba(10, 10, 12, 0.6));
}

:global(html[data-auth-theme-mode='dark'] .empty-orbit) {
  opacity: 0.45;
}
</style>
