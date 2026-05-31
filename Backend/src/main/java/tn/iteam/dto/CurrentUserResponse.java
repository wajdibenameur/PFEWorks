package tn.iteam.dto;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        Set<String> roles
) {
}
