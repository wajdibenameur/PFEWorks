export interface ZkBioProblem {
  problemId: string;
  host: string;
  description: string;
  severity: string;
  active: boolean;
  status?: string | null;
  startedAt?: number | null;
  startedAtFormatted?: string | null;
  resolvedAt?: number | null;
  resolvedAtFormatted?: string | null;
  source: string;
  eventId: number;
}
