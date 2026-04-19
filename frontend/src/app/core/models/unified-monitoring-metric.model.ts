export type MonitoringSourceType = 'ZABBIX' | 'OBSERVIUM' | 'ZKBIO' | 'CAMERA';

export interface UnifiedMonitoringMetric {
  id: string;
  source: MonitoringSourceType;
  hostId: string;
  hostName: string;
  itemId: string;
  metricKey: string;
  value: number | null;
  timestamp: number | null;
  ip: string | null;
  port: number | null;
}