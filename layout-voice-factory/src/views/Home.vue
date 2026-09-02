<template>
  <flow-background-panel>
    <div class="control-room">
      <section class="command-hero">
        <div class="command-copy">
          <span class="eyebrow">AI Voice Operations</span>
          <h1>智能语音总控台</h1>
          <p>把录音转写、实时字幕、语音合成、声纹验证与智能纪要收束到一个工作台里，形成从语音采集到智能分析再到结果沉淀的完整链路。</p>
          <div class="hero-actions">
            <router-link to="/asr" class="primary-action">
              <span>开始转写</span>
              <el-icon><Right /></el-icon>
            </router-link>
            <router-link to="/MeetingNotes" class="secondary-action">生成纪要</router-link>
          </div>
        </div>

        <div class="signal-stage">
          <el-carousel class="stage-carousel" height="360px" :interval="4000" :autoplay="true" :pause-on-hover="false" arrow="never" trigger="click">
            <el-carousel-item>
              <div class="signal-core">
                <span class="stage-tag">Voice AI · 总览</span>
                <div class="orbit orbit--outer"></div>
                <div class="orbit orbit--middle"></div>
                <div class="orbit orbit--inner"></div>
                <div class="core-node">
                  <span class="core-pulse"></span>
                  <el-icon><Cpu /></el-icon>
                  <strong>Voice AI</strong>
                </div>
                <span class="satellite satellite--asr">ASR</span>
                <span class="satellite satellite--tts">TTS</span>
                <span class="satellite satellite--sv">SV</span>
                <span class="satellite satellite--note">Note</span>
                <div class="wave-monitor">
                  <span v-for="bar in waveBars" :key="bar" :style="{ height: `${bar}px` }"></span>
                </div>
              </div>
            </el-carousel-item>

            <el-carousel-item>
              <div class="signal-core stage-asr">
                <span class="stage-tag">FunASR · 录音转写</span>
                <div class="asr-wave">
                  <span v-for="n in 22" :key="`asr-${n}`"></span>
                </div>
                <div class="asr-lines">
                  <p class="asr-line">检测到语音输入…</p>
                  <p class="asr-line">识别中：各位好，会议现在开始</p>
                  <p class="asr-line">转写文本已生成 ✓</p>
                </div>
              </div>
            </el-carousel-item>

            <el-carousel-item>
              <div class="signal-core stage-tts">
                <span class="stage-tag">TTS · 语音合成</span>
                <div class="tts-text">输入文本 → 合成自然语音</div>
                <div class="tts-emitter">
                  <span class="tts-ripple"></span>
                  <span class="tts-ripple"></span>
                  <span class="tts-ripple"></span>
                  <span class="tts-core">
                    <el-icon><Microphone /></el-icon>
                  </span>
                </div>
                <div class="tts-out">
                  <span v-for="n in 18" :key="`tts-${n}`"></span>
                </div>
              </div>
            </el-carousel-item>

            <el-carousel-item>
              <div class="signal-core stage-sv">
                <span class="stage-tag">Voiceprint · 声纹对比</span>
                <div class="sv-compare">
                  <div class="sv-wave sv-wave--a">
                    <span v-for="n in 9" :key="`sva-${n}`"></span>
                  </div>
                  <div class="sv-ring">
                    <strong>98%</strong>
                    <small>声纹匹配</small>
                  </div>
                  <div class="sv-wave sv-wave--b">
                    <span v-for="n in 9" :key="`svb-${n}`"></span>
                  </div>
                </div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>
      </section>

      <section class="service-strip">
        <article v-for="service in serviceStatuses" :key="service.key" class="service-pill">
          <span class="service-dot" :class="`service-dot--${service.status}`"></span>
          <div>
            <strong>{{ service.label }}</strong>
            <small>{{ serviceText(service.status) }}</small>
          </div>
        </article>
      </section>

      <section class="metric-grid">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </section>

      <section class="feature-grid">
        <router-link
          v-for="feature in features"
          :key="feature.path"
          :to="feature.path"
          class="feature-card"
          :class="`feature-card--${feature.tone}`"
        >
          <span class="feature-icon">
            <el-icon><component :is="feature.icon" /></el-icon>
          </span>
          <div>
            <strong>{{ feature.title }}</strong>
            <p>{{ feature.description }}</p>
          </div>
          <span class="feature-entry">
            进入功能
            <el-icon><Right /></el-icon>
          </span>
        </router-link>
      </section>

      <section class="dashboard-lower">
        <article class="activity-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Recent Activity</span>
              <h2>最近处理动态</h2>
            </div>
            <router-link to="/profile">个人主页</router-link>
          </div>
          <div v-if="recentActivities.length" class="activity-list">
            <article v-for="item in recentActivities" :key="item.id" class="activity-item">
              <span class="activity-type">{{ item.type }}</span>
              <div>
                <strong>{{ item.title }}</strong>
                <small>{{ item.time || '暂无时间' }}</small>
              </div>
            </article>
          </div>
          <div v-else class="empty-panel">登录并完成一次语音任务后，这里会自动显示最近的转写、合成、声纹和纪要记录。</div>
        </article>

        <article class="demo-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Show Flow</span>
              <h2>推荐演示顺序</h2>
            </div>
          </div>
          <ol class="demo-flow">
            <li v-for="step in demoSteps" :key="step.title">
              <span>{{ step.index }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <small>{{ step.copy }}</small>
              </div>
            </li>
          </ol>
        </article>
      </section>
    </div>
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, ref } from 'vue'
import { Connection, Cpu, Document, Headset, Microphone, Right, Tickets } from '@element-plus/icons-vue'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import http from '@/api'
import { getMyAudioHistory, AudioHistoryItem } from '@/api/history'
import { getMyTtsHistory, TtsHistoryItem } from '@/api/tts'
import { getMyVoiceprintHistory, VoiceprintHistoryItem } from '@/api/voiceprint'
import { getMeetingStats, getMyMeetingHistory, MeetingHistoryItem, MeetingStats } from '@/api/meeting'
import store from '@/store'

