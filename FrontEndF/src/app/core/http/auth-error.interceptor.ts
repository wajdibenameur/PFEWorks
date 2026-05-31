import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AUTH_CONTEXT } from '../auth/auth-context.port';
import { TokenRefreshService } from '../auth/token-refresh.service';
import { StompClientService } from '../realtime/stomp-client.service';
import { PlatformAlertService } from '../ui/platform-alert.service';

export const authErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const authContext = inject(AUTH_CONTEXT);
  const router = inject(Router);
  const tokenRefreshService = inject(TokenRefreshService);
  const stompClientService = inject(StompClientService);
  const platformAlertService = inject(PlatformAlertService);
  const isMonitoringEndpoint =
    request.url.includes('/api/monitoring/') ||
    request.url.includes('/api/zkbio/') ||
    request.url.includes('/api/cameras/') ||
    request.url.includes('/dashboard');

  return next(request).pipe(
    catchError((error: unknown) => {
      const isLogoutEndpoint = request.url.includes('/api/auth/logout');
      const isAuthEndpoint =
        request.url.includes('/api/auth/login') ||
        request.url.includes('/api/auth/refresh') ||
        isLogoutEndpoint;

      // Logout is idempotent on the frontend: if backend returns 401 because the
      // session/token is already invalid, we do not surface a blocking platform alert.
      if (error instanceof HttpErrorResponse && error.status === 401 && isLogoutEndpoint) {
        return throwError(() => error);
      }

      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isAuthEndpoint
      ) {
        return tokenRefreshService.refreshAccessToken().pipe(
          switchMap((token) => {
            console.debug('[AUTH] RETRY ORIGINAL REQUEST', request.url);
            return next(
              request.clone({
                setHeaders: {
                  Authorization: `Bearer ${token}`
                }
              })
            );
          }),
          catchError((refreshError: unknown) => {
            if (
              refreshError instanceof HttpErrorResponse &&
              (refreshError.status === 401 || refreshError.status === 403)
            ) {
              stompClientService.disconnect();
              authContext.logout();
              platformAlertService.showError(
                'Votre session a expire. Veuillez vous reconnecter.',
                refreshError.status,
                refreshError.url ?? request.url
              );
              void router.navigate(['/login']);
            }
            return throwError(() => refreshError);
          })
        );
      }

      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint) {
        stompClientService.disconnect();
        authContext.logout();
        platformAlertService.showError(
          'Votre session est invalide. Veuillez vous reconnecter.',
          error.status,
          error.url ?? request.url
        );
        void router.navigate(['/login']);
      }

      if (error instanceof HttpErrorResponse && (error.status === 403 || error.status === 401) && isAuthEndpoint) {
        if (request.url.includes('/api/auth/refresh')) {
          stompClientService.disconnect();
          authContext.logout();
          platformAlertService.showError(
            'Session expiree. Merci de vous reconnecter.',
            error.status,
            error.url ?? request.url
          );
          void router.navigate(['/login']);
        }
      }

      if (error instanceof HttpErrorResponse && !request.url.includes('/ws/')) {
        // Monitoring endpoints already implement degraded/fallback behavior in feature stores.
        // Avoid raising a global blocking alert for expected source instability.
        if (isMonitoringEndpoint) {
          return throwError(() => error);
        }

        if (error.status === 0) {
          platformAlertService.showError(
            'Le service est momentanement indisponible. Verifiez la connexion ou reessayez dans quelques secondes.',
            0,
            error.url ?? request.url
          );
        } else if (error.status >= 400) {
          platformAlertService.showHttpError(error, 'Une erreur est survenue. Merci de reessayer.');
        }
      }

      return throwError(() => error);
    })
  );
};

