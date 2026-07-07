import { Injectable, inject } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AUTH_CONTEXT } from '../auth/auth-context.port';

@Injectable({ providedIn: 'root' })
export class RoleGuard implements CanActivate {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const requiredAnyPermissions = ((route.data?.['permissionsAny'] as string[]) || []).map((permission) =>
      permission.trim().toUpperCase()
    );
    const requiredAllPermissions = ((route.data?.['permissionsAll'] as string[]) || []).map((permission) =>
      permission.trim().toUpperCase()
    );
    if (requiredAnyPermissions.length === 0 && requiredAllPermissions.length === 0) {
      return true;
    }

    // Do not block first navigation right after login while permissions are still loading.
    // Backend authorization remains the source of truth for protected API actions.
    if (!this.auth.arePermissionsLoaded()) {
      return true;
    }

    const matchesAny =
      requiredAnyPermissions.length === 0 ||
      requiredAnyPermissions.some((permission) => this.auth.hasPermission(permission));
    const matchesAll =
      requiredAllPermissions.length === 0 ||
      requiredAllPermissions.every((permission) => this.auth.hasPermission(permission));

    if (matchesAny && matchesAll) {
      return true;
    }

    this.router.navigate(['/dashboard']);
    return false;
  }
}

