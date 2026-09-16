import http from '@/api'

export type KnowledgeDocument = {
  id: number
  title: string
  source: string
  version: string
  status: 'DRAFT' | 'PUBLISHED' | string
  validFrom?: string | null
  validUntil?: string | null
  filename: string
  chunkCount: number
}

export type KnowledgeCitation = {
  documentId: number
  title: string
  source: string
  version: string
  validFrom?: string | null
  validUntil?: string | null
  chunkId: number
  chunkNo: number
  page?: number | null
  excerpt: string
  score?: number
}

type BackendResponse<T> = { code: number, msg: string, data: T }

export const uploadKnowledgeDocument = (form: FormData) =>
  http.post<any, BackendResponse<KnowledgeDocument>>('/api/knowledge/documents', form)

export const publishKnowledgeDocument = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/publish`)

export const submitKnowledgeReview = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/submit-review`)

export const approveKnowledgeDocument = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/approve`)

export const offlineKnowledgeDocument = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/offline`)

export const restoreKnowledgeDocumentToDraft = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/restore-draft`)

export const reindexKnowledgeDocument = (documentId: number) =>
  http.post<any, BackendResponse<KnowledgeDocument>>(`/api/knowledge/documents/${documentId}/reindex`)

export const getKnowledgeDocuments = () =>
  http.get<any, BackendResponse<KnowledgeDocument[]>>('/api/knowledge/documents')

export const searchKnowledge = (query: string) =>
  http.post<any, BackendResponse<{
    citations: Record<string, KnowledgeCitation>
    results: Array<{ citationId: string, score: number }>
  }>>('/api/knowledge/search', null, { params: { query } })
