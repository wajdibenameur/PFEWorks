import { Injectable, computed, signal } from '@angular/core';
import { ChatApiService } from '../data/chat-api.service';
import { ChatRealtimeService } from '../data/chat-realtime.service';
import { ChatMessage, ChatMessageVM, ChatParticipant, ChatPresenceEvent, ChatPresenceSnapshot, ChatRoom } from '../domain/chat.models';

@Injectable({ providedIn: 'root' })
export class ChatStoreService {
  readonly rooms = signal<ChatRoom[]>([]);
  readonly archivedRooms = signal<ChatRoom[]>([]);
  readonly selectedRoom = signal<ChatRoom | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly participants = signal<ChatParticipant[]>([]);
  readonly draft = signal('');
  readonly replyToMessageId = signal<number | null>(null);
  readonly currentUserId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly isLocked = computed(() => {
    const room = this.selectedRoom();
    return room?.status === 'CLOSED' || room?.archived === true;
  });
  readonly incidentRooms = computed(() => this.rooms().filter((room) => room.roomType === 'INCIDENT'));
  readonly privateRooms = computed(() => this.rooms().filter((room) => room.roomType === 'PRIVATE'));
  readonly openRooms = computed(() => this.rooms().filter((room) => room.status === 'OPEN' && !room.archived));
  readonly closedRooms = computed(() => this.rooms().filter((room) => room.status === 'CLOSED' && !room.archived));
  readonly presenceMap = computed(() => {
    const current = this.currentUserId();
    return this.participants().reduce<Record<number, 'online' | 'offline'>>((acc, participant) => {
      const isCurrentUser = current != null && participant.userId === current;
      acc[participant.userId] = isCurrentUser || participant.connected ? 'online' : 'offline';
      return acc;
    }, {});
  });
  readonly messageViewModels = computed<ChatMessageVM[]>(() => {
    const current = this.currentUserId();
    const byId = new Map(this.messages().map((message) => [message.id, message]));
    return this.messages().map((message) => {
      const senderDisplay = message.senderName || (message.senderUserId != null ? `User #${message.senderUserId}` : 'System');
      const isMine = current != null && message.senderUserId != null && message.senderUserId === current;
      const replied = message.replyToMessageId != null ? byId.get(message.replyToMessageId) : null;
      return {
        ...message,
        isMine,
        senderDisplay,
        replyPreview: replied?.content ? replied.content.slice(0, 80) : undefined
      };
    });
  });

  constructor(
    private readonly api: ChatApiService,
    private readonly realtime: ChatRealtimeService
  ) {}

  loadRooms(onRoomSelected: (roomId: number) => void): void {
    this.loading.set(true);
    this.api.getMyRooms().subscribe({
      next: (rooms) => {
        this.rooms.set(rooms);
        this.loadArchivedRooms();
        if (rooms.length > 0) {
          this.selectRoom(rooms[0], onRoomSelected);
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Unable to load chat rooms.');
        this.loading.set(false);
      }
    });
  }

  loadArchivedRooms(): void {
    this.api.getArchivedRooms().subscribe({
      next: (rooms) => this.archivedRooms.set(rooms),
      error: () => this.error.set('Unable to load archived chat rooms.')
    });
  }

  selectRoom(room: ChatRoom, onRoomSelected: (roomId: number) => void): void {
    this.selectedRoom.set(room);
    this.messages.set([]);
    this.participants.set([]);
    this.loadMessages(room.id);
    this.loadParticipants(room.id);
    onRoomSelected(room.id);
  }

  loadMessages(roomId: number): void {
    this.api.getMessages(roomId).subscribe({
      next: (page) => this.messages.set(this.mergeUniqueById([], page.content)),
      error: () => this.error.set('Unable to load messages.')
    });
  }

  loadParticipants(roomId: number): void {
    this.api.getParticipants(roomId).subscribe({
      next: (items) => this.participants.set(items),
      error: () => this.error.set('Unable to load participants.')
    });
  }

  onRealtimeMessage(message: ChatMessage): void {
    this.messages.update((current) => this.mergeUniqueById([message], current));
  }

  onPresenceEvent(event: ChatPresenceEvent): void {
    this.participants.update((current) =>
      current.map((participant) =>
        participant.userId === event.userId
          ? { ...participant, connected: event.status === 'online' }
          : participant
      )
    );
  }

  applyPresenceSnapshot(snapshot: ChatPresenceSnapshot): void {
    const statusByUser = new Map<number, boolean>(
      (snapshot.users ?? []).map((user) => [user.userId, user.status === 'ONLINE'])
    );
    this.participants.update((current) =>
      current.map((participant) => ({
        ...participant,
        connected: statusByUser.get(participant.userId) ?? participant.connected
      }))
    );
  }

  sendMessage(): void {
    const room = this.selectedRoom();
    const content = this.draft().trim();
    if (!room || !content || this.isLocked()) {
      console.debug('[CHAT STORE] sendMessage skipped', {
        hasRoom: !!room,
        hasContent: !!content,
        locked: this.isLocked()
      });
      return;
    }
    console.debug('[CHAT STORE] sendMessage dispatch', { roomId: room.id, contentLength: content.length });
    this.realtime.sendMessage(room.id, content, this.replyToMessageId());
    this.draft.set('');
    this.replyToMessageId.set(null);
  }

  setCurrentUserId(userId: number | null): void {
    this.currentUserId.set(userId);
  }

  setReplyToMessage(messageId: number | null): void {
    this.replyToMessageId.set(messageId);
  }

  patchRoomStatus(roomId: number, status: 'OPEN' | 'CLOSED'): void {
    const nowIso = new Date().toISOString();
    this.rooms.update((rooms) =>
      rooms.map((room) =>
        room.id === roomId
          ? { ...room, status, closedAt: status === 'CLOSED' ? nowIso : null }
          : room
      )
    );
    this.archivedRooms.update((rooms) =>
      rooms.map((room) =>
        room.id === roomId
          ? { ...room, status, closedAt: status === 'CLOSED' ? nowIso : null }
          : room
      )
    );
    const selected = this.selectedRoom();
    if (selected?.id === roomId) {
      this.selectedRoom.set({
        ...selected,
        status,
        closedAt: status === 'CLOSED' ? nowIso : null
      });
    }
  }

  replaceRoom(updatedRoom: ChatRoom): void {
    this.rooms.update((rooms) =>
      rooms.map((room) => (room.id === updatedRoom.id ? updatedRoom : room))
    );
    this.archivedRooms.update((rooms) =>
      rooms.map((room) => (room.id === updatedRoom.id ? updatedRoom : room))
    );
    const selected = this.selectedRoom();
    if (selected?.id === updatedRoom.id) {
      this.selectedRoom.set(updatedRoom);
    }
  }

  private mergeUniqueById(first: ChatMessage[], second: ChatMessage[]): ChatMessage[] {
    const map = new Map<number, ChatMessage>();
    for (const message of [...first, ...second]) {
      map.set(message.id, message);
    }
    return Array.from(map.values()).sort((a, b) => (a.id ?? 0) - (b.id ?? 0));
  }
}
