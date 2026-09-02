<template>
  <div class="app-shell container-fluid px-0 flex-shrink-0" :class="{ 'app-shell--bare': hideNavMenu }">
    <global-header v-if="!hideNavMenu" :user="currentUser"></global-header>
    <div class="common-layout">
      <el-container>
        <main class="app-main">
          <router-view v-slot="{ Component }">
            <component :is="Component" :key="$route.path" />
          </router-view>
        </main>
      </el-container>
    </div>
  </div>
  <footer v-if="!hideNavMenu" class="app-footer text-center py-4 mt-auto">
    <small>
      <ul class="list-inline mb-0">
        <li class="list-inline-item">© 2025 layout特辣油团队</li>
        <li class="list-inline-item">语音</li>
      </ul>
    </small>
  </footer>
  <command-palette />
</template>

<script lang="ts">
import { defineComponent, computed } from 'vue'
import { useStore } from 'vuex'
import { GlobalDataProps } from './store/types'
import { useRoute } from 'vue-router'
import 'bootstrap/dist/css/bootstrap.min.css'
import GlobalHeader from './components/GlobalHeader.vue'
import CommandPalette from './components/CommandPalette.vue'

export default defineComponent({
  name: 'App',
  components: {
    GlobalHeader,
    CommandPalette
  },
  setup () {
    const store = useStore<GlobalDataProps>()
    const route = useRoute()
    const currentUser = computed(() => store.state.user)
    const isLoading = computed(() => store.state.loading)
    const hideNavMenu = computed(() => Boolean(route.meta.hideNavMenu))

    return {
      currentUser,
      isLoading,
      hideNavMenu
    }
  }
})
</script>

<style>
.app-shell {
  min-height: calc(100vh - 72px);
  background: #ffffff;
  transition: background 0.45s ease, color 0.35s ease;
}

.common-layout .el-container {
  width: 100%;
  align-items: flex-start;
}

.app-main {
  flex: 1;
  min-width: 0;
  width: 100%;
  background: transparent;
}

.app-footer {
  position: relative;
  z-index: 1;
  background: transparent;
  color: #6b7280;
  transition: background 0.45s ease, color 0.35s ease;
}

html[data-auth-theme-mode='dark'] body {
  background: #0a0a0c;
}

html[data-auth-theme-mode='dark'] .app-shell {
  background: #0a0a0c;
}

html[data-auth-theme-mode='dark'] .app-footer {
  background: #050b16;
  color: #93a4c2;
}

html[data-auth-theme-mode='dark'] .el-popper,
html[data-auth-theme-mode='dark'] .el-select__popper,
html[data-auth-theme-mode='dark'] .el-picker__popper,
html[data-auth-theme-mode='dark'] .el-dropdown__popper,
html[data-auth-theme-mode='dark'] .el-tooltip__popper {
  background: rgba(22, 22, 26, 0.96) !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
  box-shadow: 0 18px 36px rgba(11, 11, 13, 0.34) !important;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

html[data-auth-theme-mode='dark'] .el-popper.is-light,
html[data-auth-theme-mode='dark'] .el-dropdown__popper.el-popper,
html[data-auth-theme-mode='dark'] .el-dropdown-menu {
  background: rgba(22, 22, 26, 0.96) !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

html[data-auth-theme-mode='dark'] .el-select-dropdown__item,
html[data-auth-theme-mode='dark'] .el-dropdown-menu__item,
html[data-auth-theme-mode='dark'] .el-select-dropdown__empty,
html[data-auth-theme-mode='dark'] .el-cascader-node {
  color: #e8eefc !important;
}

html[data-auth-theme-mode='dark'] .el-dropdown-menu__item:not(.is-disabled) {
  color: #e8eefc !important;
}

html[data-auth-theme-mode='dark'] .el-dropdown-menu__item.is-disabled {
  color: rgba(159, 176, 204, 0.45) !important;
}

html[data-auth-theme-mode='dark'] .el-select-dropdown__item.hover,
html[data-auth-theme-mode='dark'] .el-select-dropdown__item:hover,
html[data-auth-theme-mode='dark'] .el-dropdown-menu__item:hover,
html[data-auth-theme-mode='dark'] .el-cascader-node:hover {
  background: rgba(53, 83, 168, 0.28) !important;
  color: #f4f7ff !important;
}

html[data-auth-theme-mode='dark'] .el-select-dropdown__item.selected {
  color: #9fc3ff !important;
  font-weight: 600;
}

html[data-auth-theme-mode='dark'] .el-popper__arrow::before {
  background: rgba(22, 22, 26, 0.96) !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

html[data-auth-theme-mode='dark'] .el-dropdown-menu__item--divided::before {
  background: rgba(255, 255, 255, 0.14) !important;
}

html[data-auth-theme-mode='dark'] .el-radio__label,
html[data-auth-theme-mode='dark'] .el-checkbox__label,
html[data-auth-theme-mode='dark'] .el-form-item__label {
  color: #d9e5ff !important;
}

html[data-auth-theme-mode='dark'] .el-radio__inner,
html[data-auth-theme-mode='dark'] .el-checkbox__inner {
  background: rgba(18, 18, 21, 0.92) !important;
  border-color: rgba(255, 255, 255, 0.3) !important;
}

html[data-auth-theme-mode='dark'] .el-tag {
  background: rgba(34, 34, 38, 0.88) !important;
  border-color: rgba(255, 255, 255, 0.22) !important;
  color: #e8eefc !important;
}

html[data-auth-theme-mode='dark'] .el-table__empty-block,
html[data-auth-theme-mode='dark'] .el-empty__description p,
html[data-auth-theme-mode='dark'] .el-empty__description span {
  color: #9fb0cc !important;
  background: transparent !important;
}

html[data-auth-theme-mode='dark'] .el-loading-mask {
  background-color: rgba(15, 15, 18, 0.72) !important;
}

.history-drawer-overlay,
.meeting-drawer-overlay,
.history-drawer,
.meeting-drawer {
  z-index: 2400 !important;
}

.history-drawer .el-drawer,
.meeting-drawer .el-drawer {
  z-index: 2401 !important;
}
</style>