type ServiceStatus = 'checking' | 'online' | 'offline'

interface ActivityItem {
  id: string
  type: string
  title: string
  time?: string
}

const defaultStats: MeetingStats = {
  totalNotes: 0,
  meetingNotes: 0,
  classroomNotes: 0,
  successNotes: 0,
  failedNotes: 0,
  speakerProfiles: 0,
  totalSegments: 0,
  totalTodos: 0,
  recentSevenDaysNotes: 0
}

export default defineComponent({
  name: 'Home',
  components: { FlowBackgroundPanel, Connection, Cpu, Document, Headset, Microphone, Right, Tickets },
  setup () {
    const isLoggedIn = computed(() => store.getters.isLoggedIn)
    const serviceStatuses = ref([
      { key: 'asr', label: 'FunASR', status: 'checking' as ServiceStatus },
      { key: 'tts', label: 'TTS', status: 'checking' as ServiceStatus },
      { key: 'voiceprint', label: 'Voiceprint', status: 'checking' as ServiceStatus },
      { key: 'meeting', label: 'Meeting API', status: 'checking' as ServiceStatus }
    ])
    const audioHistory = ref<AudioHistoryItem[]>([])
    const ttsHistory = ref<TtsHistoryItem[]>([])
    const voiceprintHistory = ref<VoiceprintHistoryItem[]>([])
    const meetingHistory = ref<MeetingHistoryItem[]>([])
    const meetingStats = ref<MeetingStats>({ ...defaultStats })

    const waveBars = [18, 34, 24, 52, 38, 68, 42, 56, 28, 46, 72, 32, 58, 40, 24]
    const features = [
      { title: '录音转文字', description: '上传音频并调用 FunASR，生成可回看、可下载的转写历史。', path: '/asr', tone: 'blue', icon: Microphone },
      { title: '实时语音', description: 'WebSocket 代理实时识别，适合现场字幕和课堂记录。', path: '/RealtimeVoice', tone: 'cyan', icon: Headset },
      { title: '文字转语音', description: '输入文本、上传参考音色，生成情绪化语音结果。', path: '/TextToVoice', tone: 'violet', icon: Document },
      { title: '声纹对比', description: '两段音频比对相似度，沉淀发言人档案与历史样本。', path: '/VoicePrintCompare', tone: 'emerald', icon: Connection },
      { title: '智能纪要', description: '从长音频提炼摘要、待办、关键词和说话人时间线。', path: '/MeetingNotes', tone: 'amber', icon: Tickets }
    ]
    const demoSteps = [
      { index: '01', title: '录音转写', copy: '上传一段会议或课堂音频，展示文本生成。' },
      { index: '02', title: '智能纪要', copy: '复用转写历史，生成摘要、待办和关键词。' },
      { index: '03', title: '语音合成', copy: '输入摘要内容，合成可试听音频。' },
      { index: '04', title: '声纹对比', copy: '用两段样本验证说话人一致性。' }
    ]

    const formatNumber = (value: number) => value.toLocaleString('zh-CN')
    const statusCount = (items: { status?: string }[]) => items.filter(item => item.status === 'SUCCESS').length
    const pickTime = (item: { createTime?: string; updateTime?: string }) => item.updateTime || item.createTime || ''

    const metrics = computed(() => [
      { label: '转写任务', value: formatNumber(audioHistory.value.length), hint: `${statusCount(audioHistory.value)} 条成功记录` },
      { label: '语音合成', value: formatNumber(ttsHistory.value.length), hint: `${statusCount(ttsHistory.value)} 条可试听结果` },
      { label: '声纹比对', value: formatNumber(voiceprintHistory.value.length), hint: `${statusCount(voiceprintHistory.value)} 条已完成` },
      { label: '智能纪要', value: formatNumber(meetingStats.value.totalNotes || meetingHistory.value.length), hint: `近 7 天新增 ${meetingStats.value.recentSevenDaysNotes || 0} 条` }
    ])

    const recentActivities = computed<ActivityItem[]>(() => {
      const items: ActivityItem[] = [
        ...audioHistory.value.slice(0, 3).map(item => ({ id: `asr-${item.id}`, type: 'ASR', title: item.originalFilename || '录音转文字', time: pickTime(item) })),
        ...ttsHistory.value.slice(0, 3).map(item => ({ id: `tts-${item.id}`, type: 'TTS', title: item.inputText ? item.inputText.slice(0, 28) : '文字转语音', time: pickTime(item) })),
        ...voiceprintHistory.value.slice(0, 3).map(item => ({ id: `vp-${item.id}`, type: 'SV', title: `${item.leftFilename || '音频 A'} / ${item.rightFilename || '音频 B'}`, time: pickTime(item) })),
        ...meetingHistory.value.slice(0, 3).map(item => ({ id: `meeting-${item.id}`, type: 'NOTE', title: item.title || '智能纪要', time: pickTime(item) }))
      ]
      return items.sort((left, right) => (right.time || '').localeCompare(left.time || '')).slice(0, 6)
    })

    const serviceText = (status: ServiceStatus) => status === 'online' ? '在线' : status === 'offline' ? '离线' : '检测中'
    const updateService = (key: string, status: ServiceStatus) => {
      const target = serviceStatuses.value.find(service => service.key === key)
      if (target) target.status = status
    }

    const loadHealth = async () => {
      const checks = [
        { key: 'asr', url: '/api/funasr/health' },
        { key: 'tts', url: '/api/tts/health' },
        { key: 'voiceprint', url: '/api/voiceprint/health' }
      ]
      await Promise.all(checks.map(async check => {
        try {
          const res = await http.get(check.url) as unknown as { code?: number; status?: string }
          updateService(check.key, res?.code === 200 || res?.status === 'success' ? 'online' : 'offline')
        } catch (error) {
          updateService(check.key, 'offline')
        }
      }))
      updateService('meeting', 'online')
    }

    const loadUserData = async () => {
      if (!isLoggedIn.value) return
      const [audioRes, ttsRes, voiceprintRes, meetingRes, statsRes] = await Promise.allSettled([
        getMyAudioHistory(),
        getMyTtsHistory(),
        getMyVoiceprintHistory(),
        getMyMeetingHistory(),
        getMeetingStats()
      ])
      if (audioRes.status === 'fulfilled' && audioRes.value.code === 200) audioHistory.value = audioRes.value.data || []
      if (ttsRes.status === 'fulfilled' && ttsRes.value.code === 200) ttsHistory.value = ttsRes.value.data || []
      if (voiceprintRes.status === 'fulfilled' && voiceprintRes.value.code === 200) voiceprintHistory.value = voiceprintRes.value.data || []
      if (meetingRes.status === 'fulfilled' && meetingRes.value.code === 200) meetingHistory.value = meetingRes.value.data || []
      if (statsRes.status === 'fulfilled' && statsRes.value.code === 200) meetingStats.value = statsRes.value.data || { ...defaultStats }
    }

    onMounted(() => {
      loadHealth()
      loadUserData()
    })

    return { serviceStatuses, serviceText, metrics, features, waveBars, demoSteps, recentActivities }
  }
})
</script>

