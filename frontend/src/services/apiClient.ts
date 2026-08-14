/**
 * 真实 API 客户端（Spring Boot 后端）
 *
 * 统一处理：
 * - ApiResponse<T> 包装结构 { code, message, data }，code === 0 表示成功
 * - 后端业务错误（HTTP 4xx/5xx 或非零 code）统一抛出 ApiError，message 可直接展示
 * - 删除接口成功返回 204 No Content，无响应体
 */
const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

/** API 调用错误：message 为可直接展示给用户的提示文案 */
export class ApiError extends Error {
  /** 后端业务错误码（ErrorCode.code）；网络失败时为 -1 */
  readonly code: number;
  /** HTTP 状态码；网络失败时为 0 */
  readonly status: number;
  /** 后端返回的 data（如 SNAPSHOT_DATE_EXISTS 时的 existingId） */
  readonly data: unknown;

  constructor(code: number, status: number, message: string, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.data = data;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
  };

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(-1, 0, '无法连接服务器，请确认后端服务已启动');
  }

  // 删除接口成功返回 204 No Content，无响应体
  if (response.status === 204) {
    return undefined as T;
  }

  let payload: ApiResponse<T> | undefined;
  try {
    payload = (await response.json()) as ApiResponse<T>;
  } catch {
    // 非 JSON 响应（如网关 502），按 HTTP 状态兜底处理
  }

  if (!response.ok || !payload || payload.code !== 0) {
    throw new ApiError(
      payload?.code ?? response.status,
      response.status,
      payload?.message ?? `请求失败（HTTP ${response.status}）`,
      payload?.data
    );
  }

  return payload.data;
}

export const apiClient = {
  get<T>(path: string): Promise<T> {
    return request<T>(path);
  },
  post<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'POST', body: JSON.stringify(body) });
  },
  put<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
  },
  patch<T>(path: string, body: unknown): Promise<T> {
    return request<T>(path, { method: 'PATCH', body: JSON.stringify(body) });
  },
  delete(path: string): Promise<void> {
    return request<void>(path, { method: 'DELETE' });
  },
};
