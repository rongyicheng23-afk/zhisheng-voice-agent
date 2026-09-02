import axios from 'axios'
import http, { getRuntimeHttpBaseUrl } from '@/api/index'

export interface MeetingNoteResult {
  meetingId: number
  userId: number
  title: string
  sceneType: 'meeting' | 'classroom'
  rawObject?: string
  summaryText?: string
  keywords?: string[]
  todos?: string[]
  structuredSections?: MeetingInsightSection[]
  roleInsights?: MeetingRoleInsight[]
  todoChains?: MeetingTodoChainItem[]
  decisionInsights?: MeetingDecisionInsight[]
  fullTranscript?: string
  speakerTranscript?: string
  speakerBlocks?: MeetingSpeakerBlockItem[]
  speakerSegments?: MeetingSegmentItem[]
  status?: 'PENDING' | 'UPLOADED' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
  processingStage?: string
  processingLabel?: string
  processingDescription?: string
  processingPercent?: number
  errorMessage?: string
}

export interface MeetingHistoryItem {
  id: number
  userId: number
  title: string
  sceneType: 'meeting' | 'classroom'
  rawBucket?: string
  rawObject?: string
  rawFilename?: string
  rawContentType?: string
  rawFileSize?: number
  fullTranscript?: string
  summaryText?: string
  keywords?: string[]
  todos?: string[]
  structuredSections?: MeetingInsightSection[]
  roleInsights?: MeetingRoleInsight[]
  todoChains?: MeetingTodoChainItem[]
  decisionInsights?: MeetingDecisionInsight[]
  speakerTranscript?: string
  speakerBlocks?: MeetingSpeakerBlockItem[]
  speakerSegments?: MeetingSegmentItem[]
  status?: string
  processingStage?: string
  processingLabel?: string
  processingDescription?: string
  processingPercent?: number
  errorMessage?: string
  createTime?: string
  updateTime?: string
  hasRawAudio?: boolean
  rawAudioUrl?: string
}

export interface MeetingInsightSection {
  key?: string
  title?: string
  subtitle?: string
  items?: string[]
}

export interface MeetingRoleInsight {
  roleKey?: string
  roleLabel?: string
  speakerName?: string
  evidence?: string
  contribution?: string
}

export interface MeetingTodoChainItem {
  taskText?: string
  owner?: string
  action?: string
  deadline?: string
  statusKey?: 'complete' | 'partial' | 'pending' | string
  statusLabel?: string
}

export interface MeetingDecisionInsight {
  typeKey?: 'confirmed' | 'pending' | string
  typeLabel?: string
  content?: string
  sourceSpeaker?: string
}

export interface MeetingExportTemplate {
  includeMeta?: boolean
  includeSummary?: boolean
  includeKeywords?: boolean
  includeStructuredSections?: boolean
  includeRoleInsights?: boolean
  includeTodoChains?: boolean
  includeDecisionInsights?: boolean
  includeTodos?: boolean
  includeSpeakerTranscript?: boolean
  includeSpeakerBlocks?: boolean
  includeFullTranscript?: boolean
}

export interface MeetingSegmentCorrectionItem {
  id: number
  speakerName?: string
  transcript?: string
}

export interface MeetingCorrectionPayload {
  title?: string
  summaryText?: string
  keywords?: string[]
  todos?: string[]
  roleInsights?: MeetingRoleInsight[]
  todoChains?: MeetingTodoChainItem[]
  decisionInsights?: MeetingDecisionInsight[]
  fullTranscript?: string
  speakerSegments?: MeetingSegmentCorrectionItem[]
}

export interface MeetingSegmentItem {
  id: number
  meetingId: number
  segmentIndex: number
  startMs: number
  endMs: number
  speakerProfileId?: number
  speakerName?: string
  matchScore?: number
  transcript?: string
  segmentBucket?: string
  segmentObject?: string
  segmentFilename?: string
  segmentFileSize?: number
  hasSegmentAudio?: boolean
  segmentAudioUrl?: string | null
}

export interface MeetingSpeakerBlockItem {
  speakerProfileId?: number
  speakerName?: string
  matchScore?: number
  startMs?: number
  endMs?: number
  transcript?: string
  segmentCount?: number
}

export interface MeetingRevisionItem {
  id: number
  meetingId: number
  versionNo: number
  revisionType: 'AUTO' | 'MANUAL' | string
  title?: string
  summaryText?: string
  keywords?: string[]
  todos?: string[]
  fullTranscript?: string
  speakerTranscript?: string
  speakerBlocks?: MeetingSpeakerBlockItem[]
  speakerSegments?: MeetingSegmentItem[]
  createTime?: string
}

interface BackendResponse<T> {
  code: number
  msg: string
  data: T
}

export interface MeetingHistoryQuery {
  keyword?: string
  sceneType?: 'meeting' | 'classroom' | ''
  status?: 'PENDING' | 'UPLOADED' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | ''
  hasTodos?: boolean
  dateFrom?: string
  dateTo?: string
}

export interface MeetingStats {
  totalNotes: number
  meetingNotes: number
  classroomNotes: number
  successNotes: number
  failedNotes: number
  speakerProfiles: number
  totalSegments: number
  totalTodos: number
  recentSevenDaysNotes: number
  latestCreateTime?: string
}

const getBaseUrl = () => getRuntimeHttpBaseUrl()

const getAuthHeaders = () => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const createMeetingNote = (formData: FormData) => {
  return http.post<any, BackendResponse<MeetingNoteResult>>('/api/meeting/upload', formData)
}

export const createMeetingNoteFromHistory = (historyId: number, formData: FormData) => {
  return http.post<any, BackendResponse<MeetingNoteResult>>(`/api/meeting/from-history/${historyId}`, formData)
}

export const getMyMeetingHistory = (params?: MeetingHistoryQuery) => {
  return http.get<any, BackendResponse<MeetingHistoryItem[]>>('/api/meeting/history', { params })
}

export const getMeetingHistoryDetail = (meetingId: number) => {
  return http.get<any, BackendResponse<MeetingHistoryItem>>(`/api/meeting/history/${meetingId}`)
}

export const getMeetingRevisions = (meetingId: number) => {
  return http.get<any, BackendResponse<MeetingRevisionItem[]>>(`/api/meeting/history/${meetingId}/revisions`)
}

export const applyMeetingCorrection = (meetingId: number, payload: MeetingCorrectionPayload) => {
  return http.put<any, BackendResponse<MeetingHistoryItem>>(`/api/meeting/history/${meetingId}/correction`, payload)
}

export const getMeetingStats = () => {
  return http.get<any, BackendResponse<MeetingStats>>('/api/meeting/stats')
}

export const fetchMeetingAudioBlob = async (meetingId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/meeting/history/${meetingId}/audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const fetchMeetingSegmentAudioBlob = async (meetingId: number, segmentId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/meeting/history/${meetingId}/segments/${segmentId}/audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const fetchMeetingExportBlob = async (
  meetingId: number,
  format: 'txt' | 'md' | 'docx',
  template?: MeetingExportTemplate
) => {
  const response = await axios.get(`${getBaseUrl()}/api/meeting/history/${meetingId}/export`, {
    params: {
      format,
      template: template ? JSON.stringify(template) : undefined
    },
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}
