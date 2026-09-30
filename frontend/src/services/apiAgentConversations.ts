import { apiClient } from './apiClient';
import type { AgentChatResult } from './apiAgentChat';
import type { CreateSnapshotDraftResult, PendingActionStatus } from './apiAgentActions';

export interface AgentConversation {
  id: string;
  title: string;
  providerId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AgentConversationMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  response: AgentChatResult | null;
  actionId: string | null;
  createdAt: string;
}

export interface AgentConversationDetail {
  conversation: AgentConversation;
  messages: AgentConversationMessage[];
  hasMore: boolean;
}

const base = '/agent/conversations';

export const apiAgentConversations = {
  list: () => apiClient.get<AgentConversation[]>(base),
  create: () => apiClient.post<AgentConversation>(base, {}),
  detail: (id: string, page = 0) => apiClient.get<AgentConversationDetail>(`${base}/${id}?page=${page}`),
  rename: (id: string, title: string) => apiClient.patch<AgentConversation>(`${base}/${id}`, { title }),
  delete: (id: string) => apiClient.delete(`${base}/${id}`),
  send: (id: string, providerId: string, message: string) =>
    apiClient.post<AgentConversationMessage>(`${base}/${id}/messages`, { providerId, message }),
  importLegacy: (id: string, messages: { role: 'user' | 'assistant'; content: string }[]) =>
    apiClient.post<{ status: string }>(`${base}/${id}/import`, { messages }),
  actionStatus: (id: string, actionId: string) =>
    apiClient.get<{ status: PendingActionStatus }>(`${base}/${id}/actions/${actionId}/status`),
  attachManualDraft: (id: string, draft: CreateSnapshotDraftResult) =>
    apiClient.post<AgentConversationMessage>(`${base}/${id}/manual-drafts`, draft),
  recordActionOutcome: (id: string, actionId: string) =>
    apiClient.post<AgentConversationMessage>(`${base}/${id}/actions/${actionId}/record`, {}),
};
