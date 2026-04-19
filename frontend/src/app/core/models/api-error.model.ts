export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  errorCode?: string | null;
  message?: string | null;
  source?: string | null;
  path?: string | null;
}
