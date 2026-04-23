package tn.iteam.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketStatusUpdateRequestDTO {
    private String status;
    private String resolution;
}
