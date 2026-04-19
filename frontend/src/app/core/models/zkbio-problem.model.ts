export interface ZkBioProblem {
  problemId: string;
  host: string;
  description: string;
  severity: string;
  active: boolean;
  source: string;
  eventId: number;
}