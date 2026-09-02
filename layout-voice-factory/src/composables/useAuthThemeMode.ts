import { computed, ref, watch } from 'vue'

const STORAGE_KEY = 'auth-theme-mode'
const sharedMode = ref<'light' | 'dark'>(
  localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light'
)

watch(sharedMode, value => {
  localStorage.setItem(STORAGE_KEY, value)
  document.documentElement.setAttribute('data-auth-theme-mode', value)
}, { immediate: true })

export const useAuthThemeMode = () => {
  const isDark = computed(() => sharedMode.value === 'dark')
  const themeClass = computed(() => isDark.value ? 'theme-dark' : 'theme-light')
  const themeButtonLabel = computed(() => isDark.value ? '切换白天' : '切换夜晚')
  const themeButtonText = computed(() => isDark.value ? '夜间' : '白天')

  const toggleTheme = () => {
    sharedMode.value = isDark.value ? 'light' : 'dark'
  }

  return {
    isDark,
    themeClass,
    themeButtonLabel,
    themeButtonText,
    toggleTheme
  }
}
