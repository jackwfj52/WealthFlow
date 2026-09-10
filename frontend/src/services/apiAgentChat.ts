/**
 * AI 助手聊天 API Service（Spring Boot）
 *
 * 只发送最近 8 条 user/assistant 历史消息；绝不发送 API Key、
 * 系统提示词或草案内部 payload。draft 为后端校验通过后的
 * 待确认草案，仍须用户点击确认才会写入。
 */
import { apiClient } from './apiClient';
import type { CreateSnapshotDraftResult } from './apiAgentActions';

export interface AgentChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface AgentChatPayload {
  providerId: string;
  message: string;
  history: AgentChatMessage[];
}

/** 聊天响应：draft 仅在模型建议创建快照且后端校验通过时非空 */
export interface AgentChatResult {
  reply: string;
  draft: CreateSnapshotDraftResult | null;
  draftError: string | null;
}

export const apiAgentChat = {
  async chat(payload: AgentChatPayload): Promise<AgentChatResult> {
    return apiClient.post<AgentChatResult>('/agent/chat', payload);
  },
};
