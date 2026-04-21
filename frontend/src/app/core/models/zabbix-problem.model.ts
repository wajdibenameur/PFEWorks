export interface ZabbixProblem {
  problemId: string;
  host: string;
  port: number | null;
  hostId: string | null;
  description: string;
  severity: string;
  active: boolean;
  source: string;
  eventId: number;
  ip: string | null;
  startedAt?: number | null;
  startedAtFormatted?: string | null;
  resolvedAt?: number | null;
  resolvedAtFormatted?: string | null;
  status?: string | null;
}
