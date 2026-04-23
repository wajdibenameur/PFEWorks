package tn.iteam.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketDecisionRequestDTO {
    private Long adminId;
    private String reason;
}
