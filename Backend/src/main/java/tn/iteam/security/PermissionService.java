package tn.iteam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import tn.iteam.enums.Permission;

@Service("permissionService")
public class PermissionService {

    public boolean hasPermission(Authentication authentication, Permission permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission.name()::equals);
    }
}
