import { HttpClient, HttpParams } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { PageResponse } from '../../../core/models/page-response.model';
import { Ticket, TicketPriority, TicketStatus } from '../../../core/models/ticket.model';
import { TicketUser } from '../../../core/models/ticket-user.model';

export interface TicketCreatePayload {
  title: string;
  description: string;
  priority: TicketPriority;
  creatorId: number;
  hostId?: number | null;
  monitoringSource?: string | null;
  externalProblemId?: string | null;
  resourceRef?: string | null;
  externalProblem?: boolean;
}

export interface TicketFilters {
  status?: TicketStatus | '';
  priority?: TicketPriority | '';
  source?: string;
  page?: number;
  size?: number;
}

export interface TicketStatusUpdatePayload {
  status: TicketStatus;
  resolution?: string | null;
}

export interface TicketAssignmentPayload {
  userId: number;
}

export interface TicketInterventionPayload {
  userId: number;
  action: string;
  comment: string;
  result?: string | null;
}

export interface TicketDecisionPayload {
  adminId: number;
  reason?: string | null;
}

@Injectable({ providedIn: 'root' })
export class TicketManagerApiService {
  private readonly ticketsBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.ticketsBaseUrl = `${config.apiBaseUrl}/api/tickets`;
  }

  getTickets(filters: TicketFilters = {}): Observable<PageResponse<Ticket>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));

    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.priority) {
      params = params.set('priority', filters.priority);
    }
    if (filters.source?.trim()) {
      params = params.set('source', filters.source.trim().toUpperCase());
    }

    return this.http.get<PageResponse<Ticket>>(this.ticketsBaseUrl, { params });
  }

  getTicket(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.ticketsBaseUrl}/${id}`);
  }

  createTicket(payload: TicketCreatePayload): Observable<Ticket> {
    return this.http.post<Ticket>(this.ticketsBaseUrl, payload);
  }

  getAssignableUsers(): Observable<TicketUser[]> {
    return this.http.get<TicketUser[]>(`${this.ticketsBaseUrl}/users`);
  }

  assignTicket(ticketId: number, payload: TicketAssignmentPayload): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.ticketsBaseUrl}/${ticketId}/assign`, payload);
  }

  updateStatus(ticketId: number, payload: TicketStatusUpdatePayload): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.ticketsBaseUrl}/${ticketId}/status`, payload);
  }

  addIntervention(ticketId: number, payload: TicketInterventionPayload): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.ticketsBaseUrl}/${ticketId}/interventions`, payload);
  }

  validateTicket(ticketId: number, payload: TicketDecisionPayload): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.ticketsBaseUrl}/${ticketId}/validate`, payload);
  }

  rejectTicket(ticketId: number, payload: TicketDecisionPayload): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.ticketsBaseUrl}/${ticketId}/reject`, payload);
  }
}
