<template>
  <FlowBackgroundPanel>
    <main class="observation-page">
      <section class="observation-shell">
      <header class="page-heading">
        <p>SYSTEM OBSERVATION</p>
        <h1>系统观察</h1>
        <span>仅展示服务健康与脱敏汇总指标；不会显示用户正文、原始音频、票据或供应商密钥。</span>
      </header>

      <section class="overview-card" :class="snapshot?.status === 'UP' ? 'is-up' : 'is-degraded'">
        <div>
          <span>整体运行状态</span>
          <strong>{{ snapshot?.status === 'UP' ? '运行正常' : snapshot ? '部分降级' : '等待检测' }}</strong>
          <small>{{ checkedAt }}</small>
        </div>
        <el-button plain :loading="loading" @click="refresh">刷新检测</el-button>
      </section>

      <section class="metric-grid">
        <article class="metric-card"><span>平均首字时间</span><strong>{{ formatMs(metrics?.averageFirstTokenMs) }}</strong><small>DeepSeek 首个增量文本</small></article>
        <article class="metric-card"><span>平均首音时间</span><strong>{{ formatMs(metrics?.averageFirstAudioMs) }}</strong><small>讯飞首个 PCM 音频块</small></article>
        <article class="metric-card"><span>打断轮次</span><strong>{{ metrics?.turnsCancelled ?? '—' }}</strong><small>本次网关启动后的累计值</small></article>
        <article class="metric-card"><span>失败轮次</span><strong>{{ metrics?.turnsFailed ?? '—' }}</strong><small>不含具体用户内容</small></article>
        <article class="metric-card"><span>音频缓冲欠载</span><strong>{{ metrics?.bufferUnderruns ?? '—' }}</strong><small>用于发现连续播放问题</small></article>
        <article class="metric-card"><span>完成轮次</span><strong>{{ metrics?.turnsCompleted ?? '—' }}</strong><small>已完成的实时回答</small></article>
      </section>

      <section class="service-card" v-loading="loading">
        <div class="section-head"><div><strong>依赖服务健康</strong><small>服务名、状态和响应时间均为脱敏信息。</small></div><em>最近样本 {{ metrics?.sampleWindow ?? 0 }} 条</em></div>
        <el-table :data="snapshot?.services || []" empty-text="暂未获得服务状态">
          <el-table-column prop="name" label="服务" min-width="180" />
          <el-table-column label="状态" width="140"><template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="plain">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="响应时间" width="140"><template #default="scope">{{ scope.row.latencyMs }} ms</template></el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" />
        </el-table>
      </section>
      </section>
    </main>
  </FlowBackgroundPanel>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import { GatewayMetrics, getGatewayMetrics, getSystemStatus, SystemSnapshot } from '@/api/system'

export default defineComponent({
  name: 'SystemObservation',
  components: { FlowBackgroundPanel },
  setup () {
    const snapshot = ref<SystemSnapshot | null>(null)
    const metrics = ref<GatewayMetrics | null>(null)
    const loading = ref(false)
    const unwrap = <T,>(response: { code: number, msg: string, data: T }) => {
      if (response.code !== 200) throw new Error(response.msg || '服务状态获取失败')
      return response.data
    }
    const refresh = async () => {
      loading.value = true
      try {
        const [system, gateway] = await Promise.all([getSystemStatus(), getGatewayMetrics()])
        snapshot.value = unwrap(system)
        metrics.value = gateway
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '系统观察服务暂不可用')
      } finally { loading.value = false }
    }
    const formatMs = (value: number | null | undefined) => value == null ? '暂无样本' : `${value} ms`
    const statusLabel = (status: string) => ({ UP: '在线', DOWN: '离线', UNKNOWN: '未知' }[status] || status)
    const statusType = (status: string) => ({ UP: 'success', DOWN: 'danger', UNKNOWN: 'warning' }[status] || 'info')
    const checkedAt = computed(() => snapshot.value?.checkedAt ? `检测时间：${new Date(snapshot.value.checkedAt).toLocaleString()}` : '点击刷新开始检测')
    onMounted(refresh)
    return { snapshot, metrics, loading, refresh, formatMs, statusLabel, statusType, checkedAt }
  }
})
</script>

<style scoped>
.observation-page { position: relative; min-height: calc(100vh - 60px); padding: 48px 24px 72px; overflow: hidden; }.observation-shell { position: relative; z-index: 1; max-width: 1180px; margin: 0 auto; }.page-heading { max-width: 720px; margin-bottom: 28px; }.page-heading p { margin: 0 0 8px; color: #476ef7; letter-spacing: .14em; font-size: 12px; font-weight: 700; }.page-heading h1 { margin: 0 0 10px; color: #1c2d50; font-size: 34px; }.page-heading span, small { color: #71819e; line-height: 1.6; }.overview-card, .metric-card, .service-card { border: 1px solid rgba(112, 103, 255, .2); border-radius: 20px; background: rgba(255,255,255,.84); box-shadow: 0 18px 45px rgba(85, 109, 172, .1); backdrop-filter: blur(14px); }.overview-card { padding: 22px 24px; display: flex; justify-content: space-between; align-items: center; border-left: 5px solid #e9a23b; }.overview-card.is-up { border-left-color: #28aa6a; }.overview-card span, .overview-card small, .metric-card span, .metric-card small { display: block; }.overview-card strong { display: block; margin: 4px 0; color: #223758; font-size: 24px; }.metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; margin: 18px 0; }.metric-card { padding: 19px; }.metric-card strong { display: block; color: #263b60; font-size: 24px; margin: 8px 0 4px; }.service-card { padding: 24px; }.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }.section-head strong, .section-head small { display: block; }.section-head strong { color: #273959; font-size: 18px; }.section-head em { color: #7284a3; font-style: normal; font-size: 13px; } @media (max-width: 760px) { .observation-page { padding: 28px 14px 44px; }.metric-grid { grid-template-columns: 1fr 1fr; }.overview-card { align-items: flex-start; gap: 15px; flex-direction: column; } }
</style>
