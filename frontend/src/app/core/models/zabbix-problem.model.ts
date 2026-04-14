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
  ip: string;
}