<style scoped>
.control-room {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
  padding: 8px 0 32px;
}

.command-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: 28px;
  align-items: stretch;
  min-height: 420px;
  padding: 38px;
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid rgba(124, 154, 240, 0.22);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.86) 0%, rgba(239, 246, 255, 0.76) 48%, rgba(236, 244, 255, 0.66) 100%);
  box-shadow: 0 30px 70px rgba(15, 33, 72, 0.14);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

.command-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(59, 130, 246, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(59, 130, 246, 0.08) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.75), transparent 72%);
  pointer-events: none;
}

.command-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.eyebrow {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.eyebrow::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #22d3ee;
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.75);
}

.command-copy h1 {
  margin: 18px 0 16px;
  max-width: 620px;
  color: #101827;
  font-size: 58px;
  line-height: 1.04;
  font-weight: 850;
}

.command-copy p {
  max-width: 620px;
  margin: 0;
  color: #52637f;
  font-size: 16px;
  line-height: 1.9;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 30px;
}

.primary-action,
.secondary-action {
  min-height: 46px;
  padding: 0 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 12px;
  font-weight: 750;
  text-decoration: none;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.primary-action {
  color: #fff;
  border: 1px solid rgba(59, 130, 246, 0.25);
  background: linear-gradient(135deg, #2563eb 0%, #0891b2 100%);
  box-shadow: 0 18px 38px rgba(37, 99, 235, 0.22);
}

.secondary-action {
  color: #1f3c6d;
  border: 1px solid rgba(83, 119, 205, 0.2);
  background: rgba(255, 255, 255, 0.66);
}

.primary-action:hover,
.secondary-action:hover {
  transform: translateY(-2px);
}

.signal-core {
  position: relative;
  min-height: 350px;
  border-radius: 16px;
  overflow: hidden;
  background: radial-gradient(circle at 50% 42%, rgba(37, 99, 235, 0.2), transparent 35%), linear-gradient(145deg, rgba(6, 13, 31, 0.96), rgba(13, 31, 61, 0.9));
  border: 1px solid rgba(148, 189, 255, 0.2);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.05);
}

.orbit {
  position: absolute;
  inset: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1px solid rgba(147, 197, 253, 0.22);
}

.orbit--outer { width: 310px; height: 310px; }
.orbit--middle { width: 230px; height: 230px; }
.orbit--inner { width: 150px; height: 150px; }

.core-node {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 118px;
  height: 118px;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 50%;
  color: #eaf6ff;
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.96), rgba(30, 64, 175, 0.82));
  box-shadow: 0 0 46px rgba(34, 211, 238, 0.26);
}

