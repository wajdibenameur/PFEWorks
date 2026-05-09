import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { catchError, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { Ticket, TicketPriority, TicketStatus } from '../../../core/models/ticket.model';
import { TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-list-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ticket-list-page.component.html',
  styleUrls: ['./ticket-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketListPageComponent {
  private readonly auth = inject(AUTH_CONTEXT);

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly tickets = signal<Ticket[]>([]);
  readonly totalElements = signal(0);
  readonly statusFilter = signal<TicketStatus | ''>('');
  readonly priorityFilter = signal<TicketPriority | ''>('');
  readonly sourceFilter = signal('');

  readonly statusOptions: Array<TicketStatus> = [
    'OPEN',
    'IN_PROGRESS',
    'RESOLVED',
    'VALIDATED',
    'REJECTED',
    'CLOSED'
  ];

  readonly priorityOptions: Array<TicketPriority> = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly sourceOptions = ['ZABBIX', 'OBSERVIUM', 'ZKBIO', 'CAMERA'];
  readonly canCreateTicket = computed(() =>
    this.auth.arePermissionsLoaded()
      ? this.auth.hasPermission('CREATE_TICKET')
      : this.auth.hasRole('SUPERADMIN') || this.auth.hasRole('ADMIN') || this.auth.hasRole('SUPPORT')
  );

  constructor(
    private readonly api: TicketManagerApiService,
    private readonly router: Router
  ) {
    this.loadTickets();
  }

  refresh(): void {
    this.loadTickets();
  }

  openTicket(ticket: Ticket): void {
    this.router.navigate(['/tickets/tracking'], { queryParams: { id: ticket.id } });
  }

  private loadTickets(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.api
      .getTickets({
        status: this.statusFilter(),
        priority: this.priorityFilter(),
        source: this.sourceFilter()
      })
      .pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load tickets.'));
          return of({
            content: [] as Ticket[],
            totalElements: 0,
            totalPages: 0,
            size: 20,
            number: 0,
            first: true,
            last: true,
            empty: true
          });
        })
      )
      .subscribe((page) => {
        this.tickets.set(page.content);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      });
  }
}


