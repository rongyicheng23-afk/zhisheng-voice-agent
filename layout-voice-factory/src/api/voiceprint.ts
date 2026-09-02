import axios from 'axios'
import http, { getRuntimeHttpBaseUrl } from '@/api/index'

export interface VoiceprintCompareResult {
  historyId: number
  userId: number
  leftFilename?: string
  rightFilename?: string
  leftObject?: string
  rightObject?: string
  score?: number
  threshold?: number
  samePerson?: boolean
  message?: string
}

export interface VoiceprintHistoryItem {
  id: number
  userId: number
  leftBucket?: string
  leftObject?: string
  leftFilename?: string
  leftContentType?: string
  leftFileSize?: number
  rightBucket?: string
  rightObject?: string
  rightFilename?: string
  rightContentType?: string
  rightFileSize?: number
  score?: number
  thresholdValue?: number
  samePerson?: boolean
  resultMessage?: string
  status?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
  hasLeftAudio?: boolean
  hasRightAudio?: boolean
  leftAudioUrl?: string
  rightAudioUrl?: string
}

interface BackendResponse<T> {
  code: number
  msg: string
  data: T
}

const getBaseUrl = () => getRuntimeHttpBaseUrl()

const getAuthHeaders = () => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const compareVoiceprint = (formData: FormData) => {
  return http.post<any, BackendResponse<VoiceprintCompareResult>>('/api/voiceprint/compare', formData)
}

export const getMyVoiceprintHistory = () => {
  return http.get<any, BackendResponse<VoiceprintHistoryItem[]>>('/api/voiceprint/history')
}

export const fetchVoiceprintAudioBlob = async (historyId: number, side: 'a' | 'b') => {
  const response = await axios.get(`${getBaseUrl()}/api/voiceprint/history/${historyId}/audio-${side}`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const deleteVoiceprintHistory = (historyId: number) => {
  return http.delete<any, BackendResponse<null>>(`/api/voiceprint/history/${historyId}`)
}