.core-node :deep(.el-icon) {
  font-size: 28px;
  color: #67e8f9;
}

.core-node strong {
  font-size: 13px;
}

.core-pulse {
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  border: 1px solid rgba(103, 232, 249, 0.28);
  animation: pulse-ring 2.8s ease-out infinite;
}

.satellite {
  position: absolute;
  min-width: 58px;
  min-height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #dff7ff;
  font-size: 12px;
  font-weight: 800;
  border: 1px solid rgba(125, 211, 252, 0.22);
  background: rgba(15, 23, 42, 0.66);
  backdrop-filter: blur(12px);
}

.satellite--asr { left: 18%; top: 22%; }
.satellite--tts { right: 16%; top: 26%; }
.satellite--sv { left: 18%; bottom: 22%; }
.satellite--note { right: 15%; bottom: 24%; }

.wave-monitor {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 26px;
  height: 74px;
  display: flex;
  align-items: end;
  justify-content: center;
  gap: 8px;
  border-radius: 14px;
  background: rgba(8, 15, 35, 0.58);
  border: 1px solid rgba(125, 211, 252, 0.12);
}

.wave-monitor span {
  width: 7px;
  border-radius: 999px;
  background: linear-gradient(180deg, #67e8f9, #3b82f6);
  animation: wave-breathe 1.8s ease-in-out infinite alternate;
}

/* ===== hero 轮播 ===== */
.signal-stage {
  position: relative;
  align-self: center;
  width: 100%;
  height: 360px;
}

.stage-carousel {
  height: 100%;
  border-radius: 16px;
  overflow: hidden;
}

.signal-stage :deep(.el-carousel__item) .signal-core {
  height: 100%;
  min-height: 0;
}

.stage-carousel :deep(.el-carousel__button) {
  width: 18px;
  height: 4px;
  border-radius: 999px;
  background: #9fd9ff;
  opacity: 0.4;
}

.stage-carousel :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  opacity: 1;
  background: #67e8f9;
  box-shadow: 0 0 12px rgba(103, 232, 249, 0.6);
}

