<template>
  <teleport to="body">
    <transition name="cmdk">
      <div
        v-if="isOpen"
        class="cmdk-overlay"
        :class="themeClass"
        @mousedown.self="close"
      >
        <div class="cmdk-panel" role="dialog" aria-modal="true" aria-label="命令面板" @mousedown.stop>
          <div class="cmdk-search">
            <svg class="cmdk-search-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="11" cy="11" r="7" />
              <path d="m21 21-4.3-4.3" />
            </svg>
            <input
              ref="inputRef"
              v-model="query"
              class="cmdk-input"
              type="text"
              placeholder="搜索功能、页面、操作…"
              spellcheck="false"
              autocomplete="off"
              @keydown.down.prevent="move(1)"
              @keydown.up.prevent="move(-1)"
              @keydown.enter.prevent="runActive"
              @keydown.esc.prevent="close"
            />
            <kbd class="cmdk-esc">esc</kbd>
          </div>

          <div ref="listRef" class="cmdk-list">
            <template v-if="flatResults.length">
              <div v-for="g in groupedResults" :key="g.name" class="cmdk-group">
                <div class="cmdk-group-title">{{ g.name }}</div>
                <button
                  v-for="item in g.items"
                  :key="item.cmd.id"
                  type="button"
                  class="cmdk-item"
                  :class="{ 'is-active': item.flatIndex === active }"
                  @mousemove="active = item.flatIndex"
                  @click="run(item.cmd)"
                >
                  <span class="cmdk-item-icon">
                    <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path :d="item.cmd.icon" />
                    </svg>
                  </span>
                  <span class="cmdk-item-main">
                    <span class="cmdk-item-title">
                      <template v-for="(seg, i) in item.segs" :key="i"><mark v-if="seg.hit" class="cmdk-hl">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template></template>
                    </span>
                    <span class="cmdk-item-sub">{{ item.cmd.subtitle }}</span>
                  </span>
                  <span v-if="item.flatIndex === active" class="cmdk-item-enter">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmdk-empty">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="7" />
                <path d="m21 21-4.3-4.3" />
              </svg>
              <span>没有匹配 “{{ query }}” 的结果</span>
            </div>
          </div>

          <div class="cmdk-footer">
            <span class="cmdk-foot-item"><kbd>↑</kbd><kbd>↓</kbd> 导航</span>
            <span class="cmdk-foot-item"><kbd>↵</kbd> 选择</span>
            <span class="cmdk-foot-item"><kbd>esc</kbd> 关闭</span>
            <span class="cmdk-foot-brand">
              <span class="cmdk-foot-dot"></span>
              Voice Factory
            </span>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script lang="ts">
