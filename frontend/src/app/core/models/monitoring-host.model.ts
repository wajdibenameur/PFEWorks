export interface MonitoringHost {
  id: string;
  source: string;
  hostId: string | null;
  name: string | null;
  ip: string | null;
  port: number | null;
  protocol: string | null;
  status: string | null;
  category: string | null;
}
