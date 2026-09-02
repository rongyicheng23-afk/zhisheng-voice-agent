import http from '@/api/index'

export interface FunasrAsrResponse {
  code: number
  msg: string
  data: {
    historyId: number
    userId: number
    object: string
    transcription: string
    funasrResponse: unknown
  }
}

function uploadFile(params: { 
  file: File;
  batchSizeS?: number;
  hotword?: string;
}) {
  const formData = new FormData()
  formData.append('file', params.file)

  if (params.batchSizeS) {
    formData.append('batchSizeS', params.batchSizeS.toString())
  }

  if (params.hotword) {
    formData.append('hotword', params.hotword)
  }

  return http.post<any, FunasrAsrResponse>('/api/funasr/asr', formData)
}

export  {
  uploadFile
}
