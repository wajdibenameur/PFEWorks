export interface ZabbixMetric {
  hostId: string;
  hostName: string;
  itemId: string;
  metricKey: string;
  value: number;
  timestamp: number;
  ip: string | null;
  port: number | null;
}
