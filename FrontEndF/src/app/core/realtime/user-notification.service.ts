import { Injectable, computed, signal } from '@angular/core';
import { catchError, finalize, of } from 'rxjs';
import { NotificationApiService } from '../data/notification-api.service';
import { NotificationItem } from '../models/notification.model';
import { StompClientService } from './stomp-client.service';

@Injectable({ providedIn: 'root' })
export class UserNotificationService {
  readonly items = signal<NotificationItem[]>([]);
  readonly unreadCount = signal(0);
  readonly loading = signal(false);
  readonly page = signal(0);
  readonly hasMore = signal(true);
  readonly latest = computed(() => this.items().slice(0, 8));
  readonly grouped = computed(() => this.groupByDay(this.items()));

  constructor(
    private readonly stomp: StompClientService,
    private readonly api: NotificationApiService
  ) {
    this.bootstrap();
  }

  bootstrap(): void {
    this.refreshUnreadCount();
    this.loadFirstPage();
    this.stomp
      .subscribe<NotificationItem>('/user/queue/notifications')
      .pipe(catchError(() => of(null)))
      .subscribe((incoming) => {
        if (!incoming || !incoming.eventId) {
          return;
        }
        const exists = this.items().some((item) => item.eventId === incoming.eventId);
        if (exists) {
          return;
        }
        const normalized: NotificationItem = {
          ...incoming,
          read: false,
          archived: false
        };
        this.items.update((current) => [normalized, ...current]);
        this.unreadCount.update((count) => count + 1);
      });
  }

  loadFirstPage(): void {
    this.page.set(0);
    this.hasMore.set(true);
    this.loading.set(true);
    this.api.list(0, 20).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.hasMore.set(!result.last);
      },
      error: () => {
        this.items.set([]);
        this.hasMore.set(false);
      }
    });
  }

  loadNextPage(): void {
    if (this.loading() || !this.hasMore()) {
      return;
    }
    const nextPage = this.page() + 1;
    this.loading.set(true);
    this.api.list(nextPage, 20).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (result) => {
        this.page.set(nextPage);
        this.hasMore.set(!result.last);
        this.items.update((current) => {
          const merged = [...current, ...result.content];
          const seen = new Set<string>();
          return merged.filter((item) => {
            const key = item.eventId || `id:${item.id}`;
            if (seen.has(key)) {
              return false;
            }
            seen.add(key);
            return true;
          });
        });
      },
      error: () => {
        this.hasMore.set(false);
      }
    });
  }

  refreshUnreadCount(): void {
    this.api.unreadCount().subscribe({
      next: (result) => this.unreadCount.set(result.unreadCount),
      error: () => this.unreadCount.set(0)
    });
  }

  markAsRead(id: number): void {
    this.items.update((current) => current.map((n) => (n.id === id ? { ...n, read: true } : n)));
    this.unreadCount.update((count) => Math.max(0, count - 1));
    this.api.markRead(id).subscribe({
      error: () => this.refreshUnreadCount()
    });
  }

  markAllRead(): void {
    this.items.update((current) => current.map((n) => ({ ...n, read: true })));
    this.unreadCount.set(0);
    this.api.markAllRead().subscribe({
      error: () => this.refreshUnreadCount()
    });
  }

  remove(id: number): void {
    this.items.update((current) => current.filter((n) => n.id !== id));
    this.api.remove(id).subscribe({
      error: () => this.loadFirstPage()
    });
  }

  private groupByDay(items: NotificationItem[]): Array<{ label: string; items: NotificationItem[] }> {
    const groups = new Map<string, NotificationItem[]>();
    for (const item of items) {
      const label = this.dayLabel(item.createdAt);
      const bucket = groups.get(label) ?? [];
      bucket.push(item);
      groups.set(label, bucket);
    }
    return Array.from(groups.entries()).map(([label, groupItems]) => ({ label, items: groupItems }));
  }

  private dayLabel(value: string): string {
    const date = new Date(value);
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    if (isToday) {
      return 'Today';
    }
    return date.toLocaleDateString();
  }
}

