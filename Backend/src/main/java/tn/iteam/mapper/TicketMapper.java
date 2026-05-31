package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import org.hibernate.Hibernate;
import jakarta.persistence.EntityNotFoundException;
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
                .statusChangedAt(ticket.getStatusChangedAt())
                .resolvedAt(ticket.getResolvedAt())
                .validatedAt(ticket.getValidatedAt())
                .priority(ticket.getPriority() != null ? ticket.getPriority().name() : null)
                .externalProblem(ticket.getExternalProblem())
                .monitoringSource(ticket.getMonitoringSource())
                .externalProblemId(ticket.getExternalProblemId())
                .resourceRef(ticket.getResourceRef())
                .resolution(ticket.getResolution())
                .archived(ticket.getArchived())
                .archivedAt(ticket.getArchivedAt())
                .createdBy(toUser(ticket.getCreatedBy()))
                .assignedTo(toUser(ticket.getAssignedTo()))
                .validatedBy(toUser(ticket.getValidatedBy()))
                .interventions(ticket.getInterventions() == null || !Hibernate.isInitialized(ticket.getInterventions())
                        ? List.of()
                        : ticket.getInterventions().stream().map(this::toIntervention).toList())
                .build();
    }

    public TicketUserDTO toUser(User user) {
        if (user == null) {
            return null;
        }

        try {
            return TicketUserDTO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole() != null && user.getRole().getName() != null ? user.getRole().getName().name() : null)
                    .build();
        } catch (EntityNotFoundException ex) {
            // Defensive fallback for orphaned FK references (deleted/missing users).
            return TicketUserDTO.builder()
                    .id(safeUserId(user))
                    .username(null)
                    .email(null)
                    .role(null)
                    .build();
        }
    }

    private Long safeUserId(User user) {
        try {
            return user.getId();
        } catch (RuntimeException ex) {
            return null;
        }
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
