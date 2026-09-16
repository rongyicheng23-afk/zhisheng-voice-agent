<template>
  <nav class="vf-nav">
    <div class="vf-nav-inner" :class="{ 'vf-nav-inner--compact': hideNavMenu }">
      <router-link to="/" class="vf-brand">
        <span class="vf-brand-mark" aria-hidden="true">
          <span class="vf-brand-dot"></span>
        </span>
        <span class="vf-brand-text">Voice Factory</span>
      </router-link>

      <div v-if="!hideNavMenu" class="vf-menu">
        <router-link to="/" class="vf-menu-item" active-class="is-active">总控台</router-link>
        <router-link to="/asr" class="vf-menu-item" active-class="is-active">录音转文字</router-link>
        <router-link to="/RealtimeVoice" class="vf-menu-item" active-class="is-active">实时语音</router-link>
        <router-link to="/TextToVoice" class="vf-menu-item" active-class="is-active">文字转语音</router-link>
        <router-link to="/VoicePrintCompare" class="vf-menu-item" active-class="is-active">声纹对比</router-link>
        <router-link to="/MeetingNotes" class="vf-menu-item" active-class="is-active">智能纪要</router-link>
        <router-link to="/knowledge" class="vf-menu-item" active-class="is-active">知识库</router-link>
        <router-link v-if="canUseManagement" to="/admin" class="vf-menu-item" active-class="is-active">管理中心</router-link>
      </div>

      <div class="vf-actions">
        <button
          type="button"
          class="vf-search-trigger"
          aria-label="打开命令面板"
          :title="`命令面板 ${shortcutLabel}`"
          @click="openCommand"
        >
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="7" />
            <path d="m21 21-4.3-4.3" />
          </svg>
          <span class="vf-search-label">搜索</span>
          <kbd class="vf-search-kbd">{{ shortcutLabel }}</kbd>
        </button>
        <button
          type="button"
          class="vf-icon-btn"
          :aria-label="themeButtonLabel"
          :title="themeButtonLabel"
          @click="toggleTheme"
        >
          <svg v-if="isDark" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
          </svg>
        </button>

        <template v-if="isLoggedIn">
          <el-dropdown trigger="click" @command="handleCommand">
            <button class="vf-profile" type="button">
              <span class="vf-avatar">{{ avatarText }}</span>
              <span class="vf-profile-name">{{ displayName }}</span>
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人主页</el-dropdown-item>
                <el-dropdown-item v-if="canUseManagement" command="admin">管理中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>注销</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="vf-link">登录</router-link>
          <router-link to="/register" class="vf-cta">
            <span>注册</span>
            <span class="vf-cta-arrow" aria-hidden="true">→</span>
          </router-link>
        </template>
      </div>
    </div>
  </nav>
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, PropType, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { GlobalDataProps, UserProps } from '@/store/types'
import { useAuthThemeMode } from '@/composables/useAuthThemeMode'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { getAdminProfile } from '@/api/admin'

export default defineComponent({
  name: 'GlobalHeader',
  props: {
    user: {
      type: Object as PropType<UserProps>,
      required: true
    }
  },
  setup () {
    const router = useRouter()
    const route = useRoute()
    const store = useStore<GlobalDataProps>()
    const { isDark, themeButtonLabel, themeButtonText, toggleTheme } = useAuthThemeMode()
    const { open: openCommand } = useCommandPalette()
    const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || '')
    const shortcutLabel = isMac ? '⌘K' : 'Ctrl K'
    const isLoggedIn = computed(() => store.getters.isLoggedIn)
    const canUseManagement = ref(false)
    const hideNavMenu = computed(() => Boolean(route.meta.hideNavMenu))
    const displayName = computed(() => store.state.user?.nickName || store.state.user?.email || '用户')
    const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

    const handleCommand = (command: string) => {
      if (command === 'profile') {
        router.push('/profile')
        return
      }
      if (command === 'admin') { router.push('/admin'); return }
      if (command === 'logout') {
        store.commit('logout')
        ElMessage.success('已退出登录')
        router.push('/')
      }
    }

    const refreshManagementAccess = async () => {
      if (!isLoggedIn.value) { canUseManagement.value = false; return }
      try {
        const response = await getAdminProfile()
        if (response.code === 200) {
          canUseManagement.value = response.data.canManageKnowledge || response.data.canObserveSystem || response.data.canManageUsers
        }
      } catch { canUseManagement.value = false }
    }
    onMounted(refreshManagementAccess)
    watch(isLoggedIn, refreshManagementAccess)

    return {
      isLoggedIn,
      canUseManagement,
      isDark,
      hideNavMenu,
      displayName,
      avatarText,
      themeButtonLabel,
      themeButtonText,
      toggleTheme,
      handleCommand,
      openCommand,
      shortcutLabel
    }
  }
})
</script>

<style scoped>
.vf-nav {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: transparent;
  backdrop-filter: blur(16px) saturate(140%);
  -webkit-backdrop-filter: blur(16px) saturate(140%);
  border-bottom: 1px solid rgba(10, 10, 10, 0.06);
  transition: border-color 0.35s ease, background 0.35s ease;
}

.vf-nav-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  max-width: 1280px;
  height: 60px;
  margin: 0 auto;
  padding: 0 28px;
}

.vf-nav-inner--compact { justify-content: space-between; }

/* logo */
.vf-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: #0a0a0a;
  text-decoration: none;
  flex-shrink: 0;
}

.vf-brand-mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: #0a0a0a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.35s ease, transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.vf-brand:hover .vf-brand-mark { transform: rotate(8deg); }

.vf-brand-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff8a4a 0%, #b8a4ff 50%, #6a8eff 100%);
}

/* 菜单 */
.vf-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  height: 100%;
}