import { computed, defineComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { GlobalDataProps } from '@/store/types'
import { useAuthThemeMode } from '@/composables/useAuthThemeMode'
import { useCommandPalette } from '@/composables/useCommandPalette'

interface Command {
  id: string
  group: string
  title: string
  subtitle: string
  keywords: string
  icon: string
  run: () => void
}

interface Seg { text: string; hit: boolean }

const ICONS = {
  mic: 'M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v1a7 7 0 0 1-14 0v-1M12 18v4M8 22h8',
  speaker: 'M11 5 6 9H2v6h4l5 4zM15.54 8.46a5 5 0 0 1 0 7.07M19.07 4.93a10 10 0 0 1 0 14.14',
  doc: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  activity: 'M22 12h-4l-3 9L9 3l-3 9H2',
  compare: 'M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 16V8a4 4 0 0 1 4-4h4M18 8v8a4 4 0 0 1-4 4h-4',
  fileText: 'M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM13 2v7h7M9 13h6M9 17h4',
  dashboard: 'M3 13h8V3H3v10zM13 21h8V3h-8v18zM3 21h8v-6H3v6z',
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41',
  moon: 'M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 7a4 4 0 1 0 0 8 4 4 0 0 0 0-8z',
  login: 'M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3',
  signup: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM19 8v6M22 11h-6',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9'
}

// 子序列模糊匹配：返回是否命中、得分、命中下标（用于高亮）
function fuzzy (query: string, text: string) {
  const q = query.toLowerCase()
  const t = text.toLowerCase()
  if (!q) return { matched: true, score: 0, indices: [] as number[] }
  let qi = 0
  let score = 0
  let prev = -2
  const indices: number[] = []
  for (let ti = 0; ti < t.length && qi < q.length; ti++) {
    if (t[ti] === q[qi]) {
      indices.push(ti)
      score += 1
      if (ti === prev + 1) score += 6 // 连续命中
      if (ti === 0) score += 8 // 词首命中
      prev = ti
      qi++
    }
  }
  return { matched: qi === q.length, score, indices }
}

function buildSegs (title: string, indices: number[]): Seg[] {
  if (!indices.length) return [{ text: title, hit: false }]
  const set = new Set(indices)
  const segs: Seg[] = []
  let cur = ''
  let curHit = set.has(0)
  for (let i = 0; i < title.length; i++) {
    const hit = set.has(i)
    if (hit === curHit) {
      cur += title[i]
    } else {
      if (cur) segs.push({ text: cur, hit: curHit })
      cur = title[i]
      curHit = hit
    }
  }
  if (cur) segs.push({ text: cur, hit: curHit })
  return segs
}

export default defineComponent({
  name: 'CommandPalette',
  setup () {
    const router = useRouter()
    const store = useStore<GlobalDataProps>()
    const { isOpen, close, toggle } = useCommandPalette()
    const { isDark, themeClass, toggleTheme } = useAuthThemeMode()
    const isLoggedIn = computed(() => store.getters.isLoggedIn)

    const query = ref('')
    const active = ref(0)
    const inputRef = ref<HTMLInputElement | null>(null)
    const listRef = ref<HTMLElement | null>(null)

    const go = (path: string) => () => router.push(path)

    const commands = computed<Command[]>(() => {
      const nav: Command[] = [
        { id: 'nav-home', group: '页面导航', title: '总控台', subtitle: '所有功能入口与演示流程', keywords: 'home dashboard shouye zongkongtai 首页 总控台 gongzuotai asr tts meeting realtime voiceprint', icon: ICONS.dashboard, run: go('/') }
      ]
      const ops: Command[] = [
        {
          id: 'op-theme',
          group: '快捷操作',
          title: isDark.value ? '切换到白天模式' : '切换到夜间模式',
          subtitle: '明暗主题切换',
          keywords: 'theme dark light zhuti yejian baitian 主题 深色 浅色 模式',
          icon: isDark.value ? ICONS.sun : ICONS.moon,
          run: () => toggleTheme()
        }
      ]
      const auth: Command[] = isLoggedIn.value
        ? [
          { id: 'op-profile', group: '快捷操作', title: '个人主页', subtitle: '查看与编辑账号信息', keywords: 'profile geren zhanghao user me', icon: ICONS.user, run: go('/profile') },
          { id: 'op-logout', group: '快捷操作', title: '退出登录', subtitle: '注销当前账号', keywords: 'logout zhuxiao tuichu signout', icon: ICONS.logout, run: () => { store.commit('logout'); ElMessage.success('已退出登录'); router.push('/') } }
        ]
        : [
          { id: 'op-login', group: '快捷操作', title: '登录', subtitle: '登录已有账号', keywords: 'login denglu signin', icon: ICONS.login, run: go('/login') },
          { id: 'op-register', group: '快捷操作', title: '注册', subtitle: '创建新账号', keywords: 'register zhuce signup', icon: ICONS.signup, run: go('/register') }
        ]
      return [...nav, ...ops, ...auth]
    })

    const results = computed(() => {
      const q = query.value.trim()
      const scored = commands.value.map((cmd, order) => {
        const tr = fuzzy(q, cmd.title)
        const kr = fuzzy(q, cmd.keywords)
        const matched = !q || tr.matched || kr.matched
        const score = (tr.matched ? tr.score + 40 : 0) + (kr.matched ? kr.score : 0)
        return { cmd, matched, score, order, indices: tr.matched ? tr.indices : [] }
      }).filter(r => r.matched)

      if (q) scored.sort((a, b) => b.score - a.score || a.order - b.order)

      const flat = scored.map((r, i) => ({ cmd: r.cmd, flatIndex: i, segs: buildSegs(r.cmd.title, r.indices) }))
      const groups: { name: string; items: typeof flat }[] = []
      const map: Record<string, { name: string; items: typeof flat }> = {}
      for (const item of flat) {
        const g = item.cmd.group
        if (!map[g]) { map[g] = { name: g, items: [] }; groups.push(map[g]) }
        map[g].items.push(item)
      }
      return { flat, groups }
    })

    const flatResults = computed(() => results.value.flat)
    const groupedResults = computed(() => results.value.groups)

    const move = (dir: number) => {
      const len = flatResults.value.length
      if (!len) return
      active.value = (active.value + dir + len) % len
    }

    const run = (cmd: Command) => {
      close()
      cmd.run()
    }

    const runActive = () => {
      const item = flatResults.value[active.value]
      if (item) run(item.cmd)
    }

    // 输入变化时把高亮重置到第一项
    watch(query, () => { active.value = 0 })

    // active 变化时把选中项滚动进可视区
    watch(active, () => {
      nextTick(() => {
        listRef.value?.querySelector('.cmdk-item.is-active')?.scrollIntoView({ block: 'nearest' })
      })
    })

    // 打开/关闭：锁定背景滚动、聚焦输入框、重置状态
    watch(isOpen, value => {
      if (value) {
        query.value = ''
        active.value = 0
        document.body.style.overflow = 'hidden'
        nextTick(() => inputRef.value?.focus())
      } else {
        document.body.style.overflow = ''
      }
    })

    // 全局快捷键 ⌘K / Ctrl+K 唤起
    const onKeydown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault()
        toggle()
      }
    }

    onMounted(() => window.addEventListener('keydown', onKeydown))
    onBeforeUnmount(() => {
      window.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    })

    return {
      isOpen,
      themeClass,
      query,
      active,
      inputRef,
      listRef,
      flatResults,
      groupedResults,
      move,
      run,
      runActive,
      close
    }
  }
})
</script>

