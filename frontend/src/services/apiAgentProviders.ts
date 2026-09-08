/**
 * AI 模型提供商配置 API Service（Spring Boot）
 *
 * 统一复用 apiClient 处理 ApiResponse 包装与业务错误。
 * 后端保证任何响应都不包含完整 API Key，仅返回掩码与配置状态。
 */
import { apiClient } from './apiClient';

export type ProviderProtocol = 'OPENAI_COMPATIBLE';

export interface AiProviderConfig {
  providerId: string;
  displayName: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  model: string;
  configured: boolean;
  maskedApiKey: string;
  createdAt: string;
  updatedAt: string;
}

/** 写入请求。apiKey 仅在新增或需要覆盖旧 Key 时提交。 */
export interface AiProviderConfigPayload {
  providerId: string;
  displayName: string;
  protocol: ProviderProtocol;
  baseUrl: string;
  model: string;
  apiKey?: string;
}

/** 连接测试结果：只含状态、提示与耗时，不含模型原始响应与 Key */
export interface AiProviderTestResult {
  success: boolean;
  message: string;
  latencyMs: number;
}

export const aiProviderService = {
  async getAll(): Promise<AiProviderConfig[]> {
    return apiClient.get<AiProviderConfig[]>('/agent/providers');
  },

  async create(payload: AiProviderConfigPayload): Promise<AiProviderConfig> {
    return apiClient.post<AiProviderConfig>('/agent/providers', payload);
  },

  async update(
    providerId: string,
    payload: AiProviderConfigPayload
  ): Promise<AiProviderConfig> {
    return apiClient.put<AiProviderConfig>(
      `/agent/providers/${encodeURIComponent(providerId)}`,
      payload
    );
  },

  async remove(providerId: string): Promise<void> {
    return apiClient.delete(
      `/agent/providers/${encodeURIComponent(providerId)}`
    );
  },

  async test(providerId: string): Promise<AiProviderTestResult> {
    return apiClient.post<AiProviderTestResult>(
      `/agent/providers/${encodeURIComponent(providerId)}/test`,
      {}
    );
  },
};
