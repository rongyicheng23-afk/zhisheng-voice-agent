<template>
  <flow-background-panel>
    <div class="meeting-detail-page container-md" style="margin-top: 10px;">
      <div class="detail-card" v-loading="detailLoading">
        <div class="detail-head">
          <div class="detail-head-main">
            <el-button type="primary" link class="back-btn" @click="goBack">
              <el-icon class="el-icon--left"><ArrowLeft /></el-icon>
              返回智能纪要
            </el-button>
            <p class="detail-badge">纪要详情</p>
            <h2>{{ detail.title || '智能纪要详情' }}</h2>
            <p class="detail-desc">
              在这里查看完整摘要、全文转写、发言人纪要、原始音频和逐段发言片段，方便回放、核对和后续整理。
            </p>
          </div>
          <div class="detail-head-side">
            <span>场景类型</span>
            <strong>{{ sceneLabel }}</strong>
            <small v-if="detail.createTime">创建时间 {{ detail.createTime }}</small>
          </div>
        </div>

        <div class="detail-toolbar">
          <div class="meta-list">
            <span class="meta-chip">纪要编号 #{{ meetingId }}</span>
            <span class="meta-chip">{{ statusLabel }}</span>
            <span class="meta-chip" v-if="detail.speakerBlocks?.length">整理后 {{ detail.speakerBlocks.length }} 段发言</span>
            <span class="meta-chip" v-if="detail.speakerSegments?.length">已识别 {{ detail.speakerSegments.length }} 个片段</span>
            <span class="meta-chip" v-if="detail.roleInsights?.length">角色分析 {{ detail.roleInsights.length }} 项</span>
            <span class="meta-chip" v-if="detail.todoChains?.length">责任链 {{ detail.todoChains.length }} 条</span>
            <span class="meta-chip" v-if="detail.decisionInsights?.length">结论分析 {{ detail.decisionInsights.length }} 条</span>
          </div>
          <div class="toolbar-actions">
            <el-button v-if="!editMode" type="primary" @click="enterEditMode">
              开始校正
            </el-button>
            <el-button v-else @click="cancelEditMode">
              取消校正
            </el-button>
            <el-button v-if="editMode" type="success" :loading="saveLoading" @click="saveCorrection">
              保存校正
            </el-button>
            <el-button type="primary" plain @click="openHistoryDrawer">
              <el-icon class="el-icon--left"><Clock /></el-icon>
              历史记录
            </el-button>
            <el-button type="warning" plain @click="openExportDialog">
              <el-icon class="el-icon--left"><Download /></el-icon>
              导出纪要
            </el-button>
            <el-button type="success" plain :disabled="!detail.hasRawAudio" @click="downloadRawAudio">
              <el-icon class="el-icon--left"><Download /></el-icon>
              下载原音
            </el-button>
          </div>
        </div>

        <section v-if="showProcessingStatus" class="content-card processing-panel">
          <div class="processing-panel-head">
            <div>
              <span>当前处理阶段</span>
              <strong>{{ detail.processingLabel || statusLabel }}</strong>
            </div>
            <em>{{ processingPercentValue }}%</em>
          </div>
          <p class="processing-panel-copy">{{ detail.processingDescription || '系统正在整理纪要结果，请稍候刷新或回到历史查看。' }}</p>
          <div class="processing-panel-bar">
            <span :style="{ width: `${processingPercentValue}%` }" />
          </div>
          <div class="processing-panel-steps">
            <span
              v-for="step in processingSteps"
              :key="step.key"
              class="processing-step"
              :class="`processing-step--${step.status}`"
            >
              {{ step.label }}
            </span>
          </div>
        </section>

        <div class="overview-grid">
          <section class="content-card" v-if="!editMode">
            <span>纪要摘要</span>
            <p>{{ detail.summaryText || '暂无摘要' }}</p>
          </section>
          <section class="content-card" v-if="!editMode && detail.keywords?.length">
            <span>关键词</span>
            <div class="tag-list">
              <el-tag v-for="keyword in detail.keywords" :key="keyword" effect="plain">{{ keyword }}</el-tag>
            </div>
          </section>
        </div>

        <section class="structured-section" v-if="!editMode && detail.structuredSections?.length">
          <article
            v-for="section in detail.structuredSections"
            :key="section.key || section.title"
            class="content-card structured-card"
          >
            <div class="structured-card-head">
              <span>{{ section.title || '结构化小节' }}</span>
              <small>{{ section.subtitle || '系统正在按场景整理更适合阅读的纪要内容。' }}</small>
            </div>
            <ul class="structured-list">
              <li
                v-for="(item, index) in section.items || []"
                :key="`${section.key || section.title}-${index}`"
              >
                {{ item }}
              </li>
            </ul>
          </article>
        </section>

        <section class="analysis-section" v-if="!editMode && (detail.roleInsights?.length || detail.todoChains?.length || detail.decisionInsights?.length)">
          <article class="content-card analysis-card" v-if="detail.roleInsights?.length">
            <div class="structured-card-head">
              <span>发言角色分析</span>
              <small>从发言块里识别主讲、提问、回应与任务承接关系。</small>
            </div>
            <div class="analysis-stack">
              <article
                v-for="insight in detail.roleInsights"
                :key="`${insight.roleKey}-${insight.speakerName}`"
                class="analysis-item"
              >
                <div class="analysis-item-head">
                  <strong>{{ insight.speakerName || '待确认发言人' }}</strong>
                  <el-tag size="small" effect="plain">{{ insight.roleLabel || '角色待识别' }}</el-tag>
                </div>
                <p>{{ insight.contribution || insight.evidence || '系统正在整理角色判断依据。' }}</p>
                <small>{{ insight.evidence || '暂无判定依据' }}</small>
              </article>
            </div>
          </article>

          <article class="content-card analysis-card" v-if="detail.todoChains?.length">
            <div class="structured-card-head">
              <span>待办责任链分析</span>
              <small>用责任人、时间约束和完整度标记任务是否真的能落地。</small>
            </div>
            <div class="analysis-stack">
              <article
                v-for="(chain, index) in detail.todoChains"
                :key="`${chain.taskText}-${index}`"
                class="analysis-item"
              >
                <div class="analysis-item-head">
                  <strong>{{ chain.owner || '待确认负责人' }}</strong>
                  <span class="chain-status" :class="`chain-status--${chain.statusKey || 'pending'}`">
                    {{ chain.statusLabel || '待确认' }}
                  </span>
                </div>
                <p>{{ chain.action || chain.taskText || '暂无任务描述' }}</p>
                <small>时间约束：{{ chain.deadline || '待补充' }}</small>
              </article>
            </div>
          </article>

          <article class="content-card analysis-card" v-if="detail.decisionInsights?.length">
            <div class="structured-card-head">
              <span>结论与待确认事项</span>
              <small>把已明确的结论和仍需确认的事项拆开看，方便会后推进。</small>
            </div>
            <div class="analysis-stack">
              <article
                v-for="(decision, index) in detail.decisionInsights"
                :key="`${decision.typeKey}-${index}-${decision.content}`"
                class="analysis-item"
              >
                <div class="analysis-item-head">
                  <strong>{{ decision.typeLabel || '结论' }}</strong>
                  <el-tag size="small" :type="decision.typeKey === 'confirmed' ? 'success' : 'warning'" effect="plain">
                    {{ decision.typeKey === 'confirmed' ? '已确认' : '待确认' }}
                  </el-tag>
                </div>
                <p>{{ decision.content || '暂无结论内容' }}</p>
                <small v-if="decision.sourceSpeaker">来源发言人：{{ decision.sourceSpeaker }}</small>
              </article>
            </div>
          </article>
        </section>

        <section class="content-card version-panel" v-if="revisionList.length && !editMode">
          <div class="audio-section-head">
            <div>
              <span>版本对比</span>
              <small>这里会保留自动生成版与每次人工校正后的快照，方便你对照修改前后的纪要结果。</small>
            </div>
            <el-tag type="info" effect="plain">共 {{ revisionList.length }} 个版本</el-tag>
          </div>

          <div class="revision-toolbar" v-if="revisionList.length > 1">
            <el-select v-model="leftRevisionId" placeholder="选择左侧版本">
              <el-option
                v-for="revision in revisionList"
                :key="`left-${revision.id}`"
                :label="revisionLabel(revision)"
                :value="revision.id"
              />
            </el-select>
            <el-select v-model="rightRevisionId" placeholder="选择右侧版本">
              <el-option
                v-for="revision in revisionList"
                :key="`right-${revision.id}`"
                :label="revisionLabel(revision)"
                :value="revision.id"
              />
            </el-select>
          </div>

          <div class="revision-grid">
            <article class="revision-card" v-if="selectedLeftRevision">
              <div class="revision-card-head">
                <strong>{{ revisionLabel(selectedLeftRevision) }}</strong>
                <span>{{ selectedLeftRevision.createTime || '-' }}</span>
              </div>
              <div class="revision-block">
                <span>摘要</span>
                <p>{{ selectedLeftRevision.summaryText || '暂无摘要' }}</p>
              </div>
              <div class="revision-block" v-if="selectedLeftRevision.keywords?.length">
                <span>关键词</span>
                <div class="tag-list">
                  <el-tag v-for="keyword in selectedLeftRevision.keywords" :key="`left-${keyword}`" effect="plain">{{ keyword }}</el-tag>
                </div>
              </div>
              <div class="revision-block" v-if="selectedLeftRevision.todos?.length">
                <span>待办事项</span>
                <ul class="todo-list">
                  <li v-for="todo in selectedLeftRevision.todos" :key="`left-${todo}`">{{ todo }}</li>
                </ul>
              </div>
              <div class="revision-block" v-if="selectedLeftRevision.roleInsights?.length">
                <span>角色分析</span>
                <ul class="todo-list">
                  <li v-for="item in selectedLeftRevision.roleInsights" :key="`left-role-${item.roleKey}-${item.speakerName}`">
                    {{ item.roleLabel }}：{{ item.speakerName }} 
                  </li>
                </ul>
              </div>
              <div class="revision-block" v-if="selectedLeftRevision.decisionInsights?.length">
                <span>结论分析</span>
                <ul class="todo-list">
                  <li v-for="(item, index) in selectedLeftRevision.decisionInsights" :key="`left-decision-${index}`">
                    {{ item.typeLabel }}：{{ item.content }}
                  </li>
                </ul>
              </div>
              <div class="revision-block">
                <span>发言人纪要</span>
                <p class="transcript-text">{{ selectedLeftRevision.speakerTranscript || '暂无发言人纪要' }}</p>
              </div>
            </article>

            <article class="revision-card" v-if="selectedRightRevision">
              <div class="revision-card-head">
                <strong>{{ revisionLabel(selectedRightRevision) }}</strong>
                <span>{{ selectedRightRevision.createTime || '-' }}</span>
              </div>
              <div class="revision-block">
                <span>摘要</span>
                <p>{{ selectedRightRevision.summaryText || '暂无摘要' }}</p>
              </div>
              <div class="revision-block" v-if="selectedRightRevision.keywords?.length">
                <span>关键词</span>
                <div class="tag-list">
                  <el-tag v-for="keyword in selectedRightRevision.keywords" :key="`right-${keyword}`" effect="plain">{{ keyword }}</el-tag>
                </div>
              </div>
              <div class="revision-block" v-if="selectedRightRevision.todos?.length">
                <span>待办事项</span>
                <ul class="todo-list">
                  <li v-for="todo in selectedRightRevision.todos" :key="`right-${todo}`">{{ todo }}</li>
                </ul>
              </div>
              <div class="revision-block" v-if="selectedRightRevision.roleInsights?.length">
                <span>角色分析</span>
                <ul class="todo-list">
                  <li v-for="item in selectedRightRevision.roleInsights" :key="`right-role-${item.roleKey}-${item.speakerName}`">
                    {{ item.roleLabel }}：{{ item.speakerName }}
                  </li>
                </ul>
              </div>
              <div class="revision-block" v-if="selectedRightRevision.decisionInsights?.length">
                <span>结论分析</span>
                <ul class="todo-list">
                  <li v-for="(item, index) in selectedRightRevision.decisionInsights" :key="`right-decision-${index}`">
                    {{ item.typeLabel }}：{{ item.content }}
                  </li>
                </ul>
              </div>
              <div class="revision-block">
                <span>发言人纪要</span>
                <p class="transcript-text">{{ selectedRightRevision.speakerTranscript || '暂无发言人纪要' }}</p>
              </div>
            </article>
          </div>
        </section>

        <section v-if="editMode" class="content-card editor-panel">
          <div class="editor-head">
            <div>
              <span>人工校正工作台</span>
              <p>你可以修改标题、摘要、关键词、待办、全文，以及下方每个发言片段的说话人与文本。保存后系统会重新生成发言人纪要和整理后发言块。</p>
            </div>
            <el-tag type="warning" effect="plain">自动结果待确认</el-tag>
          </div>
          <el-form label-position="top" class="editor-form">
            <el-form-item label="纪要标题">
              <el-input v-model="correctionForm.title" maxlength="128" placeholder="请输入纪要标题" />
            </el-form-item>
            <el-form-item label="纪要摘要">
              <el-input v-model="correctionForm.summaryText" type="textarea" :rows="4" placeholder="请输入纪要摘要" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input
                v-model="correctionForm.keywordsText"
                type="textarea"
                :rows="3"
                placeholder="多个关键词可用中文顿号、逗号或换行分隔"
              />
            </el-form-item>
            <el-form-item label="待办事项">
              <el-input
                v-model="correctionForm.todosText"
                type="textarea"
                :rows="4"
                placeholder="每行一条待办事项"
              />
            </el-form-item>
            <el-form-item label="全文转写">
              <el-input
                v-model="correctionForm.fullTranscript"
                type="textarea"
                :rows="8"
                placeholder="可直接修正全文转写内容"
              />
            </el-form-item>
          </el-form>
        </section>

        <section class="content-card" v-if="!editMode && detail.todos?.length">
          <span>待办事项</span>
          <ul class="todo-list">
            <li v-for="todo in detail.todos" :key="todo">{{ todo }}</li>
          </ul>
        </section>

        <section class="content-card" v-if="detail.hasRawAudio">
          <div class="audio-section-head">
            <span>原始音频</span>
            <div class="audio-section-meta">
              <small>当前播放 {{ playbackLabel }}</small>
              <el-button type="primary" link @click="previewRawAudio">{{ rawAudioUrl ? '重新加载' : '加载原始音频' }}</el-button>
            </div>
          </div>
          <audio
            v-if="rawAudioUrl"
            ref="rawAudioRef"
            class="audio-player"
            :src="rawAudioUrl"
            controls
            preload="none"
            @timeupdate="handleRawTimeUpdate"
            @loadedmetadata="handleRawTimeUpdate"
          />
          <div v-else class="audio-actions">
            <el-button type="primary" plain @click="previewRawAudio">加载原始音频</el-button>
          </div>
        </section>

        <section class="content-card timeline-panel" v-if="detail.speakerSegments?.length">
          <div class="audio-section-head">
            <div>
              <span>发言时间轴</span>
              <small>不同颜色表示不同发言人，点击任意色块即可跳到原始音频对应时间点。</small>
            </div>
            <div class="timeline-summary">
              <strong>{{ formatClock(timelineTotalMs) }}</strong>
              <small>共 {{ detail.speakerSegments.length }} 段发言</small>
            </div>
          </div>

          <div class="timeline-legend">
            <div v-for="speaker in timelineLegend" :key="speaker.name" class="timeline-legend-chip">
              <span class="timeline-legend-dot" :style="speaker.style" />
              <span>{{ speaker.name }}</span>
            </div>
          </div>

          <div class="timeline-scroll">
            <div class="timeline-shell" :style="{ width: `${timelineCanvasWidth}px`, height: `${timelineHeightPx}px` }">
              <div class="timeline-ruler">
                <span
                  v-for="tick in timelineTicks"
                  :key="`${tick.label}-${tick.leftPx}`"
                  class="timeline-tick"
                  :style="{ left: `${tick.leftPx}px` }"
                >
                  <i />
                  <em>{{ tick.label }}</em>
                </span>
              </div>

              <div class="timeline-playhead" :style="timelinePlayheadStyle" />

              <button
                v-for="segment in timelineItems"
                :key="`timeline-${segment.id || segment.segmentIndex}`"
                type="button"
                class="timeline-segment"
                :class="{ 'timeline-segment--active': activeSegmentId === segment.id }"
                :style="segment.style"
                @click="seekToTime(segment.startMs)"
              >
                <strong>{{ segment.speakerName || '未知发言人' }}</strong>
                <small>{{ formatRange(segment.startMs, segment.endMs) }}</small>
              </button>
            </div>
          </div>
        </section>

        <section class="content-card" v-if="!editMode && detail.speakerTranscript">
          <span>发言人纪要</span>
          <p class="transcript-text">{{ detail.speakerTranscript }}</p>
        </section>

        <section class="content-card" v-if="!editMode && detail.speakerBlocks?.length">
          <span>整理后发言块</span>
          <div class="segment-list">
            <article
              v-for="(block, index) in detail.speakerBlocks"
              :key="`${block.speakerName || 'unknown'}-${index}`"
              class="segment-item segment-item--clickable"
              :class="{ 'segment-item--active': activeBlockIndex === index }"
              @click="seekToTime(block.startMs)"
            >
              <div class="segment-head">
                <div>
                  <strong>{{ block.speakerName || '未知发言人' }}</strong>
                  <small>{{ formatRange(block.startMs, block.endMs) }}</small>
                </div>
                <div class="segment-score">
                  <span>合并片段 {{ block.segmentCount || 1 }} 段</span>
                  <el-button type="primary" link @click.stop="seekToTime(block.startMs)">跳转原音</el-button>
                </div>
              </div>
              <p>{{ block.transcript || '暂无文本' }}</p>
            </article>
          </div>
        </section>

        <section class="content-card" v-if="!editMode">
          <span>全文转写</span>
          <p class="transcript-text">{{ detail.fullTranscript || '暂无全文转写' }}</p>
        </section>

        <section class="content-card" v-if="detail.speakerSegments?.length">
          <div class="audio-section-head">
            <span>发言片段时间轴</span>
            <small>{{ editMode ? '可逐条修改发言人与片段文本' : '点击卡片或“跳转原音”可联动原始音频' }}</small>
          </div>
          <div class="segment-list">
            <article
              v-for="segment in segmentEditorList"
              :key="segment.id || segment.segmentIndex"
              class="segment-item segment-item--clickable"
              :class="{ 'segment-item--active': activeSegmentId === segment.id }"
              @click="seekToTime(segment.startMs)"
            >
              <div class="segment-head">
                <div>
                  <strong>{{ segment.speakerName || '未知发言人' }}</strong>
                  <small>{{ formatRange(segment.startMs, segment.endMs) }}</small>
                </div>
                <div class="segment-score">
                  <span v-if="segment.matchScore !== undefined && segment.matchScore !== null">
                    匹配度 {{ formatPercent(segment.matchScore) }}
                  </span>
                  <el-button type="primary" link @click.stop="seekToTime(segment.startMs)">跳转原音</el-button>
                </div>
              </div>
              <template v-if="editMode">
                <div class="segment-editor-grid" @click.stop>
                  <el-input v-model="segment.speakerName" maxlength="64" placeholder="请输入发言人名称" />
                  <el-input
                    v-model="segment.transcript"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入该片段的校正文本"
                  />
                </div>
              </template>
              <p v-else>{{ segment.transcript || '暂无文本' }}</p>
              <div class="segment-actions">
                <el-button type="primary" plain :disabled="!segment.hasSegmentAudio" @click.stop="previewSegmentAudio(segment.id)">
                  试听片段
                </el-button>
                <el-button type="success" plain :disabled="!segment.hasSegmentAudio" @click.stop="downloadSegmentAudio(segment)">
                  下载片段
                </el-button>
              </div>
              <audio
                v-if="segment.id && segmentAudioUrls[segment.id]"
                class="audio-player"
                :src="segmentAudioUrls[segment.id]"
                controls
                preload="none"
              />
            </article>
          </div>
        </section>
      </div>
    </div>

    <meeting-history-drawer v-model="historyDrawerVisible" />
    <meeting-export-template-dialog
      v-model="exportDialogVisible"
      :template="exportTemplateConfig"
      default-format="docx"
      @confirm="handleExportConfirm"
    />
  </flow-background-panel>
