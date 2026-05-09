package tn.iteam.authservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterResponse {

    private String message;
    private String keycloakUserId;
}
