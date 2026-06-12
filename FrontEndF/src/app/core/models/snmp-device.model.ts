export interface SnmpDevice {
  id: number;
  ipAddress: string;
  hostname: string | null;
  type?: string | null;
  category: string | null;
  deviceGroup?: string | null;
  snmpPort: number;
  snmpCommunity: string | null;
  snmpVersion: string | null;
  pollingIntervalSeconds?: number | null;
  metricsToPoll?: string[] | null;
  status: string | null;
  lastSeen: string | null;
  lastPolledAt?: string | null;
  lastSuccessAt?: string | null;
  lastFailureAt?: string | null;
  failureCount?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  enabled: boolean;
  manualEntry: boolean;
}

export interface CreateSnmpDeviceRequest {
  ipAddress: string;
  hostname?: string | null;
  type?: string | null;
  category: string;
  deviceGroup?: string | null;
  snmpPort?: number;
  snmpCommunity?: string | null;
  snmpVersion?: string | null;
  pollingIntervalSeconds?: number | null;
  metricsToPoll?: string[] | null;
  enabled?: boolean;
}
