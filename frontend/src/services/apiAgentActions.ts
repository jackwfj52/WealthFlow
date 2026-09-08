/**
 * Agent 待确认操作真实 API Service（Spring Boot）
 *
 * 用于创建快照草案与确认执行，统一复用 apiClient 处理
 * ApiResponse 包装结构与业务错误（ApiError）。
 */
import { apiClient } from './apiClient';
import type { AssetSnapshot } from '../types/domain';

export type PendingActionStatus =
  | 'PENDING'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'EXECUTING'
  | 'EXECUTED'
  | 'FAILED';

export type PendingActionType =
  | 'CREATE_SNAPSHOT'
  | 'UPDATE_SNAPSHOT'
  | 'DELETE_SNAPSHOT'
  | 'CREATE_CATEGORY'
  | 'UPDATE_CATEGORY'
  | 'DELETE_CATEGORY';

/** 草案明细项（后端将 categoryId / amount 序列化为字符串） */
export interface DraftSnapshotItem {
  categoryId: string;
  categoryName: string;
  amount: string;
}

export interface CreateSnapshotDraftPayload {
  snapshotDate: string;
  items: { categoryId: string; amount: string }[];
}

/** 创建快照草案响应：待确认卡渲染所需字段 */
export interface CreateSnapshotDraftResult {
  actionId: string;
  actionType: PendingActionType;
  status: PendingActionStatus;
  displaySummary: string;
  expiresAt: string;
  snapshotDate: string;
  items: DraftSnapshotItem[];
  totalAmount: string;
}

/** 确认执行结果：snapshot 仅在 status 为 EXECUTED 时存在 */
export interface PendingActionExecutionResult {
  actionId: string;
  actionType: PendingActionType;
  status: PendingActionStatus;
  displaySummary: string;
  snapshot: AssetSnapshot | null;
}

/** 取消待确认操作后的响应，不包含草案的内部 payload。 */
export interface PendingActionCancellationResult {
  actionId: string;
  actionType: PendingActionType;
  status: PendingActionStatus;
  displaySummary: string;
  expiresAt: string;
}

export const apiAgentActions = {
  /** 创建待确认的快照草案（服务端校验并生成展示文案） */
  async createSnapshotDraft(
    payload: CreateSnapshotDraftPayload
  ): Promise<CreateSnapshotDraftResult> {
    return apiClient.post<CreateSnapshotDraftResult>(
      '/agent/actions/snapshot-drafts',
      payload
    );
  },

  /** 确认执行待确认操作 */
  async confirmAction(
    actionId: string
  ): Promise<PendingActionExecutionResult> {
    return apiClient.post<PendingActionExecutionResult>(
      `/agent/actions/${actionId}/confirm`,
      {}
    );
  },

  /** 取消仍处于 PENDING 状态的待确认操作。 */
  async cancelAction(
    actionId: string
  ): Promise<PendingActionCancellationResult> {
    // apiClient.post 需要 body；后端取消接口不使用请求体。
    return apiClient.post<PendingActionCancellationResult>(
      `/agent/actions/${actionId}/cancel`,
      {}
    );
  },
};
