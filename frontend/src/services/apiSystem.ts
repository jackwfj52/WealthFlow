/**
 * 系统信息 API Service（Spring Boot 后端）
 */
import { apiClient } from './apiClient';
import type { SystemInfo, SystemService } from './types';

export const apiSystemService: SystemService = {
  getInfo(): Promise<SystemInfo> {
    return apiClient.get<SystemInfo>('/system/info');
  },

  clearAll(): Promise<void> {
    return apiClient.delete('/system/all-data');
  },
};
