import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { PageResponse } from '../../../core/models/page-response.model';
import { ChatMessage, ChatParticipant, ChatRoom } from './chat.models';

export interface CreateIncidentRoomPayload {
  ticketId: number;
  name: string;
  inviteUserIds: number[];
}

export interface CreatePrivateRoomPayload {
  targetUserId: number;
}

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly baseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.baseUrl = `${config.monitoringApiUrl}/api/chat`;
  }

  getMyRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.baseUrl}/rooms`);
  }

  getArchivedRooms(): Observable<ChatRoom[]> {
    return this.http.get<ChatRoom[]>(`${this.baseUrl}/rooms/archived`);
  }

  getMessages(roomId: number, page = 0, size = 50): Observable<PageResponse<ChatMessage>> {
    return this.http.get<PageResponse<ChatMessage>>(`${this.baseUrl}/rooms/${roomId}/messages?page=${page}&size=${size}`);
  }

  joinRoom(roomId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/rooms/${roomId}/join`, {});
  }

  leaveRoom(roomId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/rooms/${roomId}/leave`, {});
  }

  getParticipants(roomId: number): Observable<ChatParticipant[]> {
    return this.http.get<ChatParticipant[]>(`${this.baseUrl}/rooms/${roomId}/participants`);
  }

  createIncidentRoom(payload: CreateIncidentRoomPayload): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.baseUrl}/rooms/incident-rooms`, payload);
  }

  createPrivateRoom(payload: CreatePrivateRoomPayload): Observable<ChatRoom> {
    return this.http.post<ChatRoom>(`${this.baseUrl}/rooms/private-rooms`, payload);
  }

  closeRoom(roomId: number): Observable<ChatRoom> {
    return this.http.put<ChatRoom>(`${this.baseUrl}/rooms/${roomId}/close`, {});
  }

  reopenRoom(roomId: number): Observable<ChatRoom> {
    return this.http.put<ChatRoom>(`${this.baseUrl}/rooms/${roomId}/reopen`, {});
  }
}
