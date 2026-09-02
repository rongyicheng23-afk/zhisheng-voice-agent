<template>
  <!-- el-dialog 组件，显示传入的 message 文本 -->
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="700"
    :before-close="handleClose"
  >
    <!-- 显示传入的 message 内容 -->
    <span>{{ message }}</span>
    <template #footer>
      <div class="dialog-footer">
        <!-- 取消按钮，关闭对话框 -->
        <el-button @click="dialogVisible = false">取消</el-button>
        <!-- 确认按钮，关闭对话框 -->
        <el-button type="primary" @click="dialogVisible = false">
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
// 引入 vue 的响应式和类型定义方法
import { ref, defineProps, defineExpose } from 'vue'

// 定义组件接收的 props
const props = defineProps<{
  // 要显示的文本内容
  message: string
  // 可选的对话框标题
  title?: string
}>()

// 控制对话框显示/隐藏的响应式变量
const dialogVisible = ref(false)

// 打开对话框的方法，供父组件调用
const open = () => {
  dialogVisible.value = true
}

// 关闭对话框的方法，供父组件调用
const close = () => {
  dialogVisible.value = false
}

// 关闭对话框的逻辑
const handleClose = (done: () => void) => {
  done()
}

// 向父组件暴露 open 和 close 方法
defineExpose({
  open,
  close
})
</script>

<style scoped>
/* Dialog 样式，沿用全站玻璃拟态面板设计 */
:deep(.el-dialog) {
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
  border: none;
  background: white;
  overflow: hidden;
}

:deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #2979ff 100%);
  border-radius: 16px 16px 0 0;
  padding: 24px 32px;
  border-bottom: none;
  position: relative;
}

:deep(.el-dialog__header::after) {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
}

:deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: white;
  letter-spacing: 0.5px;
}

:deep(.el-dialog__headerbtn) {
  top: 24px;
  right: 32px;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 18px;
}

:deep(.el-dialog__body) {
  padding: 32px;
  color: #2c3e50;
  font-size: 16px;
  line-height: 1.8;
  background: white;
  min-height: 120px;
  max-height: 400px;
  overflow-y: auto;
}

/* 内容文字样式 */
:deep(.el-dialog__body span) {
  display: block;
  background: #f8f9fa;
  padding: 20px;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  white-space: pre-wrap;
  word-wrap: break-word;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

:deep(.el-dialog__footer) {
  background: #f8f9fa;
  border-radius: 0 0 16px 16px;
  padding: 20px 32px;
  border-top: 1px solid #e9ecef;
}

:deep(.dialog-footer) {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}

:deep(.el-button) {
  border-radius: 10px;
  padding: 12px 24px;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.3s ease;
  min-width: 80px;
}

:deep(.el-button--default) {
  background: white;
  border: 2px solid #e9ecef;
  color: #6c757d;
}

:deep(.el-button--default:hover) {
  background: #f8f9fa;
  border-color: #409EFF;
  color: #409EFF;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64,158,255,0.15);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #409EFF 0%, #2979ff 100%);
  border: 2px solid #409EFF;
  color: white;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #409EFF 100%);
  border-color: #66b1ff;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64,158,255,0.3);
}

:deep(.el-button--primary:active) {
  transform: translateY(0);
}

/* 滚动条样式 */
:deep(.el-dialog__body::-webkit-scrollbar) {
  width: 6px;
}

:deep(.el-dialog__body::-webkit-scrollbar-track) {
  background: #f1f1f1;
  border-radius: 3px;
}

:deep(.el-dialog__body::-webkit-scrollbar-thumb) {
  background: #c1c1c1;
  border-radius: 3px;
}

:deep(.el-dialog__body::-webkit-scrollbar-thumb:hover) {
  background: #a8a8a8;
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-dialog) {
  background: linear-gradient(145deg, rgba(15, 25, 47, 0.98) 0%, rgba(20, 33, 60, 0.96) 100%);
  box-shadow: 0 22px 48px rgba(11, 11, 13, 0.4);
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-dialog__body) {
  background: transparent;
  color: #e8eefc;
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-dialog__body span) {
  background: rgba(18, 18, 21, 0.88);
  color: #e8eefc;
  border-left-color: #78a4ff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-dialog__footer) {
  background: rgba(18, 18, 21, 0.88);
  border-top-color: rgba(255, 255, 255, 0.16);
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-button--default) {
  background: rgba(15, 25, 47, 0.92);
  border-color: rgba(255, 255, 255, 0.18);
  color: #d7e4ff;
}

:global(html[data-auth-theme-mode='dark']) :deep(.el-button--default:hover) {
  background: rgba(20, 33, 60, 0.98);
  color: #9fc3ff;
}
</style>
