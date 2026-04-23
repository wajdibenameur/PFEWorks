package tn.iteam.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketCreateRequestDTO {
    private String title;
    private String description;
    private String priority;
    private Long creatorId;
    private Long hostId;
    private String monitoringSource;
    private String externalProblemId;
    private String resourceRef;
    private Boolean externalProblem;
}
