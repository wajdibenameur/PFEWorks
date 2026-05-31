import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { AdminApiService } from '../data/admin-api.service';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { UserManagementPageComponent } from '../../users/ui/user-management-page.component';

@Component({
  selector: 'app-admin-panel-page',
  imports: [CommonModule, UserManagementPageComponent],
  templateUrl: './admin-panel-page.component.html',
  styleUrls: ['./admin-panel-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminPanelPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly auth = inject(AUTH_CONTEXT);

  readonly sourceRows = signal<SourceAvailability[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly canManageUsers = computed(() => this.auth.hasPermission('MANAGE_USERS'));
  readonly canManagePermissions = computed(() => this.auth.hasPermission('MANAGE_PERMISSIONS'));
  readonly connectedCount = computed(() => this.sourceRows().filter((row) => row.available).length);
  readonly degradedCount = computed(() => this.sourceRows().filter((row) => row.status === 'DEGRADED').length);
  readonly unavailableCount = computed(() => this.sourceRows().filter((row) => row.status === 'UNAVAILABLE').length);

  ngOnInit(): void {
    this.loadSourceHealth();
  }

  protected syncLabel(row: SourceAvailability): string {
    return row.timestamp ?? row.lastFailureAt ?? 'No timestamp';
  }

  private loadSourceHealth(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminApi.getSourceHealth().subscribe({
      next: (rows) => {
        this.sourceRows.set(rows);
        this.isLoading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load source health.'));
        this.isLoading.set(false);
      }
    });
  }
}