.stage-tag {
  position: absolute;
  left: 18px;
  top: 16px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #bfe9ff;
  background: rgba(8, 15, 35, 0.62);
  border: 1px solid rgba(125, 211, 252, 0.24);
}

.stage-asr,
.stage-tts,
.stage-sv {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 30px;
}

/* 帧2：FunASR 录音转写 */
.asr-wave {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 72px;
}

.asr-wave span {
  width: 5px;
  height: 26px;
  border-radius: 999px;
  background: linear-gradient(180deg, #67e8f9, #3b82f6);
  transform-origin: center;
  animation: sv-bar 1.2s ease-in-out infinite;
}

.asr-wave span:nth-child(2n) { height: 48px; }
.asr-wave span:nth-child(3n) { height: 64px; animation-delay: 0.2s; }
.asr-wave span:nth-child(4n) { height: 36px; animation-delay: 0.35s; }
.asr-wave span:nth-child(5n) { height: 56px; animation-delay: 0.5s; }

.asr-lines {
  width: min(88%, 326px);
  display: grid;
  gap: 8px;
}

.asr-line {
  margin: 0;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  color: #e2f5ff;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(125, 211, 252, 0.16);
  opacity: 0.28;
  animation: stage-line 4.5s ease-in-out infinite;
}

.asr-line:nth-child(2) { animation-delay: 0.7s; }
.asr-line:nth-child(3) { animation-delay: 1.4s; }

/* 帧3：TTS 语音合成 */
.tts-text {
  color: #e2f5ff;
  font-size: 15px;
  font-weight: 700;
}

.tts-emitter {
  position: relative;
  width: 104px;
  height: 104px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tts-ripple {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px solid rgba(103, 232, 249, 0.5);
  animation: tts-ripple 2.4s ease-out infinite;
}

.tts-ripple:nth-child(2) { animation-delay: 0.8s; }
.tts-ripple:nth-child(3) { animation-delay: 1.6s; }

.tts-core {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.96), rgba(30, 64, 175, 0.82));
  box-shadow: 0 0 32px rgba(34, 211, 238, 0.3);
}

.tts-core :deep(.el-icon) {
  font-size: 24px;
  color: #67e8f9;
}

.tts-out {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 42px;
}

