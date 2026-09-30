import { apiClient } from './apiClient';

export type AiPermissionLevel = 'READ_ONLY' | 'REVIEW' | 'DIRECT';

export interface AiPermissionResponse {
  level: AiPermissionLevel;
}

export const apiAgentPermission = {
  get(): Promise<AiPermissionResponse> {
    return apiClient.get<AiPermissionResponse>('/agent/permission');
  },
  update(level: AiPermissionLevel): Promise<AiPermissionResponse> {
    return apiClient.put<AiPermissionResponse>('/agent/permission', { level });
  },
};
