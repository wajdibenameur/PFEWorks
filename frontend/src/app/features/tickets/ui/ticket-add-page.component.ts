import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { TicketPriority } from '../../../core/models/ticket.model';
import { TicketUser } from '../../../core/models/ticket-user.model';
import { TicketCreatePayload, TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-add-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ticket-add-page.component.html',
  styleUrl: './ticket-add-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketAddPageComponent {
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly users = signal<TicketUser[]>([]);

  readonly title = signal('');
  readonly description = signal('');
  readonly priority = signal<TicketPriority>('MEDIUM');
  readonly creatorId = signal<number | null>(null);
  readonly monitoringSource = signal('');
  readonly externalProblemId = signal('');
  readonly resourceRef = signal('');
  readonly hostId = signal<string>('');
  readonly externalProblem = signal(false);

  readonly priorityOptions: Array<TicketPriority> = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly sourceOptions = ['ZABBIX', 'OBSERVIUM', 'ZKBIO', 'CAMERA'];

  constructor(
    private readonly api: TicketManagerApiService,
    private readonly router: Router
  ) {
    this.loadUsers();
  }

  submit(): void {
    if (!this.title().trim() || !this.description().trim() || !this.creatorId()) {
      this.errorMessage.set('Title, description, and creator are required.');
      return;
    }

    const hostId = this.hostId().trim();
    const payload: TicketCreatePayload = {
      title: this.title().trim(),
      description: this.description().trim(),
      priority: this.priority(),
      creatorId: this.creatorId()!,
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

  parseUserId(value: string | number | null): void {
    this.creatorId.set(value == null || value === '' ? null : Number(value));
  }

  private loadUsers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      users: this.api.getAssignableUsers().pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load ticket users.'));
          return of<TicketUser[]>([]);
        })
      )
    }).subscribe(({ users }) => {
      this.users.set(users);
      if (users.length && !this.creatorId()) {
        this.creatorId.set(users[0].id);
      }
      this.isLoading.set(false);
    });
  }
}