<style scoped>
.cmdk-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 14vh;
  background: rgba(20, 18, 38, 0.32);
  backdrop-filter: blur(6px) saturate(120%);
  -webkit-backdrop-filter: blur(6px) saturate(120%);
}

.cmdk-panel {
  width: min(640px, calc(100vw - 32px));
  max-height: 64vh;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(124, 92, 255, 0.16);
  border-radius: 18px;
  box-shadow:
    0 32px 80px rgba(40, 30, 90, 0.28),
    0 2px 8px rgba(40, 30, 90, 0.1);
  backdrop-filter: blur(28px) saturate(160%);
  -webkit-backdrop-filter: blur(28px) saturate(160%);
  overflow: hidden;
}

/* 搜索行 */
.cmdk-search {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(20, 18, 38, 0.07);
}

.cmdk-search-icon {
  color: #8b80b8;
  flex-shrink: 0;
}

.cmdk-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  color: #221d3f;
  letter-spacing: -0.01em;
}

.cmdk-input::placeholder { color: #a39db8; }

.cmdk-esc {
  flex-shrink: 0;
  font-size: 11px;
  font-family: inherit;
  color: #8b80b8;
  background: rgba(20, 18, 38, 0.06);
  border: 1px solid rgba(20, 18, 38, 0.08);
  border-radius: 6px;
  padding: 3px 7px;
  line-height: 1;
}

/* 列表 */
.cmdk-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.cmdk-group { margin-bottom: 4px; }

.cmdk-group-title {
  padding: 8px 12px 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #9a93b3;
}

.cmdk-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border: none;
  background: transparent;
  border-radius: 11px;
  cursor: pointer;
  text-align: left;
  color: #2a2550;
  transition: background 0.12s ease;
}

.cmdk-item.is-active {
  background: linear-gradient(90deg, rgba(124, 92, 255, 0.14), rgba(124, 92, 255, 0.08));
}

.cmdk-item-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: rgba(124, 92, 255, 0.1);
  color: #7c5cff;
  transition: background 0.12s ease, color 0.12s ease;
}

