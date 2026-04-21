import { DashboardAnomaly } from '../../../core/models/dashboard-anomaly.model';
import { DashboardPrediction } from '../../../core/models/dashboard-prediction.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';

export interface ZabbixOverviewVm {
  totalHosts: number;
  totalMetrics: number;
  totalProblems: number;
  activeProblems: number;
  downHosts: number;
  riskyHosts: number;
  anomalies: number;
  availability: 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN';
  availabilityNote: string;
}

export interface ZabbixQualityVm {
  warnings: string[];
  metricsWithoutTimestamp: number;
  problemsWithoutStartedAt: number;
  problemsWithoutResolvedAt: number;
  severityDistribution: Array<{ severity: string; count: number }>;
}

export interface ZabbixProblemVm extends ZabbixProblem {
  severityLabel: string;
  severityTone: 'critical' | 'high' | 'medium' | 'warning' | 'info' | 'neutral';
  statusLabel: string;
}

export interface ZabbixMetricVm extends ZabbixMetric {
  hostLabel: string;
}

export interface ZabbixPredictionVm extends DashboardPrediction {
  displayStatus: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable';
  riskTone: 'healthy' | 'watch' | 'risk' | 'unavailable';
  riskLevel: 1 | 2 | 3;
  explanation: string;
}

export interface ZabbixAnomalyVm extends DashboardAnomaly {
  scorePercent: number;
}

export interface ZabbixHostVm {
  key: string;
  hostId: string | null;
  hostName: string;
  ip: string | null;
  totalMetrics: number;
  totalProblems: number;
  activeProblems: number;
  anomalyCount: number;
  latestTimestamp: number | null;
  riskStatus: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable';
  statusTone: 'critical' | 'warning' | 'normal' | 'unavailable';
}

export interface ZabbixHostMetricVm {
  metricKey: string;
  label: string;
  currentValue: number;
  previousValue: number | null;
  timestamp: number | null;
  trend: 'up' | 'down' | 'flat';
}

export interface ZabbixMetricGroupVm {
  category: 'CPU' | 'Memory' | 'Network' | 'Disk' | 'System' | 'Other';
  metrics: ZabbixHostMetricVm[];
}
