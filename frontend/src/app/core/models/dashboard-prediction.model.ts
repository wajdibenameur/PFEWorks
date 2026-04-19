export interface DashboardPrediction {
  hostid: number;
  hostName: string;
  prediction: number;
  probability: number;
  status: 'RISK' | 'WATCH' | 'NORMAL' | string;
}
