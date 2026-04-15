export interface ApiResponse<T> {
  success: boolean;
  message: string;
  source: string;
  data: T;
  errorCode?: string | null;
}
