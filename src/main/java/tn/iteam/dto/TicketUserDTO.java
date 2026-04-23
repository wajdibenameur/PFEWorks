package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TicketUserDTO {
    Long id;
    String username;
    String email;
    String role;
}
