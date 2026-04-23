import { TicketIntervention } from './ticket-intervention.model';
import { TicketUser } from './ticket-user.model';

export type TicketStatus =
  | 'OPEN'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'CLOSED'
  | 'VALIDATED'
  | 'REJECTED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Ticket {
  id: number;
  title: string | null;
  hostId: number | null;
  description: string | null;
  creationDate: string | null;
  status: TicketStatus | null;
  priority: TicketPriority | null;
  externalProblem: boolean | null;
  monitoringSource: string | null;
  externalProblemId: string | null;
  resourceRef: string | null;
  resolution: string | null;
  archived: boolean | null;
  createdBy: TicketUser | null;
  assignedTo: TicketUser | null;
  validatedBy: TicketUser | null;
  interventions: TicketIntervention[];
}
