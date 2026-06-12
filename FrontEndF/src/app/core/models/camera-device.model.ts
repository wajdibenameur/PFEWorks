export interface CameraDevice {
  id?: number | null;
  source: string | null;
  name: string | null;
  site?: string | null;
  type?: string | null;
  ip: string | null;
  port: number | null;
  protocol: string | null;
  status: string | null;
  category: string | null;
  lastScanAt: string | null;
  reachable: boolean;
  persisted: boolean;
  snmpEnabled: boolean;
  snmpStatus: string | null;
  snmpSysName: string | null;
  snmpLastSeenAt: string | null;
  snmpUptimeSeconds: number | null;
  snmpCpuPercent: number | null;
  snmpMemoryPercent: number | null;
  snmpInterfaceCount: number | null;
  enabled?: boolean | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateCameraDeviceRequest {
  ipAddress: string;
  name?: string | null;
  site?: string | null;
  type?: string | null;
  port?: number | null;
  enabled?: boolean | null;
}

