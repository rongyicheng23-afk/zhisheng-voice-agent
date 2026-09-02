import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

export const useAuthBackgroundMotion = (initialX = 70, initialY = 36) => {
  const pageRef = ref<HTMLElement | null>(null)
  const state = reactive({
    targetX: initialX,
    targetY: initialY,
    currentX: initialX,
    currentY: initialY,
    flowX: 0,
    flowY: 0,
    flowSoft: 0,
    flowRotate: 0
  })

  let frameId = 0
  let startTime = 0

  const animate = (timestamp: number) => {
    if (!startTime) {
      startTime = timestamp
    }
    const elapsed = (timestamp - startTime) / 1000

    state.currentX += (state.targetX - state.currentX) * 0.055
    state.currentY += (state.targetY - state.currentY) * 0.055
    state.flowX = Math.sin(elapsed * 0.55) * 18 + Math.cos(elapsed * 0.21) * 8
    state.flowY = Math.cos(elapsed * 0.42) * 14 + Math.sin(elapsed * 0.17) * 6
    state.flowSoft = Math.sin(elapsed * 0.28) * 22
    state.flowRotate = Math.sin(elapsed * 0.2) * 2.4

    frameId = window.requestAnimationFrame(animate)
  }

  onMounted(() => {
    frameId = window.requestAnimationFrame(animate)
  })

  onBeforeUnmount(() => {
    window.cancelAnimationFrame(frameId)
  })

  const handlePointerMove = (event: MouseEvent) => {
    if (!pageRef.value) {
      return
    }
    const rect = pageRef.value.getBoundingClientRect()
    state.targetX = Math.min(100, Math.max(0, ((event.clientX - rect.left) / rect.width) * 100))
    state.targetY = Math.min(100, Math.max(0, ((event.clientY - rect.top) / rect.height) * 100))
  }

  const handlePointerLeave = () => {
    state.targetX = initialX
    state.targetY = initialY
  }

  const pageStyle = computed(() => ({
    '--pointer-x': `${state.currentX}%`,
    '--pointer-y': `${state.currentY}%`,
    '--drift-x': `${(state.currentX - 50) * 0.9 + state.flowX}px`,
    '--drift-y': `${(state.currentY - 50) * 0.7 + state.flowY}px`,
    '--drift-x-reverse': `${(state.currentX - 50) * -0.65 - state.flowX * 0.6}px`,
    '--drift-y-reverse': `${(state.currentY - 50) * -0.55 - state.flowY * 0.5}px`,
    '--flow-x': `${state.flowX}px`,
    '--flow-y': `${state.flowY}px`,
    '--flow-soft': `${state.flowSoft}px`,
    '--flow-rotate': `${state.flowRotate}deg`
  }))

  return {
    pageRef,
    pageStyle,
    handlePointerMove,
    handlePointerLeave
  }
}
