import { apiClient } from './apiClient';

export type KnowledgeType = 'STRATEGY' | 'INVESTMENT_REVIEW' | 'RESEARCH' | 'GENERAL_NOTE';
export type KnowledgeFormat = 'TEXT' | 'MARKDOWN';
export type KnowledgeStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface KnowledgeDocument {
  id: string;
  title: string;
  type: KnowledgeType;
  format: KnowledgeFormat;
  body: string;
  tags: string[];
  status: KnowledgeStatus;
  version: number;
  sourceName?: string;
  createdAt: string;
  updatedAt: string;
  chunkCount: number;
  indexedCount: number;
  pendingCount: number;
  failedCount: number;
  chunks?: { id: string; section?: string; content: string }[];
}

export interface KnowledgePayload {
  title: string;
  type: KnowledgeType;
  format: KnowledgeFormat;
  body: string;
  tags: string[];
  status: KnowledgeStatus;
  expectedVersion?: number;
}

export interface KnowledgeVersion {
  version: number;
  title: string;
  body: string;
  format: KnowledgeFormat;
  tags: string[];
  status: KnowledgeStatus;
  createdAt: string;
}

export interface KnowledgeSource {
  id: string;
  documentId: string;
  title: string;
  type: KnowledgeType;
  version: number;
  updatedAt: string;
  section?: string;
  excerpt: string;
  score: number;
  status: KnowledgeStatus;
}

export interface KnowledgeSearchResult {
  results: KnowledgeSource[];
  mode: string;
  warning?: string;
}

export interface KnowledgePage {
  items: KnowledgeDocument[];
  total: number;
  page: number;
  pageSize: number;
}

export const knowledgeService = {
  list(filters: { query?: string; type?: KnowledgeType; status?: KnowledgeStatus; page?: number } = {}): Promise<KnowledgePage> {
    const params = new URLSearchParams();
    if (filters.query) params.set('query', filters.query);
    if (filters.type) params.set('type', filters.type);
    if (filters.status) params.set('status', filters.status);
    params.set('page', String(filters.page ?? 0));
    return apiClient.get<KnowledgePage>(`/knowledge/documents?${params}`);
  },
  get(id: string): Promise<KnowledgeDocument> {
    return apiClient.get<KnowledgeDocument>(`/knowledge/documents/${encodeURIComponent(id)}`);
  },
  create(payload: KnowledgePayload): Promise<KnowledgeDocument> {
    return apiClient.post<KnowledgeDocument>('/knowledge/documents', payload);
  },
  update(id: string, payload: KnowledgePayload): Promise<KnowledgeDocument> {
    return apiClient.put<KnowledgeDocument>(`/knowledge/documents/${encodeURIComponent(id)}`, payload);
  },
  setStatus(id: string, status: KnowledgeStatus, expectedVersion: number): Promise<KnowledgeDocument> {
    return apiClient.post<KnowledgeDocument>(`/knowledge/documents/${encodeURIComponent(id)}/status`, { status, expectedVersion });
  },
  versions(id: string): Promise<KnowledgeVersion[]> {
    return apiClient.get<KnowledgeVersion[]>(`/knowledge/documents/${encodeURIComponent(id)}/versions`);
  },
  remove(id: string): Promise<void> {
    return apiClient.delete(`/knowledge/documents/${encodeURIComponent(id)}`);
  },
  importDocuments(documents: (KnowledgePayload & { sourceName: string })[]): Promise<{ created: KnowledgeDocument[]; skipped: string[] }> {
    return apiClient.post<{ created: KnowledgeDocument[]; skipped: string[] }>('/knowledge/imports', { documents });
  },
  search(query: string, options: { type?: KnowledgeType; limit?: number; includeArchived?: boolean } = {}): Promise<KnowledgeSearchResult> {
    return apiClient.post<KnowledgeSearchResult>('/knowledge/search', { query, ...options });
  },
};
