/**
 * AI 助手聊天 API Service（Spring Boot）
 *
 * 只发送最近 8 条 user/assistant 历史消息；绝不发送 API Key、
 * 系统提示词或草案内部 payload。draft 为后端校验通过后的
 * 待确认草案；最高权限下后端会直接执行并返回 execution。
 */
import { apiClient } from './apiClient';
import type { KnowledgeSource } from './apiKnowledge';
import type {
  CreateSnapshotDraftResult,
  DeleteSnapshotDraftResult,
  BatchSnapshotDraftResult,
  PendingActionExecutionResult,
} from './apiAgentActions';

export interface AgentChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface AgentChatPayload {
  providerId: string;
  message: string;
  history: AgentChatMessage[];
}

/**
 * 聊天响应：draft / deleteDraft 仅在模型建议对应操作且后端校验通过时非空（两者互斥）；
 * draftError 在草案无法创建时给出用户友好提示；execution 为直接执行结果。
 */
export interface AgentChatResult {
  reply: string;
  draft: CreateSnapshotDraftResult | null;
  deleteDraft: DeleteSnapshotDraftResult | null;
  draftError: string | null;
  batchDraft: BatchSnapshotDraftResult | null;
  execution: PendingActionExecutionResult | null;
  steps: string[];
  /** Older conversations predate source persistence. */
  sources?: KnowledgeSource[];
}

export const apiAgentChat = {
  async chat(payload: AgentChatPayload): Promise<AgentChatResult> {
    return apiClient.post<AgentChatResult>('/agent/chat', payload);
  },
};