.cmdk-item.is-active .cmdk-item-icon {
  background: #7c5cff;
  color: #fff;
}

.cmdk-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.cmdk-item-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cmdk-hl {
  background: transparent;
  color: #7c5cff;
  font-weight: 700;
}

.cmdk-item-sub {
  font-size: 12px;
  color: #948cb0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cmdk-item-enter {
  flex-shrink: 0;
  font-size: 13px;
  color: #7c5cff;
  opacity: 0.8;
}

.cmdk-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 46px 20px;
  color: #a39db8;
  font-size: 13.5px;
}

/* 底部状态栏 */
.cmdk-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 18px;
  border-top: 1px solid rgba(20, 18, 38, 0.07);
  font-size: 11.5px;
  color: #8b80b8;
}

.cmdk-foot-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.cmdk-footer kbd {
  font-family: inherit;
  font-size: 11px;
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  color: #6f6593;
  background: rgba(20, 18, 38, 0.06);
  border: 1px solid rgba(20, 18, 38, 0.08);
  border-radius: 5px;
  line-height: 1;
}

.cmdk-foot-brand {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #9a93b3;
}

.cmdk-foot-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff8a4a 0%, #b8a4ff 50%, #6a8eff 100%);
}

/* ===== 暗色 ===== */
.cmdk-overlay.theme-dark {
  background: rgba(0, 0, 0, 0.55);
}

.theme-dark .cmdk-panel {
  background: rgba(22, 22, 27, 0.9);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow:
    0 32px 80px rgba(0, 0, 0, 0.6),
    0 0 0 1px rgba(255, 255, 255, 0.04);
}

.theme-dark .cmdk-search { border-bottom-color: rgba(255, 255, 255, 0.08); }
.theme-dark .cmdk-search-icon { color: #8e86b5; }
.theme-dark .cmdk-input { color: #f1eeff; }
.theme-dark .cmdk-input::placeholder { color: #6f6790; }
.theme-dark .cmdk-esc {
  color: #9b93bd;
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(255, 255, 255, 0.1);
}

.theme-dark .cmdk-group-title { color: #7b739c; }
.theme-dark .cmdk-item { color: #e9e6f7; }
.theme-dark .cmdk-item.is-active {
  background: linear-gradient(90deg, rgba(124, 92, 255, 0.26), rgba(124, 92, 255, 0.12));
}
.theme-dark .cmdk-item-icon {
  background: rgba(124, 92, 255, 0.18);
  color: #b3a0ff;
}
.theme-dark .cmdk-item.is-active .cmdk-item-icon {
  background: #7c5cff;
  color: #fff;
}
.theme-dark .cmdk-hl { color: #b3a0ff; }
.theme-dark .cmdk-item-sub { color: #837ba5; }
.theme-dark .cmdk-empty { color: #6f6790; }
.theme-dark .cmdk-footer {
  border-top-color: rgba(255, 255, 255, 0.08);
  color: #8e86b5;
}
.theme-dark .cmdk-footer kbd {
  color: #b0a8d0;
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(255, 255, 255, 0.1);
}
.theme-dark .cmdk-foot-brand { color: #8e86b5; }

/* ===== 过渡动画 ===== */
.cmdk-enter-active,
.cmdk-leave-active { transition: opacity 0.2s ease; }
.cmdk-enter-from,
.cmdk-leave-to { opacity: 0; }

.cmdk-enter-active .cmdk-panel { animation: cmdk-pop 0.28s cubic-bezier(0.16, 1, 0.3, 1); }
.cmdk-leave-active .cmdk-panel { animation: cmdk-pop 0.18s cubic-bezier(0.16, 1, 0.3, 1) reverse; }

@keyframes cmdk-pop {
  from { opacity: 0; transform: translateY(-10px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .cmdk-enter-active .cmdk-panel,
  .cmdk-leave-active .cmdk-panel { animation: none; }
}

@media (max-width: 520px) {
  .cmdk-overlay { padding-top: 8vh; }
  .cmdk-foot-brand { display: none; }
}
</style>
