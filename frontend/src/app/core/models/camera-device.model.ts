export interface CameraDevice {
  source: string | null;
  name: string | null;
  ip: string | null;
  port: number | null;
  protocol: string | null;
  status: string | null;
  category: string | null;
  lastScanAt: string | null;
  reachable: boolean;
  persisted: boolean;
}
