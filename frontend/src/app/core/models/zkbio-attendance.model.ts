export interface ZkBioAttendance {
  userId: string;
  userName: string;
  deviceId: string;
  deviceName: string;
  timestamp: number;
  verifyType: string;
  inOutMode: string;
  status: string;
  eventType: string;
  source: string;
}