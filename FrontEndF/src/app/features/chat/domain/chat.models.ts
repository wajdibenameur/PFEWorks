export interface ChatRoom {
  id: number;
  roomType: 'INCIDENT' | 'PRIVATE';
  status: 'OPEN' | 'CLOSED';
  name: string;
  ticketId?: number | null;
  closedAt?: string | null;
  archived?: boolean;
  archivedAt?: string | null;
  createdByUserId: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ChatMessage {
  id: number;
  roomId: number;
  senderUserId?: number | null;
  senderName?: string | null;
  senderRole?: string | null;
  messageType: 'USER' | 'SYSTEM' | 'ALERT';
  content: string;
  replyToMessageId?: number | null;
  createdAt?: string;
}

export interface ChatMessageVM extends ChatMessage {
  isMine: boolean;
  senderDisplay: string;
  replyPreview?: string;
}

export interface ChatParticipant {
  userId: number;
  username: string;
  role: string;
  connected: boolean;
}

export interface ChatPresenceEvent {
  roomId: number;
  userId: number;
  username: string;
  status: 'online' | 'offline';
}

export interface ChatPresenceSnapshotUser {
  userId: number;
  status: 'ONLINE' | 'OFFLINE';
}

export interface ChatPresenceSnapshot {
  roomId: number;
  type: 'PRESENCE_SNAPSHOT';
  users: ChatPresenceSnapshotUser[];
}
