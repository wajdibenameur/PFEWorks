package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketDTO {

    private Long id;
    private String title;
    private String description;

    private Long hostId;

    private String status;
    private String priority;

    private Long assignedTo;
    private String assignedName;

    private LocalDateTime creationDate;
}