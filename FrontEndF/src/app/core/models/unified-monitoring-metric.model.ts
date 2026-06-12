export type MonitoringSourceType = 'ZABBIX' | 'SNMP' | 'ZKBIO' | 'CAMERA';

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
