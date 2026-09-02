<template>
  <el-dialog
    v-model="visibleProxy"
    width="680px"
    append-to-body
    class="meeting-export-dialog"
    title="导出模板设置"
  >
    <div class="dialog-copy">
      你可以按使用场景自由组合导出内容，这次选择会记住为你的默认模板。
    </div>

    <div class="format-row">
      <span>导出格式</span>
      <el-radio-group v-model="form.format" size="small">
        <el-radio-button label="txt">TXT</el-radio-button>
        <el-radio-button label="md">Markdown</el-radio-button>
        <el-radio-button label="docx">DOCX</el-radio-button>
      </el-radio-group>
    </div>

    <div class="preset-row">
      <el-button size="small" @click="applyPreset('full')">标准模板</el-button>
      <el-button size="small" @click="applyPreset('brief')">精简模板</el-button>
      <small>标准模板包含全部信息，精简模板更适合快速汇报。</small>
    </div>

    <div class="template-grid">
      <el-checkbox v-model="form.template.includeMeta">基础信息（场景/时间/状态）</el-checkbox>
      <el-checkbox v-model="form.template.includeSummary">纪要摘要</el-checkbox>
      <el-checkbox v-model="form.template.includeKeywords">关键词</el-checkbox>
      <el-checkbox v-model="form.template.includeStructuredSections">结构化纪要</el-checkbox>
      <el-checkbox v-model="form.template.includeRoleInsights">发言角色分析</el-checkbox>
      <el-checkbox v-model="form.template.includeTodoChains">待办责任链分析</el-checkbox>
      <el-checkbox v-model="form.template.includeDecisionInsights">结论与待确认事项</el-checkbox>
      <el-checkbox v-model="form.template.includeTodos">待办事项列表</el-checkbox>
      <el-checkbox v-model="form.template.includeSpeakerTranscript">发言人纪要</el-checkbox>
      <el-checkbox v-model="form.template.includeSpeakerBlocks">整理后发言块</el-checkbox>
      <el-checkbox v-model="form.template.includeFullTranscript">全文转写</el-checkbox>
    </div>

    <template #footer>
      <div class="dialog-actions">
        <el-button @click="visibleProxy = false">取消</el-button>
        <el-button type="primary" @click="confirmExport">按模板导出</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
import { computed, defineComponent, reactive, watch } from 'vue'
import { MeetingExportTemplate } from '@/api/meeting'

type ExportFormat = 'txt' | 'md' | 'docx'

interface ExportTemplateDialogPayload {
  format: ExportFormat
  template: MeetingExportTemplate
}

const createDefaultTemplate = (): MeetingExportTemplate => ({
  includeMeta: true,
  includeSummary: true,
  includeKeywords: true,
  includeStructuredSections: true,
  includeRoleInsights: true,
  includeTodoChains: true,
  includeDecisionInsights: true,
  includeTodos: true,
  includeSpeakerTranscript: true,
  includeSpeakerBlocks: true,
  includeFullTranscript: true
})

const mergeTemplate = (template?: MeetingExportTemplate): MeetingExportTemplate => ({
  ...createDefaultTemplate(),
  ...(template || {})
})

export default defineComponent({
  name: 'MeetingExportTemplateDialog',
  props: {
    modelValue: {
      type: Boolean,
      required: true
    },
    defaultFormat: {
      type: String as () => ExportFormat,
      default: 'docx'
    },
    template: {
      type: Object as () => MeetingExportTemplate | undefined,
      default: undefined
    }
  },
  emits: ['update:modelValue', 'confirm'],
  setup (props, { emit }) {
    const form = reactive<ExportTemplateDialogPayload>({
      format: props.defaultFormat,
      template: mergeTemplate(props.template)
    })

    const visibleProxy = computed({
      get: () => props.modelValue,
      set: (value: boolean) => emit('update:modelValue', value)
    })

    const hydrateForm = () => {
      form.format = props.defaultFormat
      form.template = mergeTemplate(props.template)
    }

    const applyPreset = (preset: 'full' | 'brief') => {
      if (preset === 'full') {
        form.template = createDefaultTemplate()
        return
      }
      form.template = {
        includeMeta: true,
        includeSummary: true,
        includeKeywords: true,
        includeStructuredSections: true,
        includeRoleInsights: false,
        includeTodoChains: true,
        includeDecisionInsights: true,
        includeTodos: true,
        includeSpeakerTranscript: false,
        includeSpeakerBlocks: false,
        includeFullTranscript: false
      }
    }

    const confirmExport = () => {
      const payload: ExportTemplateDialogPayload = {
        format: form.format,
        template: { ...form.template }
      }
      emit('confirm', payload)
      visibleProxy.value = false
    }

    watch(
      () => props.modelValue,
      visible => {
        if (visible) {
          hydrateForm()
        }
      }
    )

    watch(
      () => props.template,
      () => {
        if (props.modelValue) {
          hydrateForm()
        }
      },
      { deep: true }
    )

    return {
      visibleProxy,
      form,
      applyPreset,
      confirmExport
    }
  }
})
</script>

<style scoped>
.dialog-copy {
  margin-bottom: 14px;
  color: #5c6b94;
  line-height: 1.7;
  font-size: 13px;
}

.format-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.85);
  border: 1px solid rgba(145, 171, 255, 0.26);
}

.format-row span {
  font-size: 13px;
  color: #30406d;
  font-weight: 600;
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.preset-row small {
  color: #6a78a4;
  font-size: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(137, 164, 255, 0.22);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:global(.dark-mode) .dialog-copy {
  color: #c3d5ff;
}

:global(.dark-mode) .format-row {
  background: rgba(38, 64, 132, 0.36);
  border-color: rgba(114, 155, 255, 0.36);
}

:global(.dark-mode) .format-row span {
  color: #e3edff;
}

:global(.dark-mode) .preset-row small {
  color: #9fb8f7;
}

:global(.dark-mode) .template-grid {
  background: rgba(19, 33, 70, 0.88);
  border-color: rgba(103, 138, 230, 0.45);
}

@media (max-width: 720px) {
  .template-grid {
    grid-template-columns: 1fr;
  }

  .format-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
