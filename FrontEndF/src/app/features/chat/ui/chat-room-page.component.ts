import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { Ticket } from '../../../core/models/ticket.model';
import { TicketUser } from '../../../core/models/ticket-user.model';
import { TicketManagerApiService } from '../../tickets/data/ticket-manager-api.service';
import { Subject, takeUntil } from 'rxjs';
import { ChatApiService } from '../data/chat-api.service';
import { ChatRealtimeService } from '../data/chat-realtime.service';
import { ChatRoom } from '../domain/chat.models';
import { ChatStoreService } from '../state/chat-store.service';

@Component({
  selector: 'app-chat-room-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-room-page.component.html',
  styleUrls: ['./chat-room-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatRoomPageComponent implements OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly roomBindingDestroy$ = new Subject<void>();
  private readonly auth = inject(AUTH_CONTEXT);
  readonly store: ChatStoreService;

  readonly tickets = signal<Ticket[]>([]);
  readonly collaborators = signal<TicketUser[]>([]);
  readonly selectedTicketId = signal<number | null>(null);
  readonly roomName = signal('');
  readonly selectedCollaboratorId = signal<number | null>(null);
  readonly invitedCollaboratorIds = signal<number[]>([]);
  readonly roomTab = signal<'ACTIVE' | 'CLOSED' | 'ARCHIVED'>('ACTIVE');
  readonly selectedPrivateTargetId = signal<number | null>(null);
  readonly createError = signal<string | null>(null);
  readonly isCreating = signal(false);
  readonly isBootstrapLoading = signal(false);

  readonly canCreateRooms = computed(() => this.auth.hasRole('SUPERADMIN') || this.auth.hasRole('ADMIN'));
  readonly invitedCollaborators = computed(() => {
    const invited = new Set(this.invitedCollaboratorIds());
    return this.collaborators().filter((user) => invited.has(user.id));
  });
  readonly currentUser = signal<{ username: string } | null>(null);

  constructor(
    store: ChatStoreService,
    private readonly chatApi: ChatApiService,
    private readonly ticketApi: TicketManagerApiService,
    private readonly realtime: ChatRealtimeService
  ) {
    this.store = store;
    this.auth.user$.pipe(takeUntil(this.destroy$)).subscribe((user) => {
      this.currentUser.set(user ? { username: user.username } : null);
      const parsedId = user?.id != null ? Number(user.id) : NaN;
      this.store.setCurrentUserId(Number.isFinite(parsedId) ? parsedId : null);
    });
    this.store.loadRooms((roomId) => this.bindRealtime(roomId));
    this.loadCreationData();
  }

  selectRoom(room: ChatRoom): void {
    if (room.archived) {
      this.roomBindingDestroy$.next();
      this.store.selectRoom(room, () => {});
      return;
    }
    this.store.selectRoom(room, (roomId) => this.bindRealtime(roomId));
  }

  send(): void {
    this.store.sendMessage();
  }

  setRoomTab(tab: 'ACTIVE' | 'CLOSED' | 'ARCHIVED'): void {
    this.roomTab.set(tab);
  }

  closeCurrentRoom(): void {
    const room = this.store.selectedRoom();
    if (!room || room.status !== 'OPEN' || room.archived || !this.canCreateRooms()) {
      return;
    }
    this.store.patchRoomStatus(room.id, 'CLOSED');
    this.chatApi.closeRoom(room.id).subscribe({
      next: (updated) => this.store.replaceRoom(updated),
      error: () => {
        this.store.patchRoomStatus(room.id, 'OPEN');
        this.store.error.set('Unable to close room.');
      }
    });
  }

  reopenCurrentRoom(): void {
    const room = this.store.selectedRoom();
    if (!room || room.status === 'OPEN' || room.archived || !this.canCreateRooms()) {
      return;
    }
    this.store.patchRoomStatus(room.id, 'OPEN');
    this.chatApi.reopenRoom(room.id).subscribe({
      next: (updated) => this.store.replaceRoom(updated),
      error: () => {
        this.store.patchRoomStatus(room.id, 'CLOSED');
        this.store.error.set('Unable to reopen room.');
      }
    });
  }

  replyTo(messageId: number): void {
    this.store.setReplyToMessage(messageId);
  }

  clearReply(): void {
    this.store.setReplyToMessage(null);
  }

  formatTime(timestamp?: string): string {
    if (!timestamp) {
      return '';
    }
    const parsed = new Date(timestamp);
    if (Number.isNaN(parsed.getTime())) {
      return timestamp;
    }
    return parsed.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  scrollToMessage(messageId?: number | null): void {
    if (!messageId) {
      return;
    }
    const el = document.getElementById(`chat-message-${messageId}`);
    if (!el) {
      return;
    }
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('highlighted');
    window.setTimeout(() => el.classList.remove('highlighted'), 1400);
  }

  addCollaborator(): void {
    const userId = this.selectedCollaboratorId();
    if (!userId) {
      return;
    }
    this.invitedCollaboratorIds.update((current) =>
      current.includes(userId) ? current : [...current, userId]
    );
    this.selectedCollaboratorId.set(null);
  }

  removeCollaborator(userId: number): void {
    this.invitedCollaboratorIds.update((current) => current.filter((id) => id !== userId));
  }

  toNullableNumber(value: unknown): number | null {
    if (value == null || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  createIncidentRoom(): void {
    if (!this.canCreateRooms()) {
      return;
    }

    const ticketId = this.selectedTicketId();
    const name = this.roomName().trim();
    if (!ticketId || !name) {
      this.createError.set('Ticket and room name are required.');
      return;
    }

    this.isCreating.set(true);
    this.createError.set(null);

    this.chatApi
      .createIncidentRoom({
        ticketId,
        name,
        inviteUserIds: this.invitedCollaboratorIds()
      })
      .subscribe({
        next: (createdRoom) => {
          this.store.loadRooms((roomId) => this.bindRealtime(roomId));
          this.store.selectRoom(createdRoom, (roomId) => this.bindRealtime(roomId));
          this.selectedTicketId.set(null);
          this.roomName.set('');
          this.invitedCollaboratorIds.set([]);
          this.selectedCollaboratorId.set(null);
          this.isCreating.set(false);
        },
        error: (error) => {
          const message =
            typeof error?.error?.message === 'string'
              ? error.error.message
              : 'Unable to create incident room.';
          this.createError.set(message);
          this.isCreating.set(false);
        }
      });
  }

  createPrivateRoom(): void {
    const targetUserId = this.selectedPrivateTargetId();
    if (!targetUserId) {
      this.createError.set('Please select a user for private chat.');
      return;
    }

    this.isCreating.set(true);
    this.createError.set(null);

    this.chatApi.createPrivateRoom({ targetUserId }).subscribe({
      next: (createdRoom) => {
        this.store.loadRooms((roomId) => this.bindRealtime(roomId));
        this.store.selectRoom(createdRoom, (roomId) => this.bindRealtime(roomId));
        this.selectedPrivateTargetId.set(null);
        this.isCreating.set(false);
      },
      error: (error) => {
        const message =
          typeof error?.error?.message === 'string'
            ? error.error.message
            : 'Unable to create private chat.';
        this.createError.set(message);
        this.isCreating.set(false);
      }
    });
  }

  private loadCreationData(): void {
    if (!this.canCreateRooms()) {
      return;
    }

    this.isBootstrapLoading.set(true);

    this.ticketApi.getTickets({ size: 200, archived: 'active' }).subscribe({
      next: (page) => {
        this.tickets.set(page.content ?? []);
        this.isBootstrapLoading.set(false);
      },
      error: () => {
        this.createError.set('Unable to load active tickets.');
        this.isBootstrapLoading.set(false);
      }
    });

    this.ticketApi.getAssignableUsers().subscribe({
      next: (users) => this.collaborators.set(users),
      error: () => this.createError.set('Unable to load collaborators.')
    });
  }

  private bindRealtime(roomId: number): void {
    this.roomBindingDestroy$.next();
    this.realtime
      .presenceSnapshots()
      .pipe(takeUntil(this.destroy$), takeUntil(this.roomBindingDestroy$))
      .subscribe((snapshot) => {
        if (snapshot.roomId === roomId && snapshot.type === 'PRESENCE_SNAPSHOT') {
          this.store.applyPresenceSnapshot(snapshot);
        }
      });
    this.realtime
      .roomMessages(roomId)
      .pipe(takeUntil(this.destroy$), takeUntil(this.roomBindingDestroy$))
      .subscribe((msg) => this.store.onRealtimeMessage(msg));
    this.realtime
      .roomPresence(roomId)
      .pipe(takeUntil(this.destroy$), takeUntil(this.roomBindingDestroy$))
      .subscribe((event) => this.store.onPresenceEvent(event));
    this.realtime.requestPresenceSnapshot(roomId);
  }

  ngOnDestroy(): void {
    this.roomBindingDestroy$.next();
    this.roomBindingDestroy$.complete();
    this.destroy$.next();
    this.destroy$.complete();
  }
}