</template>

<script lang="ts">
import { computed, defineComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Clock, Download } from '@element-plus/icons-vue'
import FlowBackgroundPanel from '@/components/FlowBackgroundPanel.vue'
import MeetingHistoryDrawer from '@/components/MeetingHistoryDrawer.vue'
import MeetingExportTemplateDialog from '@/components/MeetingExportTemplateDialog.vue'
import {
  applyMeetingCorrection,
  fetchMeetingAudioBlob,
  fetchMeetingExportBlob,
  fetchMeetingSegmentAudioBlob,
  getMeetingHistoryDetail,
  getMeetingRevisions,
  MeetingCorrectionPayload,
  MeetingExportTemplate,
  MeetingHistoryItem,
  MeetingRevisionItem,
  MeetingSegmentItem
} from '@/api/meeting'
import store from '@/store'

export default defineComponent({
  name: 'MeetingNoteDetail',
  components: {
    FlowBackgroundPanel,
    MeetingHistoryDrawer,
    MeetingExportTemplateDialog,
    ArrowLeft,
    Clock,
    Download
  },
  setup () {
    type ExportFormat = 'txt' | 'md' | 'docx'
    interface ExportConfirmPayload {
      format: ExportFormat
      template: MeetingExportTemplate
    }

    interface EditableMeetingSegment extends MeetingSegmentItem {
      id: number
    }

    const route = useRoute()
    const router = useRouter()
    const meetingId = computed(() => Number(route.params.meetingId))
    const detailLoading = ref(false)
    const saveLoading = ref(false)
    const editMode = ref(false)
    const historyDrawerVisible = ref(false)
    const rawAudioUrl = ref('')
    const rawAudioRef = ref<HTMLAudioElement | null>(null)
    const rawAudioDurationMs = ref(0)
    const currentPlaybackMs = ref(-1)
    const segmentAudioUrls = ref<Record<number, string>>({})
    const exportDialogVisible = ref(false)
    const revisionList = ref<MeetingRevisionItem[]>([])
    const leftRevisionId = ref<number | null>(null)
    const rightRevisionId = ref<number | null>(null)
    const exportTemplateConfig = ref<MeetingExportTemplate>({
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

    const detail = reactive<MeetingHistoryItem>({
      id: 0,
      userId: 0,
      title: '',
      sceneType: 'meeting',
      rawFilename: '',
      fullTranscript: '',
      summaryText: '',
      keywords: [],
      todos: [],
      structuredSections: [],
      roleInsights: [],
      todoChains: [],
      decisionInsights: [],
      speakerTranscript: '',
      speakerBlocks: [],
      speakerSegments: [],
      hasRawAudio: false,
      processingStage: '',
      processingLabel: '',
      processingDescription: '',
      processingPercent: 0
    })

    const correctionForm = reactive({
      title: '',
      summaryText: '',
      keywordsText: '',
      todosText: '',
      fullTranscript: '',
      speakerSegments: [] as EditableMeetingSegment[]
    })

    const isLoggedIn = computed(() => Boolean(store.state.token && store.state.user?.isLogin && Number(store.state.user?._id)))
    const exportTemplateStorageKey = computed(() => `meeting-export-template:${Number(store.state.user?._id) || 0}`)
    const sceneLabel = computed(() => (detail.sceneType === 'classroom' ? '课堂模式' : '会议模式'))
    const statusLabel = computed(() => {
      if (detail.status === 'SUCCESS') return '处理成功'
      if (detail.status === 'FAILED') return '处理失败'
      return detail.processingLabel || detail.status || '处理中'
    })
    const showProcessingStatus = computed(() => Boolean(
      detail.status &&
      detail.status !== 'SUCCESS' &&
      detail.status !== 'FAILED'
    ))
    const processingPercentValue = computed(() => {
      const percent = Number(detail.processingPercent || 0)
      if (Number.isNaN(percent) || percent <= 0) {
        return 12
      }
      return Math.max(8, Math.min(100, Math.round(percent)))
    })
    const processingSteps = computed(() => {
      const currentStage = (detail.processingStage || '').toUpperCase()
      const includeSpeakerStep = Boolean(
        detail.speakerBlocks?.length ||
        detail.speakerSegments?.length ||
        currentStage === 'SPEAKER_MATCHING' ||
        currentStage === 'FINALIZING'
      )
      const steps = [
        { key: 'QUEUED', label: '排队中' },
        { key: 'UPLOADED', label: '音频入库' },
        { key: 'TRANSCRIBING', label: '语音转写' },
        ...(includeSpeakerStep ? [{ key: 'SPEAKER_MATCHING', label: '发言匹配' }] : []),
        { key: 'STRUCTURING', label: '纪要整理' },
        { key: 'FINALIZING', label: '结果封装' },
        { key: 'SUCCESS', label: '完成' }
      ]
      const aliases: Record<string, string> = {
        PENDING: 'QUEUED',
        QUEUED: 'QUEUED',
        AUDIO_READY: 'UPLOADED',
        UPLOADED: 'UPLOADED',
        TRANSCRIBING: 'TRANSCRIBING',
        SPEAKER_MATCHING: 'SPEAKER_MATCHING',
        STRUCTURING: 'STRUCTURING',
        FINALIZING: 'FINALIZING',
        SUCCESS: 'SUCCESS',
        FAILED: 'FINALIZING'
      }
      const normalized = aliases[currentStage] || 'QUEUED'
      const currentIndex = Math.max(0, steps.findIndex(step => step.key === normalized))
      return steps.map((step, index) => ({
        ...step,
        status: index < currentIndex ? 'done' : (index === currentIndex ? 'active' : 'todo')
      }))
    })
    const playbackLabel = computed(() => formatClock(currentPlaybackMs.value))
    const timelineTotalMs = computed(() => {
      const segmentMaxEndMs = Math.max(...(detail.speakerSegments || []).map(segment => segment.endMs || 0), 0)
      return Math.max(rawAudioDurationMs.value, segmentMaxEndMs, 1)
    })
    const timelineCanvasWidth = computed(() => Math.max(860, Math.ceil((timelineTotalMs.value / 1000) * 34)))
    const timelineItems = computed(() => {
      const rowEndPositions: number[] = []
      const rowHeight = 82
      const rowGap = 14
      const rulerHeight = 44
      return (detail.speakerSegments || []).map(segment => {
        const startMs = segment.startMs || 0
        const endMs = Math.max(segment.endMs || startMs, startMs + 1)
        const startPx = Math.max(0, Math.round((startMs / timelineTotalMs.value) * timelineCanvasWidth.value))
        const widthPx = Math.max(
          Math.round(((endMs - startMs) / timelineTotalMs.value) * timelineCanvasWidth.value),
          124
        )
        let rowIndex = 0
        while (rowEndPositions[rowIndex] !== undefined && startPx < rowEndPositions[rowIndex] + 10) {
          rowIndex += 1
        }
        rowEndPositions[rowIndex] = startPx + widthPx
        return {
          ...segment,
          rowIndex,
          leftPx: startPx,
          widthPx,
          topPx: rulerHeight + rowIndex * (rowHeight + rowGap),
          style: {
            left: `${startPx}px`,
            width: `${widthPx}px`,
            top: `${rulerHeight + rowIndex * (rowHeight + rowGap)}px`,
            ...speakerStyle(segment.speakerName)
          }
        }
      })
    })
    const timelineHeightPx = computed(() => {
      const maxRow = Math.max(...timelineItems.value.map(item => item.rowIndex), 0)
      return 44 + (maxRow + 1) * 82 + maxRow * 14 + 16
    })
    const timelineTicks = computed(() => {
      const totalMs = timelineTotalMs.value
      const totalSeconds = totalMs / 1000
      let stepSeconds = 15
      if (totalSeconds > 300) stepSeconds = 30
      if (totalSeconds > 900) stepSeconds = 60
      if (totalSeconds > 1800) stepSeconds = 120
      const ticks: Array<{ label: string, leftPx: number }> = []
      for (let second = 0; second <= totalSeconds; second += stepSeconds) {
        ticks.push({
          label: formatClock(second * 1000),
          leftPx: Math.round((second * 1000 / totalMs) * timelineCanvasWidth.value)
        })
      }
      if (!ticks.length || ticks[ticks.length - 1].label !== formatClock(totalMs)) {
        ticks.push({
          label: formatClock(totalMs),
          leftPx: timelineCanvasWidth.value
        })
      }
      return ticks
    })
    const timelinePlayheadStyle = computed(() => {
      if (currentPlaybackMs.value < 0) {
        return { display: 'none' }
      }
      const leftPx = Math.min(
        timelineCanvasWidth.value,
        Math.max(0, Math.round((currentPlaybackMs.value / timelineTotalMs.value) * timelineCanvasWidth.value))
      )
      return {
        left: `${leftPx}px`,
        top: '26px',
        height: `${timelineHeightPx.value - 26}px`
      }
    })
    const timelineLegend = computed(() => {
      const uniqueNames = new Map<string, Record<string, string>>()
      for (const segment of detail.speakerSegments || []) {
        const name = segment.speakerName || '未知发言人'
        if (!uniqueNames.has(name)) {
          uniqueNames.set(name, speakerStyle(name))
        }
      }
      return Array.from(uniqueNames.entries()).map(([name, style]) => ({ name, style }))
    })
    const activeBlockIndex = computed(() => {
      const blocks = detail.speakerBlocks || []
      return blocks.findIndex(block => isWithinRange(currentPlaybackMs.value, block.startMs, block.endMs))
    })
    const activeSegmentId = computed(() => {
      const segments = segmentEditorList.value
      const activeSegment = segments.find(segment => isWithinRange(currentPlaybackMs.value, segment.startMs, segment.endMs))
      return activeSegment?.id
    })
    const segmentEditorList = computed(() => (editMode.value ? correctionForm.speakerSegments : (detail.speakerSegments || [])))
    const selectedLeftRevision = computed(() => revisionList.value.find(item => item.id === leftRevisionId.value) || revisionList.value[0] || null)
    const selectedRightRevision = computed(() => revisionList.value.find(item => item.id === rightRevisionId.value) || revisionList.value[revisionList.value.length - 1] || null)

    const ensureDetailAccess = (showMessage = true) => {
      if (isLoggedIn.value) {
        return true
      }
      if (showMessage) {
        ElMessage.warning('请先登录后再查看纪要详情')
      }
      router.push('/login')
      return false
    }

    const handleUnauthorized = () => {
      store.commit('logout')
      ElMessage.error('登录状态已失效，请重新登录后再查看纪要详情')
      router.push('/login')
    }

    const revokeAllAudioUrls = () => {
      if (rawAudioUrl.value) {
        URL.revokeObjectURL(rawAudioUrl.value)
        rawAudioUrl.value = ''
      }
      rawAudioDurationMs.value = 0
      currentPlaybackMs.value = -1
      Object.values(segmentAudioUrls.value).forEach(url => URL.revokeObjectURL(url))
      segmentAudioUrls.value = {}
    }

    const assignDetail = (data: MeetingHistoryItem) => {
      Object.assign(detail, {
        ...data,
        keywords: Array.isArray(data.keywords) ? data.keywords : [],
        todos: Array.isArray(data.todos) ? data.todos : [],
        structuredSections: Array.isArray(data.structuredSections) ? data.structuredSections : [],
        roleInsights: Array.isArray(data.roleInsights) ? data.roleInsights : [],
        todoChains: Array.isArray(data.todoChains) ? data.todoChains : [],
        decisionInsights: Array.isArray(data.decisionInsights) ? data.decisionInsights : [],
        speakerBlocks: Array.isArray(data.speakerBlocks) ? data.speakerBlocks : [],
        speakerSegments: Array.isArray(data.speakerSegments) ? data.speakerSegments : [],
        processingStage: data.processingStage || '',
        processingLabel: data.processingLabel || '',
        processingDescription: data.processingDescription || '',
        processingPercent: Number(data.processingPercent || 0)
      })
    }

    const syncRevisionSelection = () => {
      if (!revisionList.value.length) {
        leftRevisionId.value = null
        rightRevisionId.value = null
        return
      }
      if (!leftRevisionId.value || !revisionList.value.some(item => item.id === leftRevisionId.value)) {
        leftRevisionId.value = revisionList.value[0].id
      }
      if (!rightRevisionId.value || !revisionList.value.some(item => item.id === rightRevisionId.value)) {
        rightRevisionId.value = revisionList.value[revisionList.value.length - 1].id
      }
    }

    const loadRevisions = async () => {
      const res = await getMeetingRevisions(meetingId.value)
      if (res.code !== 200) {
        throw new Error(res.msg || '加载纪要版本失败')
      }
      revisionList.value = Array.isArray(res.data) ? res.data : []
      syncRevisionSelection()
    }

    const hydrateCorrectionForm = () => {
      correctionForm.title = detail.title || ''
      correctionForm.summaryText = detail.summaryText || ''
      correctionForm.keywordsText = (detail.keywords || []).join('、')
      correctionForm.todosText = (detail.todos || []).join('\n')
      correctionForm.fullTranscript = detail.fullTranscript || ''
      correctionForm.speakerSegments = (detail.speakerSegments || [])
        .filter((segment): segment is EditableMeetingSegment => Boolean(segment.id))
        .map(segment => ({
          ...segment,
          id: Number(segment.id),
          speakerName: segment.speakerName || '',
          transcript: segment.transcript || ''
        }))
    }

    const loadDetail = async () => {
      if (!ensureDetailAccess()) {
        return
      }
      if (!meetingId.value) {
        ElMessage.error('纪要编号无效')
        router.push('/MeetingNotes')
        return
      }
      detailLoading.value = true
      try {
        const res = await getMeetingHistoryDetail(meetingId.value)
        if (res.code !== 200 || !res.data?.id) {
          throw new Error(res.msg || '加载纪要详情失败')
        }
        assignDetail(res.data)
        await loadRevisions()
        if (editMode.value) {
          hydrateCorrectionForm()
        }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载纪要详情失败')
        router.push('/MeetingNotes')
      } finally {
        detailLoading.value = false
      }
    }

    const previewRawAudio = async () => {
      if (!ensureDetailAccess()) {
        return
      }
      if (rawAudioUrl.value) {
        return
      }
      try {
        const blob = await fetchMeetingAudioBlob(meetingId.value)
        rawAudioUrl.value = URL.createObjectURL(blob)
        currentPlaybackMs.value = -1
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载原始音频失败')
      }
    }

    const ensureRawAudioReady = async () => {
      if (!detail.hasRawAudio) {
        ElMessage.warning('当前纪要没有可联动的原始音频')
        return null
      }
      if (!rawAudioUrl.value) {
        await previewRawAudio()
        await nextTick()
      }
      return rawAudioRef.value
    }

    const downloadRawAudio = async () => {
      if (!ensureDetailAccess()) {
        return
      }
      try {
        const blob = await fetchMeetingAudioBlob(meetingId.value)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = detail.rawFilename || `meeting-note-${meetingId.value}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载原始音频失败')
      }
    }

    const loadExportTemplate = () => {
      try {
        const raw = localStorage.getItem(exportTemplateStorageKey.value)
        if (!raw) return
        const parsed = JSON.parse(raw) as MeetingExportTemplate
        exportTemplateConfig.value = { ...exportTemplateConfig.value, ...(parsed || {}) }
      } catch (error) {
        console.warn('load export template failed', error)
      }
    }

    const saveExportTemplate = (template: MeetingExportTemplate) => {
      exportTemplateConfig.value = { ...exportTemplateConfig.value, ...template }
      localStorage.setItem(exportTemplateStorageKey.value, JSON.stringify(exportTemplateConfig.value))
    }

    const exportMeeting = async (format: ExportFormat, template?: MeetingExportTemplate) => {
      if (!ensureDetailAccess()) {
        return
      }
      try {
        const blob = await fetchMeetingExportBlob(meetingId.value, format, template || exportTemplateConfig.value)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        const safeTitle = (detail.title || `meeting-note-${meetingId.value}`).replace(/[\\/:*?"<>|]/g, '_')
        link.download = `${safeTitle}.${format}`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '导出纪要失败')
      }
    }

    const openExportDialog = () => {
      if (!ensureDetailAccess()) {
        return
      }
      exportDialogVisible.value = true
    }

    const handleExportConfirm = async (payload: ExportConfirmPayload) => {
      saveExportTemplate(payload.template)
      await exportMeeting(payload.format, payload.template)
    }

    const previewSegmentAudio = async (segmentId?: number) => {
      if (!ensureDetailAccess() || !segmentId) {
        return
      }
      if (segmentAudioUrls.value[segmentId]) {
        return
      }
      try {
        const blob = await fetchMeetingSegmentAudioBlob(meetingId.value, segmentId)
        segmentAudioUrls.value = { ...segmentAudioUrls.value, [segmentId]: URL.createObjectURL(blob) }
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '加载片段音频失败')
      }
    }

    const downloadSegmentAudio = async (segment: MeetingSegmentItem) => {
      if (!ensureDetailAccess() || !segment.id) {
        return
      }
      try {
        const blob = await fetchMeetingSegmentAudioBlob(meetingId.value, segment.id)
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = segment.segmentFilename || `meeting-segment-${segment.segmentIndex}.wav`
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        URL.revokeObjectURL(url)
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '下载片段音频失败')
      }
    }

    const formatRange = (startMs?: number, endMs?: number) => {
      const toText = (value?: number) => {
        if (value === undefined || value === null || value < 0) {
          return '--:--'
        }
        const totalSeconds = Math.floor(value / 1000)
        const minutes = Math.floor(totalSeconds / 60)
        const seconds = totalSeconds % 60
        return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
      }
      return `${toText(startMs)} - ${toText(endMs)}`
    }

    const formatPercent = (score?: number) => {
      if (score === undefined || score === null) return '--'
      return `${(score * 100).toFixed(2)}%`
    }

    const formatClock = (value?: number) => {
      if (value === undefined || value === null || value < 0) {
        return '00:00'
      }
      const totalSeconds = Math.floor(value / 1000)
      const minutes = Math.floor(totalSeconds / 60)
      const seconds = totalSeconds % 60
      return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    }

    const colorPalette = [
      { background: 'linear-gradient(135deg, rgba(74, 124, 255, 0.22) 0%, rgba(98, 147, 255, 0.36) 100%)', border: '#4c7bff', text: '#1f3fa9', shadow: 'rgba(124, 92, 255, 0.28)' },
      { background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.22) 0%, rgba(52, 211, 153, 0.34) 100%)', border: '#10b981', text: '#0f766e', shadow: 'rgba(16, 185, 129, 0.24)' },
      { background: 'linear-gradient(135deg, rgba(249, 115, 22, 0.22) 0%, rgba(251, 146, 60, 0.34) 100%)', border: '#f97316', text: '#c2410c', shadow: 'rgba(249, 115, 22, 0.22)' },
      { background: 'linear-gradient(135deg, rgba(168, 85, 247, 0.22) 0%, rgba(192, 132, 252, 0.34) 100%)', border: '#a855f7', text: '#7c3aed', shadow: 'rgba(168, 85, 247, 0.24)' },
      { background: 'linear-gradient(135deg, rgba(14, 165, 233, 0.22) 0%, rgba(56, 189, 248, 0.34) 100%)', border: '#0ea5e9', text: '#0369a1', shadow: 'rgba(14, 165, 233, 0.24)' },
      { background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.22) 0%, rgba(251, 191, 36, 0.34) 100%)', border: '#f59e0b', text: '#b45309', shadow: 'rgba(245, 158, 11, 0.24)' }
    ]

    const speakerStyle = (speakerName?: string) => {
      const normalized = (speakerName || '未知发言人').trim() || '未知发言人'
      if (normalized === '未知发言人') {
        return {
          '--speaker-bg': 'linear-gradient(135deg, rgba(100, 116, 139, 0.22) 0%, rgba(148, 163, 184, 0.34) 100%)',
          '--speaker-border': '#64748b',
          '--speaker-text': '#475569',
          '--speaker-shadow': 'rgba(100, 116, 139, 0.2)'
        }
      }
      const hash = Array.from(normalized).reduce((sum, char) => sum + char.charCodeAt(0), 0)
      const palette = colorPalette[hash % colorPalette.length]
      return {
        '--speaker-bg': palette.background,
        '--speaker-border': palette.border,
        '--speaker-text': palette.text,
        '--speaker-shadow': palette.shadow
      }
    }

    const isWithinRange = (currentMs: number, startMs?: number, endMs?: number) => {
      if (startMs === undefined || startMs === null || endMs === undefined || endMs === null) {
        return false
      }
      return currentMs >= startMs && currentMs <= endMs
    }

    const handleRawTimeUpdate = () => {
      currentPlaybackMs.value = Math.round((rawAudioRef.value?.currentTime || 0) * 1000)
      rawAudioDurationMs.value = Math.round((rawAudioRef.value?.duration || 0) * 1000)
    }

    const seekToTime = async (startMs?: number) => {
      if (!ensureDetailAccess() || startMs === undefined || startMs === null) {
        return
      }
      const audio = await ensureRawAudioReady()
      if (!audio) {
        return
      }
      audio.currentTime = Math.max(0, startMs / 1000)
      currentPlaybackMs.value = Math.round(audio.currentTime * 1000)
      try {
        await audio.play()
      } catch (error) {
        // ignore autoplay rejection; user still lands on the correct timestamp
      }
    }

    const splitKeywords = (value: string) => {
      return value
        .split(/[\n,，、；;]/)
        .map(item => item.trim())
        .filter(Boolean)
    }

    const splitTodos = (value: string) => {
      return value
        .split('\n')
        .map(item => item.trim())
        .filter(Boolean)
    }

    const enterEditMode = () => {
      if (!ensureDetailAccess()) {
        return
      }
      hydrateCorrectionForm()
      editMode.value = true
    }

    const cancelEditMode = () => {
      editMode.value = false
      hydrateCorrectionForm()
    }

    const saveCorrection = async () => {
      if (!ensureDetailAccess()) {
        return
      }
      if (!correctionForm.title.trim()) {
        ElMessage.warning('纪要标题不能为空')
        return
      }
      saveLoading.value = true
      try {
        const payload: MeetingCorrectionPayload = {
          title: correctionForm.title.trim(),
          summaryText: correctionForm.summaryText.trim(),
          keywords: splitKeywords(correctionForm.keywordsText),
          todos: splitTodos(correctionForm.todosText),
          fullTranscript: correctionForm.fullTranscript.trim(),
          speakerSegments: correctionForm.speakerSegments.map(segment => ({
            id: segment.id,
            speakerName: (segment.speakerName || '').trim(),
            transcript: (segment.transcript || '').trim()
          }))
        }
        const res = await applyMeetingCorrection(meetingId.value, payload)
        if (res.code !== 200 || !res.data?.id) {
          throw new Error(res.msg || '保存校正失败')
        }
        assignDetail(res.data)
        await loadRevisions()
        editMode.value = false
        ElMessage.success('校正内容已保存')
      } catch (error: any) {
        if (error?.response?.status === 401) {
          handleUnauthorized()
          return
        }
        ElMessage.error(error?.message || '保存校正失败')
      } finally {
        saveLoading.value = false
      }
    }

    const openHistoryDrawer = () => {
      if (!ensureDetailAccess()) {
        return
      }
      historyDrawerVisible.value = true
    }

    const goBack = () => {
      router.push('/MeetingNotes')
    }

    const revisionLabel = (revision?: MeetingRevisionItem | null) => {
      if (!revision) {
        return '未命名版本'
      }
      const typeLabel = revision.revisionType === 'MANUAL' ? '人工校正版' : '自动生成版'
      return `V${revision.versionNo} · ${typeLabel}`
    }

    onMounted(() => {
      loadExportTemplate()
      void loadDetail()
    })

    onBeforeUnmount(() => {
      revokeAllAudioUrls()
    })

    return {
      meetingId,
      detail,
      detailLoading,
      saveLoading,
      editMode,
      historyDrawerVisible,
      rawAudioUrl,
      rawAudioRef,
      rawAudioDurationMs,
      currentPlaybackMs,
      segmentAudioUrls,
      exportDialogVisible,
      exportTemplateConfig,
      correctionForm,
      revisionList,
      leftRevisionId,
      rightRevisionId,
      selectedLeftRevision,
      selectedRightRevision,
      sceneLabel,
      statusLabel,
      showProcessingStatus,
      processingPercentValue,
      processingSteps,
      playbackLabel,
      timelineTotalMs,
      timelineCanvasWidth,
      timelineItems,
      timelineHeightPx,
      timelineTicks,
      timelinePlayheadStyle,
      timelineLegend,
      activeBlockIndex,
      activeSegmentId,
      segmentEditorList,
      previewRawAudio,
      downloadRawAudio,
      exportMeeting,
      openExportDialog,
      handleExportConfirm,
      previewSegmentAudio,
      downloadSegmentAudio,
      formatRange,
      formatPercent,
      formatClock,
      handleRawTimeUpdate,
      seekToTime,
      enterEditMode,
      cancelEditMode,
      saveCorrection,
      openHistoryDrawer,
      goBack,
      revisionLabel
    }
  }
})
</script>

<style scoped>
.meeting-detail-page {
  padding-bottom: 24px;
}

.detail-card {
  max-width: 1080px;
  margin: 0 auto;
  border: 1px solid rgba(148, 128, 238, 0.2);
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.96) 0%, rgba(247, 250, 255, 0.94) 100%);
  padding: 28px;
  box-shadow: 0 22px 48px rgba(30, 61, 122, 0.08), inset 0 0 0 1px rgba(148, 128, 238, 0.06);
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f5f9ff 0%, #eef4ff 100%);
}

.back-btn {
  padding-left: 0;
  margin-bottom: 10px;
}

.detail-badge {
  margin: 0 0 8px;
  color: #7c5cff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.detail-head h2 {
  margin: 0 0 10px;
  color: #27324a;
  font-size: 34px;
}

.detail-desc {
  margin: 0;
  color: #5f6c84;
  line-height: 1.7;
  max-width: 640px;
}

.detail-head-side {
  min-width: 220px;
  border-radius: 18px;
  padding: 18px 20px;
  background: linear-gradient(135deg, #6a4fe0 0%, #9b87ff 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-shadow: 0 16px 34px rgba(106, 79, 224, 0.22);
}

.detail-head-side span,
.detail-head-side small {
  font-size: 13px;
  opacity: 0.82;
}

.detail-head-side strong {
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.2;
}

.detail-head-side small {
  margin-top: 10px;
  line-height: 1.6;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #eef4ff;
  color: #5270a8;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.revision-toolbar {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.revision-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.revision-card {
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  border: 1px solid #e8f0ff;
}

.revision-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.revision-card-head strong {
  color: #243362;
  font-size: 17px;
}

.revision-card-head span {
  color: #7282a4;
  font-size: 12px;
}

.revision-block + .revision-block {
  margin-top: 14px;
}

.revision-block span {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 8px;
}

.processing-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.processing-panel-head strong {
  display: block;
  color: #28385d;
  font-size: 22px;
  line-height: 1.2;
}

.processing-panel-head em {
  font-style: normal;
  color: #6a4fe0;
  font-size: 26px;
  font-weight: 700;
}

.processing-panel-copy {
  margin-top: 10px !important;
  color: #5f6c84;
}

.processing-panel-bar {
  overflow: hidden;
  margin-top: 14px;
  height: 10px;
  border-radius: 999px;
  background: rgba(130, 152, 210, 0.16);
}

.processing-panel-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6a4fe0 0%, #9b87ff 58%, #6cc9c5 100%);
  box-shadow: 0 10px 20px rgba(106, 79, 224, 0.22);
  transition: width 0.35s ease;
}

.processing-panel-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.processing-step {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(150, 134, 228, 0.12);
  background: rgba(255, 255, 255, 0.68);
  color: #7b89a5;
  font-size: 12px;
}

.processing-step--done {
  border-color: rgba(92, 193, 132, 0.2);
  background: rgba(92, 193, 132, 0.12);
  color: #2f8051;
}

.processing-step--active {
  border-color: rgba(106, 79, 224, 0.24);
  background: rgba(106, 79, 224, 0.12);
  color: #335ad8;
}

.content-card {
  margin-top: 16px;
  padding: 18px;
  border-radius: 18px;
  background: #fbfdff;
  border: 1px solid #e8f0ff;
}

.content-card span {
  display: block;
  color: #7c8ab2;
  font-size: 12px;
  margin-bottom: 8px;
}

.content-card p,
.todo-list {
  margin: 0;
  color: #243362;
  line-height: 1.8;
  white-space: pre-wrap;
}

.structured-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.structured-card {
  margin-top: 0;
}

.structured-card-head span {
  display: block;
  color: #2a3960;
  font-size: 16px;
  font-weight: 700;
}

.structured-card-head small {
  display: block;
  margin-top: 6px;
  color: #71809d;
  line-height: 1.65;
}

.structured-list {
  margin: 14px 0 0;
  padding-left: 18px;
  color: #2f3f63;
}

.structured-list li + li {
  margin-top: 10px;
}

.analysis-section {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.analysis-card {
  margin-top: 0;
}

.analysis-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.analysis-item {
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f8ff 0%, #edf5ff 100%);
  border: 1px solid rgba(148, 128, 250, 0.15);
}

.analysis-item-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.analysis-item-head strong {
  color: #243362;
}

.analysis-item p {
  margin: 0;
  color: #33415c;
  line-height: 1.8;
}

.analysis-item small {
  display: block;
  margin-top: 8px;
  color: #7282a4;
  line-height: 1.65;
}

.chain-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.chain-status--complete {
  background: rgba(92, 193, 132, 0.12);
  color: #2f8051;
}

.chain-status--partial {
  background: rgba(255, 180, 84, 0.16);
  color: #a96518;
}

.chain-status--pending {
  background: rgba(150, 134, 228, 0.12);
  color: #496399;
}

.editor-panel {
  padding: 22px;
}

.editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.editor-head p {
  margin-top: 8px;
  color: #5f6c84;
}

.editor-form :deep(.el-form-item__label) {
  color: #5c6f98;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.todo-list {
  padding-left: 18px;
}

.audio-player {
  width: 100%;
}

.audio-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.audio-section-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.audio-section-meta small,
.audio-section-head small {
  color: #7282a4;
}

.timeline-panel {
  overflow: hidden;
}

.timeline-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.timeline-summary strong {
  color: #243362;
  font-size: 18px;
}

.timeline-summary small {
  color: #7282a4;
}

.timeline-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.timeline-legend-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #f4f7ff;
  color: #43557d;
  font-size: 13px;
}

.timeline-legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: var(--speaker-bg);
  border: 2px solid var(--speaker-border);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.5);
}

.timeline-scroll {
  overflow-x: auto;
  padding-bottom: 6px;
}

.timeline-shell {
  position: relative;
  min-width: 100%;
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(244, 248, 255, 0.9) 0%, rgba(235, 243, 255, 0.74) 100%),
    linear-gradient(90deg, rgba(124, 92, 255, 0.04) 0%, rgba(124, 92, 255, 0) 100%);
  border: 1px dashed rgba(124, 92, 255, 0.28);
}

.timeline-ruler {
  position: absolute;
  inset: 0 18px auto 18px;
  height: 44px;
}

.timeline-tick {
  position: absolute;
  top: 8px;
  transform: translateX(-50%);
}

.timeline-tick i {
  display: block;
  width: 1px;
  height: 12px;
  margin: 0 auto 4px;
  background: rgba(86, 110, 156, 0.34);
}

.timeline-tick em {
  font-style: normal;
  font-size: 11px;
  color: #7181a3;
  white-space: nowrap;
}

.timeline-playhead {
  position: absolute;
  width: 2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 91, 91, 0.22) 0%, rgba(255, 91, 91, 0.88) 35%, rgba(255, 91, 91, 0.18) 100%);
  box-shadow: 0 0 0 4px rgba(255, 91, 91, 0.08);
  pointer-events: none;
  z-index: 1;
}

.timeline-segment {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--speaker-border);
  background: var(--speaker-bg);
  box-shadow: 0 10px 22px var(--speaker-shadow);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.timeline-segment strong {
  color: var(--speaker-text);
  font-size: 14px;
}

.timeline-segment small {
  color: rgba(49, 72, 119, 0.82);
}

.timeline-segment:hover {
  transform: translateY(-2px);
}

.timeline-segment--active {
  border-width: 2px;
  box-shadow: 0 14px 26px var(--speaker-shadow), 0 0 0 4px rgba(124, 92, 255, 0.08);
}

.audio-actions {
  display: flex;
  justify-content: flex-start;
}

.transcript-text {
  max-height: 260px;
  overflow-y: auto;
}

.segment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.segment-item {
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f4f8ff 0%, #edf5ff 100%);
  border: 1px solid transparent;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.segment-item--clickable {
  cursor: pointer;
}

.segment-item--active {
  border-color: rgba(124, 92, 255, 0.68);
  box-shadow: 0 10px 24px rgba(124, 92, 255, 0.14);
  transform: translateY(-1px);
}

.segment-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.segment-head strong {
  display: block;
  color: #243362;
}

.segment-head small,
.segment-score span {
  color: #7282a4;
}

.segment-item p {
  margin: 0;
  color: #33415c;
  line-height: 1.8;
}

.segment-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.segment-editor-grid {
  display: grid;
  gap: 10px;
}

:global(html[data-auth-theme-mode='dark'] .detail-card) {
  background: linear-gradient(145deg, rgba(27, 27, 31, 0.96) 0%, rgba(35, 35, 40, 0.94) 100%);
  border-color: rgba(140, 110, 245, 0.24);
  box-shadow: 0 24px 54px rgba(6, 12, 28, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .detail-head) {
  background: linear-gradient(135deg, rgba(37, 37, 42, 0.96) 0%, rgba(44, 44, 50, 0.92) 100%);
}

:global(html[data-auth-theme-mode='dark'] .detail-badge) {
  color: #8db2ff;
}

:global(html[data-auth-theme-mode='dark'] .detail-head h2),
:global(html[data-auth-theme-mode='dark'] .content-card p),
:global(html[data-auth-theme-mode='dark'] .revision-card-head strong),
:global(html[data-auth-theme-mode='dark'] .todo-list),
:global(html[data-auth-theme-mode='dark'] .processing-panel-head strong),
:global(html[data-auth-theme-mode='dark'] .segment-head strong),
:global(html[data-auth-theme-mode='dark'] .analysis-item-head strong),
:global(html[data-auth-theme-mode='dark'] .segment-item p),
:global(html[data-auth-theme-mode='dark'] .analysis-item p),
:global(html[data-auth-theme-mode='dark'] .structured-card-head span),
:global(html[data-auth-theme-mode='dark'] .structured-list) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .detail-desc),
:global(html[data-auth-theme-mode='dark'] .editor-head p),
:global(html[data-auth-theme-mode='dark'] .processing-panel-copy),
:global(html[data-auth-theme-mode='dark'] .content-card span),
:global(html[data-auth-theme-mode='dark'] .revision-block span),
:global(html[data-auth-theme-mode='dark'] .revision-card-head span),
:global(html[data-auth-theme-mode='dark'] .audio-section-meta small),
:global(html[data-auth-theme-mode='dark'] .audio-section-head small),
:global(html[data-auth-theme-mode='dark'] .timeline-summary small),
:global(html[data-auth-theme-mode='dark'] .segment-head small),
:global(html[data-auth-theme-mode='dark'] .segment-score span),
:global(html[data-auth-theme-mode='dark'] .structured-card-head small),
:global(html[data-auth-theme-mode='dark'] .analysis-item small) {
  color: rgba(220, 230, 255, 0.84);
}

:global(html[data-auth-theme-mode='dark'] .meta-chip) {
  background: rgba(31, 31, 35, 0.92);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .content-card) {
  background: linear-gradient(145deg, rgba(14, 23, 43, 0.92) 0%, rgba(25, 25, 29, 0.9) 100%);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .processing-step) {
  background: rgba(15, 25, 47, 0.92);
  color: rgba(210, 223, 255, 0.72);
  border-color: rgba(140, 110, 245, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .processing-step--done) {
  background: rgba(37, 82, 61, 0.42);
  color: #8de5b2;
}

:global(html[data-auth-theme-mode='dark'] .processing-step--active) {
  background: rgba(42, 71, 152, 0.42);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .revision-card) {
  background: rgba(19, 19, 22, 0.9);
  border-color: rgba(140, 110, 245, 0.16);
}

:global(html[data-auth-theme-mode='dark'] .analysis-item) {
  background: rgba(20, 20, 23, 0.92);
  border-color: rgba(140, 110, 245, 0.14);
}

:global(html[data-auth-theme-mode='dark'] .chain-status--complete) {
  background: rgba(37, 82, 61, 0.42);
  color: #8de5b2;
}

:global(html[data-auth-theme-mode='dark'] .chain-status--partial) {
  background: rgba(117, 76, 19, 0.42);
  color: #ffd58f;
}

:global(html[data-auth-theme-mode='dark'] .chain-status--pending) {
  background: rgba(42, 71, 152, 0.42);
  color: #a9c2ff;
}

:global(html[data-auth-theme-mode='dark'] .segment-item) {
  background: rgba(20, 20, 23, 0.92);
}

:global(html[data-auth-theme-mode='dark'] .segment-item--active) {
  border-color: rgba(126, 161, 255, 0.72);
  box-shadow: 0 14px 28px rgba(20, 42, 92, 0.45);
}

:global(html[data-auth-theme-mode='dark'] .timeline-shell) {
  background:
    linear-gradient(180deg, rgba(8, 17, 34, 0.96) 0%, rgba(13, 24, 47, 0.92) 100%),
    linear-gradient(90deg, rgba(120, 152, 255, 0.08) 0%, rgba(120, 152, 255, 0) 100%);
  border-color: rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .timeline-summary strong),
:global(html[data-auth-theme-mode='dark'] .timeline-legend-chip) {
  color: #eef3ff;
}

:global(html[data-auth-theme-mode='dark'] .timeline-legend-chip) {
  background: rgba(17, 28, 52, 0.92);
}

:global(html[data-auth-theme-mode='dark'] .timeline-tick em) {
  color: rgba(220, 230, 255, 0.72);
}

:global(html[data-auth-theme-mode='dark'] .timeline-tick i) {
  background: rgba(220, 230, 255, 0.22);
}

:global(html[data-auth-theme-mode='dark'] .timeline-segment strong) {
  color: var(--speaker-text);
}

:global(html[data-auth-theme-mode='dark'] .timeline-segment small) {
  color: rgba(232, 239, 255, 0.76);
}

:global(html[data-auth-theme-mode='dark'] .timeline-segment--active) {
  box-shadow: 0 16px 30px var(--speaker-shadow), 0 0 0 4px rgba(126, 161, 255, 0.12);
}

:global(html[data-auth-theme-mode='dark'] .editor-form .el-input__wrapper),
:global(html[data-auth-theme-mode='dark'] .editor-form .el-textarea__inner) {
  background: rgba(19, 19, 22, 0.94);
  color: #eef3ff;
  box-shadow: inset 0 0 0 1px rgba(140, 110, 245, 0.18);
}

:global(html[data-auth-theme-mode='dark'] .editor-form .el-textarea__inner::placeholder),
:global(html[data-auth-theme-mode='dark'] .editor-form .el-input__inner::placeholder) {
  color: rgba(220, 230, 255, 0.42);
}

:global(html[data-auth-theme-mode='dark'] .editor-form .el-form-item__label) {
  color: rgba(220, 230, 255, 0.82);
}

@media (max-width: 960px) {
  .detail-head,
  .detail-toolbar,
  .segment-head,
  .editor-head,
  .audio-section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .structured-section {
    grid-template-columns: 1fr;
  }

  .analysis-section {
    grid-template-columns: 1fr;
  }

  .revision-toolbar,
  .revision-grid {
    grid-template-columns: 1fr;
  }

  .detail-head-side {
    width: 100%;
  }

  .timeline-summary {
    align-items: flex-start;
  }
}
</style>
