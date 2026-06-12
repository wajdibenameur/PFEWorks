export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface NotificationItem {
  id: number;
  eventId: string;
  title: string;
  message: string;
  eventType: string;
  severity: NotificationSeverity | string;
  entityType: string;
  entityId: number | null;
  actionUrl: string | null;
  read: boolean;
  archived: boolean;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

