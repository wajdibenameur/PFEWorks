import { Injectable, inject } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AUTH_CONTEXT } from '../auth/auth-context.port';

@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    // UX-only route hint. Backend authorization still decides access.
    const requiredAnyPermissions = ((route.data?.['permissionsAny'] as string[]) || []).map((permission) =>
      permission.trim().toUpperCase()
    );
    const requiredRoles = ((route.data?.['roles'] as string[]) || []).map((role) =>
      role.trim().toUpperCase()
    );
    const userRoles = this.auth.getRoles();

    if (requiredAnyPermissions.length > 0) {
      if (this.auth.arePermissionsLoaded()) {
        if (requiredAnyPermissions.some((permission) => this.auth.hasPermission(permission))) {
          return true;
        }
      } else if (requiredRoles.length > 0 && requiredRoles.some((role) => userRoles.includes(role))) {
        return true;
      }
    }

    if (requiredRoles.some(role => userRoles.includes(role))) {
      return true;
    }

    this.router.navigate(['/dashboard']); // Redirect to safe page
    return false;
  }
}

