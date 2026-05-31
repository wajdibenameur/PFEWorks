package tn.iteam.auth.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class KeycloakRoleRepresentation {

    private String id;
    private String name;
    private String description;
}

