export type AssetCategory = 'SERVER' | 'PRINTER' | 'CAMERA' | 'ACCESS_CONTROL' | 'UNKNOWN';
export type AssetStatus = 'UP' | 'DOWN' | 'UNKNOWN';
export type MonitoringSource = 'ZABBIX' | 'OBSERVIUM' | 'CAMERA' | 'ZKBIO';

export interface GlobalAssetVm {
  id: string;
  hostname: string;
  address: string;
  ip: string;
  port: number | null;
  source: MonitoringSource;
  category: AssetCategory;
  status: AssetStatus;
  hasActiveAlert: boolean;
  problemCount: number;
  lastMetricTimestamp: number | null;
}

export interface GlobalKpiVm {
  totalMonitoredAssets: number;
  totalDownAssets: number;
  serversTotal: number;
  serversDown: number;
  printersTotal: number;
  printersDown: number;
}

export interface ProblemSummaryVm {
  totalAlerts: number;
  critical: number;
  high: number;
  medium: number;
  warning: number;
  info: number;
}

export interface SourceHealthVm {
  source: MonitoringSource;
  total: number | null;
  down: number | null;
  coverage: 'native' | 'synthetic' | 'not_applicable' | 'unknown';
  availability: 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN';
  note: string;
}

export interface DataCoverageVm {
  title: string;
  status: 'REAL' | 'PARTIAL' | 'ESTIMATED';
  detail: string;
}
