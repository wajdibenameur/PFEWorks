import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { UserNotificationService } from '../../../core/realtime/user-notification.service';

@Component({
  selector: 'app-notification-center-page',
  imports: [CommonModule, RouterModule],
  templateUrl: './notification-center-page.component.html',
  styleUrls: ['./notification-center-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationCenterPageComponent {
  private readonly notifications = inject(UserNotificationService);

  readonly groups = computed(() => this.notifications.grouped());
  readonly unreadCount = computed(() => this.notifications.unreadCount());
  readonly loading = computed(() => this.notifications.loading());
  readonly hasMore = computed(() => this.notifications.hasMore());

  constructor() {
    this.notifications.loadFirstPage();
    this.notifications.refreshUnreadCount();
  }

  protected markRead(id: number): void {
    this.notifications.markAsRead(id);
  }

  protected remove(id: number): void {
    this.notifications.remove(id);
  }

  protected markAllRead(): void {
    this.notifications.markAllRead();
  }

  protected problemBadge(eventType: string): string | null {
    if (eventType?.includes('NEW_MONITORING_PROBLEM')) {
      return 'NEW';
    }
    if (eventType?.includes('RECENT_MONITORING_PROBLEM')) {
      return 'RECENT';
    }
    return null;
  }

  protected problemBadgeClass(eventType: string): string {
    return eventType?.includes('RECENT_MONITORING_PROBLEM') ? 'badge-recent' : 'badge-new';
  }

  @HostListener('window:scroll')
  protected onWindowScroll(): void {
    const nearBottom = window.innerHeight + window.scrollY >= document.body.offsetHeight - 240;
    if (nearBottom) {
      this.notifications.loadNextPage();
    }
  }
}
