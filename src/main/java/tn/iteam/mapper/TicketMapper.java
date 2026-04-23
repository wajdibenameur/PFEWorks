package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.Intervention;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.TicketInterventionDTO;
import tn.iteam.dto.TicketResponseDTO;
import tn.iteam.dto.TicketUserDTO;

import java.util.List;

@Component
public class TicketMapper {

    public TicketResponseDTO toResponse(Ticket ticket) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .hostId(ticket.getHostId())
                .description(ticket.getDescription())
                .creationDate(ticket.getCreationDate())
                .status(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                .priority(ticket.getPriority() != null ? ticket.getPriority().name() : null)
                .externalProblem(ticket.getExternalProblem())
                .monitoringSource(ticket.getMonitoringSource())
                .externalProblemId(ticket.getExternalProblemId())
                .resourceRef(ticket.getResourceRef())
                .resolution(ticket.getResolution())
                .archived(ticket.getArchived())
                .createdBy(toUser(ticket.getCreatedBy()))
                .assignedTo(toUser(ticket.getAssignedTo()))
                .validatedBy(toUser(ticket.getValidatedBy()))
                .interventions(ticket.getInterventions() == null
                        ? List.of()
                        : ticket.getInterventions().stream().map(this::toIntervention).toList())
                .build();
    }

    public TicketUserDTO toUser(User user) {
        if (user == null) {
            return null;
        }

        return TicketUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null && user.getRole().getName() != null ? user.getRole().getName().name() : null)
                .build();
    }

    public TicketInterventionDTO toIntervention(Intervention intervention) {
        return TicketInterventionDTO.builder()
                .id(intervention.getId())
                .action(intervention.getAction())
                .comment(intervention.getComment())
                .result(intervention.getResult())
                .timestamp(intervention.getTimestamp())
                .performedBy(toUser(intervention.getPerformedBy()))
                .build();
    }
}
