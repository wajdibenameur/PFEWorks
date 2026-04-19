export interface UnifiedMonitoringResponse<T> {
  data: T;
  degraded: boolean;
  freshness: Record<string, string>;
  coverage: Record<string, string>;
}
