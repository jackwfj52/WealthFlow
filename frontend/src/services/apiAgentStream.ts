import { ApiError, BASE_URL } from './apiClient';
import { readServerEvents } from './sse';
import type { AgentConversationMessage } from './apiAgentConversations';

export interface AgentProgressEvent {
  id: string;
  tool: string;
  label: string;
  status: 'running' | 'completed' | 'failed';
  detail: string;
  at: string;
}

export async function streamAgentMessage(id: string, providerId: string, message: string,
  onProgress: (event: AgentProgressEvent) => void, onHeartbeat: () => void, signal?: AbortSignal): Promise<AgentConversationMessage> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}/agent/conversations/${encodeURIComponent(id)}/messages/stream`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ providerId, message }), signal,
    });
  } catch {
    throw new ApiError(-1, 0, '无法连接助手服务，请确认后端已启动');
  }
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
    const payload = await response.json().catch(() => null);
    if (response.ok && payload?.code === 0 && payload.data?.response) return payload.data;
    throw new ApiError(payload?.code ?? response.status, response.status,
      payload?.message ?? '助手进度连接失败，请刷新会话核对后重试');
  }
  if (!response.body) throw new ApiError(-1, 0, '浏览器无法读取助手进度');
  let result: AgentConversationMessage | undefined;
  try { await readServerEvents(response.body, ({ event, data }) => {
    if (event === 'progress') { onProgress(JSON.parse(data) as AgentProgressEvent); onHeartbeat(); }
    if (event === 'heartbeat') onHeartbeat();
    if (event === 'error') {
      const error = JSON.parse(data) as { code: number; message: string };
      throw new ApiError(error.code, response.status, error.message);
    }
    if (event === 'result') { result = JSON.parse(data) as AgentConversationMessage; return false; }
  }); } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(-1, 0, '进度连接已中断，后台可能仍在处理。请刷新会话核对结果，避免重复提交。');
  }
  if (!result) throw new ApiError(-1, 0, '进度连接已中断，后台可能仍在处理。请刷新会话核对结果，避免重复提交。');
  return result;
}
