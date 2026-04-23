package tn.iteam.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketInterventionRequestDTO {
    private Long userId;
    private String action;
    private String comment;
    private String result;
}
