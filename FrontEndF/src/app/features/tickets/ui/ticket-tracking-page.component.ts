import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { Ticket, TicketPriority, TicketStatus } from '../../../core/models/ticket.model';
import { TicketUser } from '../../../core/models/ticket-user.model';
import { TicketCreatePayload, TicketManagerApiService } from '../data/ticket-manager-api.service';

@Component({
  selector: 'app-ticket-tracking-page',
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-tracking-page.component.html',
  styleUrls: ['./ticket-tracking-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketTrackingPageComponent {
  private readonly auth = inject(AUTH_CONTEXT);

  readonly isLoading = signal(false);
  readonly isMutating = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly tickets = signal<Ticket[]>([]);
  readonly users = signal<TicketUser[]>([]);
  readonly selectedTicket = signal<Ticket | null>(null);
  readonly createFormOpen = signal(false);
  readonly showArchivedOnly = signal(false);

  readonly statusDraft = signal<TicketStatus>('OPEN');
  readonly resolutionDraft = signal('');
  readonly assignUserId = signal<number | null>(null);
  readonly interventionAction = signal('COMMENT');
  readonly interventionComment = signal('');
  readonly interventionResult = signal('');
  readonly rejectReason = signal('');
  readonly createTitle = signal('');
  readonly createDescription = signal('');
  readonly createPriority = signal<TicketPriority>('MEDIUM');
  readonly createMonitoringSource = signal('');
  readonly createExternalProblemId = signal('');
  readonly createResourceRef = signal('');
  readonly createHostId = signal('');
  readonly createExternalProblem = signal(false);
  readonly createAssignUserId = signal<number | null>(null);

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

  readonly hasSelection = computed(() => this.selectedTicket() !== null);
  readonly displayedTickets = computed(() =>
    this.tickets().filter((ticket) => {
      const archiveMatch = this.showArchivedOnly() ? Boolean(ticket.archived) : !Boolean(ticket.archived);
      if (!archiveMatch) {
        return false;
      }
      if (this.isSupport() && ticket.status === 'RESOLVED') {
        return false;
      }
      return true;
    })
  );
  readonly canCreateTicket = computed(() => this.auth.arePermissionsLoaded() && this.auth.hasPermission('CREATE_TICKET'));
  readonly canEditTicket = computed(() => this.auth.arePermissionsLoaded() && this.auth.hasPermission('EDIT_TICKET'));
  readonly canAssignTicket = computed(
    () =>
      this.auth.arePermissionsLoaded() &&
      this.auth.hasPermission('ASSIGN_TICKET') &&
      this.auth.hasPermission('VIEW_USERS')
  );
  readonly canValidateTicket = computed(() => this.auth.arePermissionsLoaded() && this.auth.hasPermission('VALIDATE_TICKET'));
  readonly canAddComment = computed(() => this.auth.arePermissionsLoaded() && this.auth.hasPermission('ADD_COMMENT'));
  readonly isSupport = computed(() => this.auth.hasRole('SUPPORT'));
  readonly isArchivedSelection = computed(() => Boolean(this.selectedTicket()?.archived));
  readonly canSendForVerification = computed(() => {
    const ticket = this.selectedTicket();
    if (!ticket) {
      return false;
    }
    return ticket.interventions?.some((it) => (it.comment ?? '').trim().length > 0) ?? false;
  });
  readonly visibleStatusOptions = computed(() => {
    const base = this.statusOptions.filter((status) => status !== 'VALIDATED' && status !== 'REJECTED');
    return this.isSupport() ? base.filter((status) => status !== 'CLOSED') : base;
  });

  constructor(
    private readonly api: TicketManagerApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.route.queryParamMap.subscribe((params) => {
      const id = Number(params.get('id'));
      const archivedParam = params.get('archived');
      this.showArchivedOnly.set(archivedParam === '1' || archivedParam === 'true');
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
    this.successMessage.set(null);
    this.loadPage(current?.id ?? null);
  }

  toggleCreateForm(): void {
    this.createFormOpen.update((open) => !open);
    if (!this.createFormOpen()) {
      this.resetCreateForm();
    }
  }

  toggleArchivedFilter(): void {
    this.showArchivedOnly.update((value) => !value);
    const visible = this.displayedTickets();
    const current = this.selectedTicket();
    if (!visible.length) {
      this.selectedTicket.set(null);
      return;
    }
    if (!current || !visible.some((ticket) => ticket.id === current.id)) {
      this.applyTicketSelection(visible[0]);
    }
  }

  submitCreate(): void {
    if (!this.canCreateTicket()) {
      this.errorMessage.set('Cette action requiert la permission CREATE_TICKET.');
      return;
    }

    if (!this.createTitle().trim() || !this.createDescription().trim()) {
      this.errorMessage.set('Le titre et la description sont obligatoires.');
      return;
    }

    const hostId = this.createHostId().trim();
    const payload: TicketCreatePayload = {
      title: this.createTitle().trim(),
      description: this.createDescription().trim(),
      priority: this.createPriority(),
      monitoringSource: this.createMonitoringSource().trim() || null,
      externalProblemId: this.createExternalProblemId().trim() || null,
      resourceRef: this.createResourceRef().trim() || null,
      hostId: hostId ? Number(hostId) : null,
      externalProblem: this.createExternalProblem()
    };

    this.isMutating.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.api.createTicket(payload).subscribe({
      next: (ticket) => {
        const assigneeId = this.createAssignUserId();
        if (assigneeId) {
          this.api.assignTicket(ticket.id, { userId: assigneeId }).subscribe({
            next: (assignedTicket) => {
              this.isMutating.set(false);
              this.createFormOpen.set(false);
              this.resetCreateForm();
              this.loadPage(assignedTicket.id);
            },
            error: (error) => {
              this.isMutating.set(false);
              this.errorMessage.set(extractApiErrorMessage(error, 'Ticket cree, mais assignation impossible.'));
              this.createFormOpen.set(false);
              this.resetCreateForm();
              this.loadPage(ticket.id);
            }
          });
          return;
        }

        this.isMutating.set(false);
        this.createFormOpen.set(false);
        this.resetCreateForm();
        this.loadPage(ticket.id);
      },
      error: (error) => {
        this.isMutating.set(false);
        this.errorMessage.set(extractApiErrorMessage(error, 'Creation du ticket impossible.'));
      }
    });
  }

  clearSelection(): void {
    this.selectedTicket.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { id: null },
      queryParamsHandling: 'merge'
    });
  }

  updateStatus(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.canEditTicket()) {
      return;
    }

    this.runMutation(
      this.api.updateStatus(ticket.id, {
        status: this.statusDraft(),
        resolution: this.resolutionDraft().trim() || null
      }),
      () => this.returnToListWithSuccess('Ticket mis a jour avec succes.')
    );
  }

  assign(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.assignUserId() || !this.canAssignTicket()) {
      return;
    }

    this.runMutation(
      this.api.assignTicket(ticket.id, { userId: this.assignUserId()! }),
      () => this.returnToListWithSuccess('Ticket assigne avec succes.')
    );
  }

  addIntervention(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.interventionComment().trim() || !this.canAddComment()) {
      return;
    }

    this.runMutation(
      this.api.addIntervention(ticket.id, {
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
    if (!ticket || !this.canValidateTicket()) {
      return;
    }

    if (this.isSupport()) {
      if (!this.canSendForVerification()) {
        this.errorMessage.set('Ajoutez au moins une intervention avec un commentaire avant envoi pour verification.');
        return;
      }
      // Support marks ticket as treated and sends it for admin/superadmin verification.
      this.runMutation(
        this.api.updateStatus(ticket.id, {
          status: 'RESOLVED',
          resolution: this.resolutionDraft().trim() || 'Traite par support, en attente de verification.'
        }),
        () => {
          this.router.navigate(['/tickets/list']);
        }
      );
      return;
    }

    this.runMutation(this.api.validateTicket(ticket.id, {}), () => {
      this.router.navigate(['/tickets/list']);
    });
  }

  rejectTicket(): void {
    const ticket = this.selectedTicket();
    if (!ticket || !this.rejectReason().trim() || !this.canValidateTicket()) {
      return;
    }

    this.runMutation(
      this.api.rejectTicket(ticket.id, {
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

  parseCreateAssignUser(value: string | number | null): void {
    this.createAssignUserId.set(value == null || value === '' ? null : Number(value));
  }

  private loadPage(selectedId: number | null): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      ticketsPage: this.api.getTickets({ size: 50, archived: this.showArchivedOnly() ? 'archived' : 'active' }).pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Impossible de charger les tickets.'));
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
      users: (this.canAssignTicket() ? this.api.getAssignableUsers() : of<TicketUser[]>([])).pipe(
        catchError((error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Impossible de charger les utilisateurs assignables.'));
          return of<TicketUser[]>([]);
        })
      )
    }).subscribe(({ ticketsPage, users }) => {
      this.tickets.set(ticketsPage.content);
      this.users.set(users);

      const visibleTickets = ticketsPage.content.filter((ticket) =>
        this.showArchivedOnly() ? Boolean(ticket.archived) : !Boolean(ticket.archived)
      );
      if (selectedId != null) {
        const selected = visibleTickets.find((ticket) => ticket.id === selectedId) ?? null;
        if (!selected) {
          // Ticket may exist in the opposite archive view; still load detail by id for direct access.
          this.api.getTicket(selectedId).pipe(
            catchError((error) => {
              this.errorMessage.set(extractApiErrorMessage(error, 'Impossible de charger le detail du ticket.'));
              this.selectedTicket.set(null);
              this.isLoading.set(false);
              return of(null);
            })
          ).subscribe((detailedTicket) => {
            if (detailedTicket) {
              this.patchTicket(detailedTicket);
            }
            this.isLoading.set(false);
          });
          return;
        }

        this.api.getTicket(selectedId).pipe(
          catchError((error) => {
            // Fallback to paged row if detail endpoint fails.
            this.errorMessage.set(extractApiErrorMessage(error, 'Impossible de charger le detail du ticket.'));
            return of(selected);
          })
        ).subscribe((detailedTicket) => {
          this.patchTicket(detailedTicket);
          this.isLoading.set(false);
        });
        return;
      }

      this.selectedTicket.set(null);
      this.isLoading.set(false);
    });
  }

  private runMutation(request$: ReturnType<TicketManagerApiService['getTicket']>, onSuccess?: () => void): void {
    this.isMutating.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

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

  private resetCreateForm(): void {
    this.createTitle.set('');
    this.createDescription.set('');
    this.createPriority.set('MEDIUM');
    this.createMonitoringSource.set('');
    this.createExternalProblemId.set('');
    this.createResourceRef.set('');
    this.createHostId.set('');
    this.createExternalProblem.set(false);
    this.createAssignUserId.set(null);
  }

  private returnToListWithSuccess(message: string): void {
    this.successMessage.set(message);
    this.clearSelection();
    this.loadPage(null);
  }
}


