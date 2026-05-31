package tn.iteam.auth.dto;

import lombok.Data;

@Data
public class KeycloakUserSessionRepresentation {
    private String id;
    private String username;
    private String ipAddress;
    private Long start;
    private Long lastAccess;
}

