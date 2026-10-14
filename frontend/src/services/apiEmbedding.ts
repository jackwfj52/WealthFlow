import { apiClient } from './apiClient';

export type EmbeddingMode = 'REUSE' | 'CUSTOM';

/** API Key 永不回填，接口仅返回掩码。 */
export interface EmbeddingConfig {
  mode: EmbeddingMode;
  providerId: string | null;
  baseUrl: string | null;
  model: string;
  enabled: boolean;
  configured: boolean;
  maskedApiKey: string | null;
  fingerprint: string | null;
}

export interface EmbeddingConfigPayload {
  mode: EmbeddingMode;
  providerId?: string;
  baseUrl?: string;
  model: string;
  apiKey?: string;
  enabled: boolean;
}

export interface EmbeddingTestResult {
  success: boolean;
  message: string;
  latencyMs: number;
  dimensions: number | null;
}

export interface KnowledgeIndexStatus {
  total: number;
  indexed: number;
  pending: number;
  failed: number;
  processing: boolean;
  model: string | null;
  enabled: boolean;
  warning: string | null;
}

export const embeddingService = {
  getConfig(): Promise<EmbeddingConfig> {
    return apiClient.get<EmbeddingConfig>('/knowledge/embedding');
  },

  saveConfig(payload: EmbeddingConfigPayload): Promise<EmbeddingConfig> {
    return apiClient.put<EmbeddingConfig>('/knowledge/embedding', payload);
  },

  /** 后端使用固定公开文本验证真实 Embedding 响应，不发送知识库原文。 */
  test(): Promise<EmbeddingTestResult> {
    return apiClient.post<EmbeddingTestResult>('/knowledge/embedding/test', {});
  },

  getIndexStatus(): Promise<KnowledgeIndexStatus> {
    return apiClient.get<KnowledgeIndexStatus>('/knowledge/index-status');
  },

  reindex(): Promise<KnowledgeIndexStatus> {
    return apiClient.post<KnowledgeIndexStatus>('/knowledge/reindex', {});
  },
};