.tts-out span {
  width: 5px;
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(180deg, #a5b4fc, #3b82f6);
  transform-origin: center;
  animation: sv-bar 1.5s ease-in-out infinite;
}

.tts-out span:nth-child(2n) { height: 34px; animation-delay: 0.2s; }
.tts-out span:nth-child(3n) { height: 26px; animation-delay: 0.4s; }

/* 帧4：声纹对比 */
.sv-compare {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
}

.sv-wave {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 92px;
}

.sv-wave span {
  width: 5px;
  height: 30px;
  border-radius: 999px;
  background: linear-gradient(180deg, #67e8f9, #3b82f6);
  transform-origin: center;
  animation: sv-bar 1.3s ease-in-out infinite;
}

.sv-wave--b span { background: linear-gradient(180deg, #a78bfa, #6366f1); }
.sv-wave span:nth-child(2n) { height: 56px; animation-delay: 0.18s; }
.sv-wave span:nth-child(3n) { height: 74px; animation-delay: 0.36s; }
.sv-wave span:nth-child(4n) { height: 42px; animation-delay: 0.5s; }

.sv-ring {
  position: relative;
  width: 106px;
  height: 106px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #eaf6ff;
  border: 2px solid rgba(103, 232, 249, 0.22);
  box-shadow: 0 0 30px rgba(34, 211, 238, 0.2);
}

.sv-ring::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 2px solid transparent;
  border-top-color: #67e8f9;
  border-right-color: #67e8f9;
  animation: sv-spin 2.6s linear infinite;
}

.sv-ring strong { font-size: 26px; line-height: 1; }
.sv-ring small { margin-top: 4px; font-size: 11px; color: #9fd9ff; }

@keyframes sv-bar {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}

@keyframes stage-line {
  0%, 100% { opacity: 0.28; }
  30%, 70% { opacity: 1; }
}

@keyframes tts-ripple {
  from { transform: scale(0.4); opacity: 0.8; }
  to { transform: scale(1); opacity: 0; }
}

@keyframes sv-spin {
  to { transform: rotate(360deg); }
}

.service-strip,
.metric-grid,
.feature-grid,
.dashboard-lower {
  margin-top: 18px;
}

.service-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.service-pill,
.metric-card,
.feature-card,
.activity-panel,
.demo-panel {
  border-radius: 14px;
  border: 1px solid rgba(124, 154, 240, 0.18);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 18px 42px rgba(15, 33, 72, 0.08);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.service-pill {
  min-height: 72px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.service-dot {
  width: 11px;
  height: 11px;
  border-radius: 999px;
  background: #94a3b8;
}

.service-dot--online {
  background: #10b981;
  box-shadow: 0 0 0 6px rgba(16, 185, 129, 0.12);
}

.service-dot--offline {
  background: #ef4444;
  box-shadow: 0 0 0 6px rgba(239, 68, 68, 0.1);
}

.service-pill strong,
.metric-card strong,
.feature-card strong,
.panel-head h2,
.activity-item strong,
.demo-flow strong {
  color: #172033;
}

.service-pill small,
.metric-card small,
.feature-card p,
.activity-item small,
.demo-flow small,
.empty-panel {
  color: #64748b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  padding: 18px;
  min-height: 126px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.metric-card span {
  color: #52637f;
  font-size: 13px;
  font-weight: 700;
}

.metric-card strong {
  font-size: 34px;
  line-height: 1;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.feature-card {
  position: relative;
  min-height: 210px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  text-decoration: none;
  overflow: hidden;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.feature-card::after {
  content: '';
  position: absolute;
  right: -36px;
  top: -36px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: var(--feature-glow, rgba(59, 130, 246, 0.16));
}

.feature-card:hover {
  transform: translateY(-4px);
  border-color: rgba(59, 130, 246, 0.3);
  box-shadow: 0 24px 52px rgba(15, 33, 72, 0.13);
}

.feature-card--blue { --feature-glow: rgba(59, 130, 246, 0.18); }
.feature-card--cyan { --feature-glow: rgba(34, 211, 238, 0.18); }
.feature-card--violet { --feature-glow: rgba(139, 92, 246, 0.16); }
.feature-card--emerald { --feature-glow: rgba(16, 185, 129, 0.16); }
.feature-card--amber { --feature-glow: rgba(245, 158, 11, 0.16); }

.feature-icon {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #2563eb;
  background: rgba(239, 246, 255, 0.9);
  border: 1px solid rgba(96, 165, 250, 0.18);
}

.feature-icon :deep(.el-icon) {
  font-size: 22px;
}

.feature-card p {
  margin: 8px 0 0;
  line-height: 1.65;
  font-size: 13px;
}

.feature-entry {
  margin-top: auto;
  width: fit-content;
  min-height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 8px;
  color: #2563eb;
  background: rgba(219, 234, 254, 0.72);
  font-weight: 900;
}

.feature-entry :deep(.el-icon) {
  font-size: 14px;
}

.dashboard-lower {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 16px;
}

.activity-panel,
.demo-panel {
  padding: 20px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-head h2 {
  margin: 8px 0 0;
  font-size: 22px;
}

.panel-head a {
  color: #2563eb;
  font-size: 13px;
  text-decoration: none;
  font-weight: 700;
}

.activity-list {
  display: grid;
  gap: 10px;
}

.activity-item,
.demo-flow li {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.78);
  border: 1px solid rgba(124, 154, 240, 0.12);
}

.activity-type {
  min-width: 54px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #1d4ed8;
  background: rgba(219, 234, 254, 0.85);
  font-size: 12px;
  font-weight: 800;
}

.empty-panel {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 18px;
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.72);
  border: 1px dashed rgba(124, 154, 240, 0.24);
  line-height: 1.8;
}

.demo-flow {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}

.demo-flow span {
  color: #0891b2;
  font-weight: 900;
  font-size: 13px;
}

.demo-flow small {
  display: block;
  margin-top: 4px;
  line-height: 1.55;
}

@keyframes pulse-ring {
  from { opacity: 0.8; transform: scale(0.9); }
  to { opacity: 0; transform: scale(1.28); }
}

@keyframes wave-breathe {
  from { transform: scaleY(0.62); opacity: 0.55; }
  to { transform: scaleY(1); opacity: 1; }
}

:global(html[data-auth-theme-mode='dark'] .command-hero),
:global(html[data-auth-theme-mode='dark'] .service-pill),
:global(html[data-auth-theme-mode='dark'] .metric-card),
:global(html[data-auth-theme-mode='dark'] .feature-card),
:global(html[data-auth-theme-mode='dark'] .activity-panel),
:global(html[data-auth-theme-mode='dark'] .demo-panel) {
  background: rgba(12, 20, 39, 0.78);
  border-color: rgba(148, 189, 255, 0.16);
  box-shadow: 0 24px 58px rgba(2, 8, 23, 0.38);
}

:global(html[data-auth-theme-mode='dark'] .command-copy h1),
:global(html[data-auth-theme-mode='dark'] .service-pill strong),
:global(html[data-auth-theme-mode='dark'] .metric-card strong),
:global(html[data-auth-theme-mode='dark'] .feature-card strong),
:global(html[data-auth-theme-mode='dark'] .panel-head h2),
:global(html[data-auth-theme-mode='dark'] .activity-item strong),
:global(html[data-auth-theme-mode='dark'] .demo-flow strong) {
  color: #eef6ff;
}

:global(html[data-auth-theme-mode='dark'] .command-copy p),
:global(html[data-auth-theme-mode='dark'] .service-pill small),
:global(html[data-auth-theme-mode='dark'] .metric-card small),
:global(html[data-auth-theme-mode='dark'] .feature-card p),
:global(html[data-auth-theme-mode='dark'] .activity-item small),
:global(html[data-auth-theme-mode='dark'] .demo-flow small),
:global(html[data-auth-theme-mode='dark'] .empty-panel) {
  color: #9fb3d6;
}

:global(html[data-auth-theme-mode='dark'] .secondary-action),
:global(html[data-auth-theme-mode='dark'] .activity-item),
:global(html[data-auth-theme-mode='dark'] .empty-panel),
:global(html[data-auth-theme-mode='dark'] .demo-flow li) {
  background: rgba(15, 27, 52, 0.76);
  border-color: rgba(148, 189, 255, 0.14);
}

@media (max-width: 1200px) {
  .feature-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .command-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .control-room {
    width: min(100% - 24px, 1180px);
  }

  .command-hero {
    padding: 24px;
  }

  .command-copy h1 {
    font-size: 40px;
  }

  .service-strip,
  .metric-grid,
  .feature-grid,
  .dashboard-lower {
    grid-template-columns: 1fr;
  }
}
</style>
