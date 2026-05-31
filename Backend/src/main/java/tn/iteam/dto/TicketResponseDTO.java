package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class TicketResponseDTO {
    Long id;
    String title;
    Long hostId;
    String description;
    LocalDateTime creationDate;
    String status;
    LocalDateTime statusChangedAt;
    LocalDateTime resolvedAt;
    LocalDateTime validatedAt;
    String priority;
    Boolean externalProblem;
    String monitoringSource;
    String externalProblemId;
    String resourceRef;
    String resolution;
    Boolean archived;
    LocalDateTime archivedAt;
    TicketUserDTO createdBy;
    TicketUserDTO assignedTo;
    TicketUserDTO validatedBy;
    List<TicketInterventionDTO> interventions;
}
