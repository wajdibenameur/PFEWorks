import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { StompClientService } from '../../../core/realtime/stomp-client.service';
import { ChatMessage, ChatPresenceEvent, ChatPresenceSnapshot } from './chat.models';

@Injectable({ providedIn: 'root' })
export class ChatRealtimeService {
  constructor(private readonly stomp: StompClientService) {}

  roomMessages(roomId: number): Observable<ChatMessage> {
    return this.stomp.subscribe<ChatMessage>(`/topic/chat.room.${roomId}`);
  }

  roomPresence(roomId: number): Observable<ChatPresenceEvent> {
    return this.stomp.subscribe<ChatPresenceEvent>(`/topic/chat.presence.${roomId}`);
  }

  presenceSnapshots(): Observable<ChatPresenceSnapshot> {
    return this.stomp.subscribe<ChatPresenceSnapshot>('/user/queue/chat.presence');
  }

  requestPresenceSnapshot(roomId: number): void {
    this.stomp.publish('/app/chat.presence.snapshot', { roomId });
  }

  sendMessage(roomId: number, content: string, replyToMessageId?: number | null): void {
    console.debug('[CHAT WS] sendMessage', { roomId, contentLength: content.length });
    this.stomp.publish('/app/chat.sendMessage', {
      roomId,
      content,
      messageType: 'USER',
      replyToMessageId: replyToMessageId ?? null
    });
  }
}
