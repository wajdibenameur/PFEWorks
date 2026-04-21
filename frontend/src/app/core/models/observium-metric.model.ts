export interface ObserviumMetric {
  hostId: string;
  hostName: string;
  itemId: string;
  metricKey: string;
  value: number | null;
  timestamp: number | null;
  ip: string | null;
  port: number | null;
}
