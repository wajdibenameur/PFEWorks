package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class TicketInterventionDTO {
    Long id;
    String action;
    String comment;
    String result;
    LocalDateTime timestamp;
    TicketUserDTO performedBy;
}
