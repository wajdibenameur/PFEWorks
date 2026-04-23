import { TicketUser } from './ticket-user.model';

export interface TicketIntervention {
  id: number;
  action: string | null;
  comment: string | null;
  result: string | null;
  timestamp: string | null;
  performedBy: TicketUser | null;
}
