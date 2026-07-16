/** API 统一响应格式 —— 后续对接 Spring Boot 时使用 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
}
