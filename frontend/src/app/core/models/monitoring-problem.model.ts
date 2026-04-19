export interface MonitoringProblem {
  id: string;
  source: string;
  problemId: string | null;
  eventId: number | null;
  hostId: string | null;
  hostName: string | null;
  description: string | null;
  severity: string | null;
  active: boolean;
  status: string | null;
  ip?: string | null;
  port?: number | null;
  startedAt?: number | null;
  startedAtFormatted?: string | null;
  resolvedAt?: number | null;
  resolvedAtFormatted?: string | null;
}
