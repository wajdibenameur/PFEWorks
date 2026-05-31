import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { TicketPriority } from '../../../core/models/ticket.model';
import { TicketCreatePayload, TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-add-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ticket-add-page.component.html',
  styleUrls: ['./ticket-add-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketAddPageComponent {
  private readonly auth = inject(AUTH_CONTEXT);

  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly title = signal('');
  readonly description = signal('');
  readonly priority = signal<TicketPriority>('MEDIUM');
  readonly monitoringSource = signal('');
  readonly externalProblemId = signal('');
  readonly resourceRef = signal('');
  readonly hostId = signal<string>('');
  readonly externalProblem = signal(false);

  readonly priorityOptions: Array<TicketPriority> = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly sourceOptions = ['ZABBIX', 'OBSERVIUM', 'ZKBIO', 'CAMERA'];
  readonly canCreateTicket = computed(() => this.auth.arePermissionsLoaded() && this.auth.hasPermission('CREATE_TICKET'));

  constructor(
    private readonly api: TicketManagerApiService,
    private readonly router: Router
  ) {}

  submit(): void {
    if (!this.canCreateTicket()) {
      this.errorMessage.set('Cette action requiert CREATE_TICKET.');
      return;
    }

    if (!this.title().trim() || !this.description().trim()) {
      this.errorMessage.set('Title and description are required.');
      return;
    }

    const hostId = this.hostId().trim();
    const payload: TicketCreatePayload = {
      title: this.title().trim(),
      description: this.description().trim(),
      priority: this.priority(),
      monitoringSource: this.monitoringSource().trim() || null,
      externalProblemId: this.externalProblemId().trim() || null,
      resourceRef: this.resourceRef().trim() || null,
      hostId: hostId ? Number(hostId) : null,
      externalProblem: this.externalProblem()
    };

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    this.api.createTicket(payload).subscribe({
      next: (ticket) => {
        this.isSubmitting.set(false);
        this.router.navigate(['/tickets/tracking'], { queryParams: { id: ticket.id } });
      },
      error: (error) => {
        this.isSubmitting.set(false);
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to create ticket.'));
      }
    });
  }
}


