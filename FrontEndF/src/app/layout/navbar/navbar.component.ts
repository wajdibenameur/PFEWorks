import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterModule } from '@angular/router';
import { AUTH_CONTEXT } from '../../core/auth/auth-context.port';
import { RealtimeConnectionStore } from '../../core/realtime/realtime-connection.store';
import { StompClientService } from '../../core/realtime/stomp-client.service';

@Component({
  selector: 'app-navbar',
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NavbarComponent {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly router = inject(Router);
  private readonly realtime = inject(RealtimeConnectionStore);
  private readonly stompClient = inject(StompClientService);

  readonly toggleSidebar = output<void>();
  readonly profileMenuOpen = signal(false);
  readonly user = toSignal(this.auth.user$, { initialValue: null });
  readonly connectionStatus = computed(() => this.realtime.status());
  readonly connectionLabel = computed(() => this.connectionStatus().toLowerCase());
  readonly displayName = computed(() => this.user()?.username ?? 'guest.operator');
  readonly primaryRole = computed(() => this.user()?.roles[0] ?? 'VIEWER');
  readonly initials = computed(() =>
    this.displayName()
      .split(/[.\s_-]+/)
      .filter(Boolean)
      .map((part: string) => part.charAt(0))
      .join('')
      .slice(0, 2)
      .toUpperCase()
  );

  protected toggleProfileMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.profileMenuOpen.update((open) => !open);
  }

  protected goToProfile(): void {
    this.profileMenuOpen.set(false);
    void this.router.navigate(['/profile']);
  }

  protected logout(): void {
    this.profileMenuOpen.set(false);
    this.stompClient.disconnect();
    this.auth.logout();
    void this.router.navigate(['/login']);
  }

  @HostListener('document:click')
  protected closeProfileMenu(): void {
    this.profileMenuOpen.set(false);
  }
}


