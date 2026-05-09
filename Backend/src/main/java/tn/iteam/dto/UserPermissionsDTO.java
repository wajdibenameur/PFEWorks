package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class UserPermissionsDTO {
    Long userId;
    String username;
    String role;
    Set<String> rolePermissions;
    Set<String> extraPermissions;
    Set<String> revokedPermissions;
    Set<String> effectivePermissions;
}
