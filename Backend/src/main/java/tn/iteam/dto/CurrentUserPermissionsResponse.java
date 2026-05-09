package tn.iteam.dto;

import java.util.Set;

public record CurrentUserPermissionsResponse(
        String username,
        Set<String> roles,
        Set<String> effectivePermissions
) {
}
