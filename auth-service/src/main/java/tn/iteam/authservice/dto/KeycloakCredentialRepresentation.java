package tn.iteam.authservice.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/**
 * Keycloak Admin API: Credential representation for password reset.
 */
@Data
@Builder
@Jacksonized
public class KeycloakCredentialRepresentation {

    private String type;
    private String value;
    private Boolean temporary;
}
