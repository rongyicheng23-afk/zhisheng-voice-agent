import { ref } from 'vue'
import type { UploadFile } from 'element-plus'

export interface AudioFileMeta {
  name: string
  size: number
  type: string
  lastModified: number
}

const STORAGE_KEY = 'audio-upload-metadata'

const readStoredMetadata = (): AudioFileMeta[] => {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return []
  }

  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    sessionStorage.removeItem(STORAGE_KEY)
    return []
  }
}

const uploadFiles = ref<UploadFile[]>([])
const fileMetadata = ref<AudioFileMeta[]>(readStoredMetadata())
const conversionText = ref('')
const lastHistoryId = ref<number | null>(null)

const persistMetadata = () => {
  if (!fileMetadata.value.length) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }

  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(fileMetadata.value))
}

const toMetadata = (file: UploadFile): AudioFileMeta => ({
  name: file.name,
  size: file.size ?? 0,
  type: file.raw?.type || 'application/octet-stream',
  lastModified: file.raw?.lastModified || 0
})

export const useAudioUploadState = () => {
  const setSelectedFiles = (files: UploadFile[]) => {
    uploadFiles.value = [...files]
    fileMetadata.value = uploadFiles.value.map(toMetadata)
    conversionText.value = ''
    lastHistoryId.value = null
    persistMetadata()
  }

  const removeFileByName = (fileName: string) => {
    uploadFiles.value = uploadFiles.value.filter(file => file.name !== fileName)
    fileMetadata.value = fileMetadata.value.filter(file => file.name !== fileName)

    if (!fileMetadata.value.length) {
      conversionText.value = ''
      lastHistoryId.value = null
    }

    persistMetadata()
  }

  const clearFiles = () => {
    uploadFiles.value = []
    fileMetadata.value = []
    conversionText.value = ''
    lastHistoryId.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  const getRawFileByName = (fileName: string): File | null => {
    const matched = uploadFiles.value.find(file => file.name === fileName)
    return matched?.raw as File || null
  }

  const setConversionResult = (text: string, historyId?: number | null) => {
    conversionText.value = text
    lastHistoryId.value = historyId ?? null
  }

  return {
    uploadFiles,
    fileMetadata,
    conversionText,
    lastHistoryId,
    setSelectedFiles,
    removeFileByName,
    clearFiles,
    getRawFileByName,
    setConversionResult
  }
}
