import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AUTH_CONTEXT } from '../auth/auth-context.port';
import { TokenRefreshService } from './token-refresh.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly router = inject(Router);
  private readonly tokenRefreshService = inject(TokenRefreshService);

  canActivate() {
    if (this.auth.getAccessToken()) {
      return true;
    }

    if (this.auth.getRefreshToken()) {
      return this.tokenRefreshService.refreshAccessToken().pipe(
        map(() => true),
        catchError(() => {
          void this.router.navigate(['/login']);
          return of(false);
        })
      );
    }

    void this.router.navigate(['/login']);
    return false;
  }
}

