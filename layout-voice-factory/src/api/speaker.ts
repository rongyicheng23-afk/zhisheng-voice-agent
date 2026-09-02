import axios from 'axios'
import http, { getRuntimeHttpBaseUrl } from '@/api/index'

export interface SpeakerProfileItem {
  id: number
  userId: number
  speakerName: string
  speakerRole?: string
  sampleBucket?: string
  sampleObject?: string
  sampleFilename?: string
  sampleContentType?: string
  sampleFileSize?: number
  status?: string
  createTime?: string
  updateTime?: string
  hasSampleAudio?: boolean
  sampleAudioUrl?: string
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

export const registerSpeakerProfile = (formData: FormData) => {
  return http.post<any, BackendResponse<SpeakerProfileItem>>('/api/speaker/register', formData)
}

export const getMySpeakerProfiles = () => {
  return http.get<any, BackendResponse<SpeakerProfileItem[]>>('/api/speaker/list')
}

export const deleteSpeakerProfile = (profileId: number) => {
  return http.delete<any, BackendResponse<null>>(`/api/speaker/${profileId}`)
}

export const fetchSpeakerSampleAudioBlob = async (profileId: number) => {
  const response = await axios.get(`${getBaseUrl()}/api/speaker/${profileId}/audio`, {
    responseType: 'blob',
    headers: getAuthHeaders()
  })
  return response.data as Blob
}