.vf-menu-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 100%;
  padding: 0 14px;
  font-size: 14px;
  font-weight: 500;
  color: rgba(10, 10, 10, 0.6);
  text-decoration: none;
  transition: color 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.vf-menu-item:hover { color: #0a0a0a; }

.vf-menu-item.is-active { color: #0a0a0a; }

.vf-menu-item.is-active::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: -1px;
  height: 1.5px;
  background: #0a0a0a;
  border-radius: 1px;
}

/* 右侧操作区 */
.vf-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}

.vf-search-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px 0 12px;
  border-radius: 999px;
  background: rgba(10, 10, 10, 0.04);
  border: 1px solid rgba(10, 10, 10, 0.06);
  color: rgba(10, 10, 10, 0.55);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
}

.vf-search-trigger:hover {
  background: rgba(10, 10, 10, 0.06);
  border-color: rgba(10, 10, 10, 0.12);
  color: #0a0a0a;
}

.vf-search-label { font-weight: 500; }

.vf-search-kbd {
  font-family: inherit;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(10, 10, 10, 0.06);
  border: 1px solid rgba(10, 10, 10, 0.05);
  color: rgba(10, 10, 10, 0.5);
  line-height: 1;
}

@media (max-width: 700px) {
  .vf-search-label,
  .vf-search-kbd { display: none; }
  .vf-search-trigger { padding: 0; width: 36px; justify-content: center; }
}

.vf-icon-btn {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: transparent;
  border: 1px solid transparent;
  color: rgba(10, 10, 10, 0.7);
  cursor: pointer;
  transition: background 0.2s cubic-bezier(0.16, 1, 0.3, 1), color 0.2s, border-color 0.2s;
}

.vf-icon-btn:hover {
  background: rgba(10, 10, 10, 0.05);
  color: #0a0a0a;
}

.vf-link {
  padding: 0 14px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  color: rgba(10, 10, 10, 0.75);
  text-decoration: none;
  border-radius: 999px;
  transition: color 0.2s, background 0.2s;
}

.vf-link:hover {
  color: #0a0a0a;
  background: rgba(10, 10, 10, 0.04);
}

.vf-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  background: #0a0a0a;
  color: #fafafa;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.2s;
}

.vf-cta:hover { transform: translateY(-1px); opacity: 0.92; }

.vf-cta-arrow {
  display: inline-block;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.vf-cta:hover .vf-cta-arrow { transform: translateX(3px); }

.vf-profile {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 12px 0 4px;
  border-radius: 999px;
  background: transparent;
  border: 1px solid rgba(10, 10, 10, 0.08);
  color: #0a0a0a;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.vf-profile:hover {
  background: rgba(10, 10, 10, 0.04);
  border-color: rgba(10, 10, 10, 0.16);
}

.vf-profile:focus { outline: none; }

.vf-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff8a4a 0%, #b8a4ff 60%, #6a8eff 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.vf-profile-name {
  max-width: 100px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 深色模式 */
:global(html[data-auth-theme-mode='dark'] .vf-nav) {
  border-bottom-color: rgba(250, 250, 250, 0.06);
}

:global(html[data-auth-theme-mode='dark'] .vf-brand) {
  color: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-brand-mark) {
  background: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-menu-item) {
  color: rgba(250, 250, 250, 0.55);
}

:global(html[data-auth-theme-mode='dark'] .vf-menu-item:hover),
:global(html[data-auth-theme-mode='dark'] .vf-menu-item.is-active) {
  color: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-menu-item.is-active::after) {
  background: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-icon-btn) {
  color: rgba(250, 250, 250, 0.6);
}

:global(html[data-auth-theme-mode='dark'] .vf-search-trigger) {
  background: rgba(250, 250, 250, 0.05);
  border-color: rgba(250, 250, 250, 0.08);
  color: rgba(250, 250, 250, 0.55);
}

:global(html[data-auth-theme-mode='dark'] .vf-search-trigger:hover) {
  background: rgba(250, 250, 250, 0.08);
  border-color: rgba(250, 250, 250, 0.14);
  color: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-search-kbd) {
  background: rgba(250, 250, 250, 0.08);
  border-color: rgba(250, 250, 250, 0.06);
  color: rgba(250, 250, 250, 0.5);
}

:global(html[data-auth-theme-mode='dark'] .vf-icon-btn:hover) {
  background: rgba(250, 250, 250, 0.06);
  color: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-link) {
  color: rgba(250, 250, 250, 0.7);
}

:global(html[data-auth-theme-mode='dark'] .vf-link:hover) {
  color: #fafafa;
  background: rgba(250, 250, 250, 0.05);
}

:global(html[data-auth-theme-mode='dark'] .vf-cta) {
  background: #fafafa;
  color: #0a0a0a;
}

:global(html[data-auth-theme-mode='dark'] .vf-profile) {
  border-color: rgba(250, 250, 250, 0.1);
  color: #fafafa;
}

:global(html[data-auth-theme-mode='dark'] .vf-profile:hover) {
  background: rgba(250, 250, 250, 0.04);
  border-color: rgba(250, 250, 250, 0.18);
}

/* 响应式 */
@media (max-width: 1100px) {
  .vf-nav-inner { gap: 18px; padding: 0 18px; }
  .vf-menu-item { padding: 0 10px; font-size: 13.5px; }
}

@media (max-width: 820px) {
  .vf-menu {
    order: 3;
    flex-basis: 100%;
    height: 40px;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .vf-menu::-webkit-scrollbar { display: none; }
  .vf-nav-inner {
    flex-wrap: wrap;
    height: auto;
    padding-top: 12px;
    padding-bottom: 8px;
    row-gap: 4px;
  }
}

@media (max-width: 520px) {
  .vf-profile-name { display: none; }
}
</style>
