import axios from 'axios'
import http, { getRuntimeHttpBaseUrl } from '@/api/index'

export interface TtsSynthesizeResult {
  historyId: number
  userId: number
  text: string
  emotion: string
  language: string
  requestedFormat: string
  sourceFilename?: string
  sourceObject?: string
  resultFilename?: string
  resultObject?: string
  resultContentType?: string
  resultFileSize?: number
}

export interface TtsHistoryItem {
  id: number
  userId: number
  inputText: string
  emotion: string
  language: string
  requestedFormat: string
  sourceBucket?: string
  sourceObject?: string
  sourceFilename?: string
  sourceContentType?: string
  sourceFileSize?: number
  resultBucket?: string
  resultObject?: string
  resultFilename?: string
  resultContentType?: string
  resultFileSize?: number
  status?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
  hasSourceAudio?: boolean
  hasResultAudio?: boolean
  sourceAudioUrl?: string
  resultAudioUrl?: string
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

export const synthesizeTextToVoice = (formData: FormData) => {
  return http.post<any, BackendResponse<TtsSynthesizeResult>>('/api/tts/synthesize', formData)
}

export const getMyTtsHistory = () => {
  return http.get<any, BackendResponse<TtsHistoryItem[]>>('/api/tts/history')
}

export const fetchTtsSourceAudioBlob = async (historyId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/tts/history/${historyId}/source-audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const fetchTtsResultAudioBlob = async (historyId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/tts/history/${historyId}/result-audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const deleteTtsHistory = (historyId: number) => {
  return http.delete<any, BackendResponse<null>>(`/api/tts/history/${historyId}`)
}
