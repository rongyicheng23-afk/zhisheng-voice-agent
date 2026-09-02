import axios from 'axios'
import http, { getRuntimeHttpBaseUrl } from '@/api/index'

export interface AudioHistoryItem {
  id: number
  userId: number
  bucket?: string
  object?: string
  originalFilename: string
  contentType?: string
  fileSize?: number
  requestMode?: string
  funasrMode?: string
  status?: string
  transcription?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
  hasAudio?: boolean
  audioUrl?: string
}

export interface MinioAudioObjectItem {
  bucket: string
  object: string
  filename: string
  size?: number
  lastModified?: string
  requestMode?: string
  contentType?: string
  audioUrl?: string
}

interface FunasrResponse<T> {
  code: number
  msg: string
  data: T
}

const getBaseUrl = () => getRuntimeHttpBaseUrl()

const getAuthHeaders = () => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const getMyAudioHistory = () => {
  return http.get<any, FunasrResponse<AudioHistoryItem[]>>('/api/funasr/history')
}

export const getMyMinioAudioObjects = () => {
  return http.get<any, FunasrResponse<MinioAudioObjectItem[]>>('/api/funasr/minio/files')
}

export const fetchHistoryAudioBlob = async (historyId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/funasr/history/${historyId}/audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const fetchMinioAudioBlob = async (object: string) => {
  const response = await axios.get(`${getBaseUrl()}/api/funasr/minio/audio`, {
    params: { object },
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}

export const deleteAudioHistory = (historyId: number) => {
  return http.delete<any, FunasrResponse<null>>(`/api/funasr/history/${historyId}`)
}
