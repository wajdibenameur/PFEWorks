package tn.iteam.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * Keycloak Admin API: User representation for create/read operations.
 */
@Data
@Builder
@Jacksonized
public class KeycloakUserRepresentation {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;

    @JsonProperty("emailVerified")
    private Boolean emailVerified;

    private Map<String, List<String>> attributes;

    private List<Map<String, Object>> credentials;
}

