import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { Ticket, TicketPriority, TicketStatus } from '../../../core/models/ticket.model';
import { TicketUser } from '../../../core/models/ticket-user.model';
import { TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-tracking-page',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './ticket-tracking-page.component.html',
  styleUrl: './ticket-tracking-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketTrackingPageComponent {
  readonly isLoading = signal(false);
  readonly isMutating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly tickets = signal<Ticket[]>([]);
  readonly users = signal<TicketUser[]>([]);
  readonly selectedTicket = signal<Ticket | null>(null);

  readonly statusDraft = signal<TicketStatus>('OPEN');
  readonly resolutionDraft = signal('');
  readonly assignUserId = signal<number | null>(null);
  readonly interventionUserId = signal<number | null>(null);
  readonly interventionAction = signal('COMMENT');
  readonly interventionComment = signal('');
  readonly interventionResult = signal('');
  readonly adminId = signal<number | null>(null);
  readonly rejectReason = signal('');

  readonly statusOptions: Array<TicketStatus> = [
    'OPEN',
    'IN_PROGRESS',
    'RESOLVED',
    'VALIDATED',
    'REJECTED',
    'CLOSED'
  ];

  readonly hasSelection = computed(() => this.selectedTicket() !== null);

  constructor(
    private readonly api: TicketManagerApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.route.queryParamMap.subscribe((params) => {
      const id = Number(params.get('id'));
      this.loadPage(Number.isFinite(id) && id > 0 ? id : null);
    });
  }

  selectTicket(ticket: Ticket): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: ticket.id },
      queryParamsHandling: 'merge'
    });
  }

  refresh(): void {
    const current = this.selectedTicket();
    this.loadPage(current?.id ?? null);
  }

  updateStatus(): void {
    const ticket = this.selectedTicket();
    if (!ticket) {
      return;
    }

    this.runMutation(
      this.api.updateStatus(ticket.id, {
        status: this.statusDraft(),
        resolution: this.resolutionDraft().trim() || null
      })
    );
  }

  assign(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.assignUserId()) {
      return;
    }

    this.runMutation(this.api.assignTicket(ticket.id, { userId: this.assignUserId()! }));
  }

  addIntervention(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.interventionUserId() || !this.interventionComment().trim()) {
      return;
    }

    this.runMutation(
      this.api.addIntervention(ticket.id, {
        userId: this.interventionUserId()!,
        action: this.interventionAction().trim() || 'COMMENT',
        comment: this.interventionComment().trim(),
        result: this.interventionResult().trim() || null
      }),
      () => {
        this.interventionComment.set('');
        this.interventionResult.set('');
      }
    );
  }

  validateTicket(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.adminId()) {
      return;
    }

    this.runMutation(this.api.validateTicket(ticket.id, { adminId: this.adminId()! }));
  }

  rejectTicket(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.adminId() || !this.rejectReason().trim()) {
      return;
    }

    this.runMutation(
      this.api.rejectTicket(ticket.id, {
        adminId: this.adminId()!,
        reason: this.rejectReason().trim()
      }),
      () => this.rejectReason.set('')
    );
  }

  statusBadge(ticket: Ticket | null): string {
    return ticket?.status ?? 'UNKNOWN';
  }

  priorityLabel(ticket: Ticket | null): TicketPriority | 'N/A' {
    return ticket?.priority ?? 'N/A';
  }

  parseAssignUser(value: string | number | null): void {
    this.assignUserId.set(value == null || value === '' ? null : Number(value));
  }

  parseAdminUser(value: string | number | null): void {
    this.adminId.set(value == null || value === '' ? null : Number(value));
  }

  parseInterventionUser(value: string | number | null): void {
    this.interventionUserId.set(value == null || value === '' ? null : Number(value));
  }

  private loadPage(selectedId: number | null): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      ticketsPage: this.api.getTickets({ size: 50 }).pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load tickets for tracking.'));
          return of({
            content: [] as Ticket[],
            totalElements: 0,
            totalPages: 0,
            size: 50,
            number: 0,
            first: true,
            last: true,
            empty: true
          });
        })
      ),
      users: this.api.getAssignableUsers().pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load ticket users.'));
          return of<TicketUser[]>([]);
        })
      )
    }).subscribe(({ ticketsPage, users }) => {
      this.tickets.set(ticketsPage.content);
      this.users.set(users);

      const selected =
        ticketsPage.content.find((ticket) => ticket.id === selectedId) ?? ticketsPage.content[0] ?? null;

      if (selected) {
        this.applyTicketSelection(selected);
      } else {
        this.selectedTicket.set(null);
      }

      if (users.length) {
        this.interventionUserId.set(this.interventionUserId() ?? users[0].id);
        this.adminId.set(this.adminId() ?? users[0].id);
      }

      this.isLoading.set(false);
    });
  }

  private runMutation(request$: ReturnType<TicketManagerApiService['getTicket']>, onSuccess?: () => void): void {
    this.isMutating.set(true);
    this.errorMessage.set(null);

    request$.subscribe({
      next: (ticket) => {
        this.isMutating.set(false);
        this.patchTicket(ticket);
        onSuccess?.();
      },
      error: (error) => {
        this.isMutating.set(false);
        this.errorMessage.set(extractApiErrorMessage(error, 'Ticket operation failed.'));
      }
    });
  }

  private patchTicket(ticket: Ticket): void {
    this.tickets.update((current) =>
      current.map((entry) => (entry.id === ticket.id ? ticket : entry))
    );
    this.applyTicketSelection(ticket);
  }

  private applyTicketSelection(ticket: Ticket): void {
    this.selectedTicket.set(ticket);
    this.statusDraft.set(ticket.status ?? 'OPEN');
    this.resolutionDraft.set(ticket.resolution ?? '');
    this.assignUserId.set(ticket.assignedTo?.id ?? null);
  }
}
