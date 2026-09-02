import { ref } from 'vue'

// 模块级共享状态：顶栏入口按钮与命令面板组件通过它解耦通信
const isOpen = ref(false)

export const useCommandPalette = () => {
  const open = () => { isOpen.value = true }
  const close = () => { isOpen.value = false }
  const toggle = () => { isOpen.value = !isOpen.value }
  return { isOpen, open, close, toggle }
}
