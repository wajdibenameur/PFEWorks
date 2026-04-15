import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AUTH_CONTEXT } from '../../core/auth/auth-context.port';
import { RealtimeConnectionStore } from '../../core/realtime/realtime-connection.store';

@Component({
  selector: 'app-user-panel',
  imports: [CommonModule],
  templateUrl: './user-panel.component.html',
  styleUrl: './user-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserPanelComponent {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly realtime = inject(RealtimeConnectionStore);

  readonly fallbackUser = {
    username: 'Guest Operator',
    role: 'Viewer',
    status: 'Not Authenticated'
  };

  readonly connectionStatus = computed(() => this.realtime.status());
  readonly statusClass = computed(() => `status-${this.connectionStatus().toLowerCase()}`);
}
