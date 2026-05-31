import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AUTH_CONTEXT } from '../../core/auth/auth-context.port';
import { RealtimeConnectionStore } from '../../core/realtime/realtime-connection.store';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { StompClientService } from '../../core/realtime/stomp-client.service';

@Component({
  selector: 'app-user-panel',
  imports: [CommonModule],
  templateUrl: './user-panel.component.html',
  styleUrls: ['./user-panel.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserPanelComponent {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly realtime = inject(RealtimeConnectionStore);
  private readonly router = inject(Router);
  private readonly stompClient = inject(StompClientService);

  readonly fallbackUser = {
    username: 'Guest Operator',
    role: 'Viewer',
    status: 'Not Authenticated'
  };

  readonly currentUser = toSignal(this.auth.user$, { initialValue: null });
  readonly connectionStatus = computed(() => this.realtime.status());
  readonly statusClass = computed(() => `status-${this.connectionStatus().toLowerCase()}`);
  readonly displayUser = computed(() => this.currentUser() ?? this.fallbackUser);
  readonly displayRole = computed(() => this.currentUser()?.roles?.[0] ?? this.fallbackUser.role);
  readonly authStatus = computed(() => (this.currentUser() ? 'Authenticated' : this.fallbackUser.status));

  logout(): void {
    this.stompClient.disconnect();
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}


