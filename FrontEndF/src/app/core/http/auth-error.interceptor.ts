import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AUTH_CONTEXT } from '../auth/auth-context.port';
import { TokenRefreshService } from '../auth/token-refresh.service';
import { StompClientService } from '../realtime/stomp-client.service';

export const authErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const authContext = inject(AUTH_CONTEXT);
  const router = inject(Router);
  const tokenRefreshService = inject(TokenRefreshService);
  const stompClientService = inject(StompClientService);

  return next(request).pipe(
    catchError((error: unknown) => {
      const isAuthEndpoint =
        request.url.includes('/api/auth/login') || request.url.includes('/api/auth/refresh');

      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isAuthEndpoint &&
        authContext.getRefreshToken()
      ) {
        return tokenRefreshService.refreshAccessToken().pipe(
          switchMap((token) =>
            next(
              request.clone({
                setHeaders: {
                  Authorization: `Bearer ${token}`
                }
              })
            )
          ),
          catchError((refreshError: unknown) => {
            stompClientService.disconnect();
            authContext.logout();
            void router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }

      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint) {
        stompClientService.disconnect();
        authContext.logout();
        void router.navigate(['/login']);
      }

      return throwError(() => error);
    })
  );
};

