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
    String priority;
    Boolean externalProblem;
    String monitoringSource;
    String externalProblemId;
    String resourceRef;
    String resolution;
    Boolean archived;
    TicketUserDTO createdBy;
    TicketUserDTO assignedTo;
    TicketUserDTO validatedBy;
    List<TicketInterventionDTO> interventions;
}
