<template>
  <section class="quick-start-panel" :class="{ 'is-collapsed': !expanded }">
    <div class="quick-start-head">
      <div class="quick-start-copy">
        <span class="quick-start-badge">快速上手</span>
        <h4>{{ title }}</h4>
        <p>{{ subtitle }}</p>
      </div>
      <button type="button" class="quick-start-toggle" @click="toggleExpanded">
        <span>{{ expanded ? '收起' : '展开' }}</span>
        <el-icon class="quick-start-toggle-icon" :class="{ 'is-expanded': expanded }">
          <ArrowDown />
        </el-icon>
      </button>
    </div>

    <transition name="quick-start-fade">
      <div v-show="expanded" class="quick-start-body">
        <div class="step-grid">
          <article v-for="(step, index) in steps" :key="`${storageKey}-${index}`" class="step-card">
            <span class="step-index">{{ index + 1 }}</span>
            <div class="step-copy">
              <h5>{{ step.title }}</h5>
              <p>{{ step.description }}</p>
            </div>
          </article>
        </div>

        <div v-if="tips.length" class="tip-row">
          <span v-for="tip in tips" :key="tip" class="tip-chip">{{ tip }}</span>
        </div>
      </div>
    </transition>
  </section>
</template>

<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

interface QuickStartStep {
  title: string
  description: string
}

const props = withDefaults(defineProps<{
  title: string
  subtitle?: string
  storageKey: string
  steps: QuickStartStep[]
  tips?: string[]
}>(), {
  subtitle: '第一次使用时建议先按下面的步骤完成一次完整流程。',
  tips: () => []
})

const expanded = ref(true)

const persistKey = `voice_factory_quick_start_${props.storageKey}`

const toggleExpanded = () => {
  expanded.value = !expanded.value
  localStorage.setItem(persistKey, expanded.value ? 'open' : 'closed')
}

onMounted(() => {
  const cached = localStorage.getItem(persistKey)
  if (cached === 'open' || cached === 'closed') {
    expanded.value = cached === 'open'
  }
})
</script>

<style scoped>
.quick-start-panel {
  margin-bottom: 22px;
  border-radius: 18px;
  border: 1px solid rgba(123, 156, 238, 0.18);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.66) 0%, rgba(241, 246, 255, 0.9) 100%);
  box-shadow: 0 18px 36px rgba(45, 75, 146, 0.09);
  overflow: hidden;
}

.quick-start-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.quick-start-copy {
  min-width: 0;
}

.quick-start-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(75, 125, 255, 0.1);
  color: #4b7dff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.quick-start-copy h4 {
  margin: 10px 0 6px;
  color: #243463;
  font-size: 20px;
}

.quick-start-copy p {
  margin: 0;
  color: #617192;
  line-height: 1.7;
  font-size: 13px;
}

.quick-start-toggle {
  border: none;
  background: rgba(75, 125, 255, 0.08);
  color: #4066d9;
  border-radius: 12px;
  min-height: 42px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.22s ease, transform 0.22s ease;
}

.quick-start-toggle:hover {
  background: rgba(75, 125, 255, 0.14);
  transform: translateY(-1px);
}

.quick-start-toggle-icon {
  transition: transform 0.24s ease;
}

.quick-start-toggle-icon.is-expanded {
  transform: rotate(180deg);
}

.quick-start-body {
  padding: 0 20px 20px;
}

.step-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.step-card {
  min-width: 0;
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(121, 154, 255, 0.14);
}

.step-index {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(75, 125, 255, 0.14) 0%, rgba(75, 125, 255, 0.22) 100%);
  color: #4169df;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.step-copy {
  min-width: 0;
}

.step-copy h5 {
  margin: 0 0 6px;
  color: #243463;
  font-size: 15px;
}

.step-copy p {
  margin: 0;
  color: #627292;
  font-size: 13px;
  line-height: 1.7;
}

.tip-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tip-chip {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(75, 125, 255, 0.08);
  color: #5d6f98;
  font-size: 12px;
}

.quick-start-fade-enter-active,
.quick-start-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.quick-start-fade-enter-from,
.quick-start-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 900px) {
  .step-grid {
    grid-template-columns: 1fr;
  }

  .quick-start-head {
    flex-direction: column;
    align-items: stretch;
  }

  .quick-start-toggle {
    justify-content: center;
  }
}

html[data-auth-theme-mode='dark'] .quick-start-panel {
  background: linear-gradient(180deg, rgba(18, 29, 57, 0.94) 0%, rgba(13, 22, 44, 0.98) 100%);
  border-color: rgba(255, 255, 255, 0.18);
  box-shadow: 0 22px 42px rgba(2, 8, 23, 0.32);
}

html[data-auth-theme-mode='dark'] .quick-start-badge {
  background: rgba(84, 127, 255, 0.16);
  color: #9bbdff;
}

html[data-auth-theme-mode='dark'] .quick-start-copy h4,
html[data-auth-theme-mode='dark'] .step-copy h5 {
  color: #eef3ff;
}

html[data-auth-theme-mode='dark'] .quick-start-copy p,
html[data-auth-theme-mode='dark'] .step-copy p,
html[data-auth-theme-mode='dark'] .tip-chip {
  color: rgba(219, 231, 255, 0.78);
}

html[data-auth-theme-mode='dark'] .quick-start-toggle {
  background: rgba(84, 127, 255, 0.12);
  color: #b9d1ff;
}

html[data-auth-theme-mode='dark'] .step-card {
  background: rgba(13, 22, 43, 0.84);
  border-color: rgba(255, 255, 255, 0.14);
}

html[data-auth-theme-mode='dark'] .step-index {
  background: linear-gradient(135deg, rgba(84, 127, 255, 0.2) 0%, rgba(84, 127, 255, 0.3) 100%);
  color: #d8e5ff;
}

html[data-auth-theme-mode='dark'] .tip-chip {
  background: rgba(84, 127, 255, 0.1);
}
</style>
