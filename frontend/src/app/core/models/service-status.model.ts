export interface ServiceStatus {
  source: string | null;
  name: string | null;
  ip: string | null;
  port: number | null;
  protocol: string | null;
  status: string | null;
  category: string | null;
  lastCheck: string | null;
}
