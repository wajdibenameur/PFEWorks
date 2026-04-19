import { DashboardAnomaly } from './dashboard-anomaly.model';
import { DashboardPrediction } from './dashboard-prediction.model';

export interface DashboardOverview {
  activeProblems: number;
  problemsBySeverity: Record<string, number>;
  predictions: DashboardPrediction[];
  anomalies: DashboardAnomaly[];
  dataQuality: Record<string, unknown>;
  warning: string | null;
}
