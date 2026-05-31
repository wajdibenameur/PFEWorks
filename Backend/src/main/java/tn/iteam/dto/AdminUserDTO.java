package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class AdminUserDTO {
    Long id;
    String username;
    String email;
    String phone;
    String position;
    String role;
    Boolean enabled;
    Set<String> rolePermissions;
    Set<String> extraPermissions;
    Set<String> revokedPermissions;
    Set<String> effectivePermissions;
}
