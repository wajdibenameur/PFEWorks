import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { Ticket, TicketPriority, TicketStatus } from '../../../core/models/ticket.model';
import { TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-list-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ticket-list-page.component.html',
  styleUrl: './ticket-list-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketListPageComponent {
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
