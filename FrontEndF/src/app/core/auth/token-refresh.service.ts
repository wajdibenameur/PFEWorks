import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap } from 'rxjs/operators';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';
import { AUTH_CONTEXT, AuthContextPort } from './auth-context.port';

interface RefreshTokenResponse {
  access_token: string;
}

@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private refreshInFlight$: Observable<string> | null = null;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(AUTH_CONTEXT) private readonly authContext: AuthContextPort
  ) {}

  refreshAccessToken(): Observable<string> {
    if (this.refreshInFlight$) {
      console.debug('[AUTH] REFRESH WAIT EXISTING');
      return this.refreshInFlight$;
    }

    console.debug('[AUTH] REFRESH START');
    const ensureCsrf$: Observable<unknown> = this.hasXsrfCookie()
      ? of(null)
      : this.http.get(`${this.config.authApiUrl}/api/auth/csrf`, { withCredentials: true });

    const refresh$: Observable<string> = ensureCsrf$
      .pipe(
        switchMap(() =>
          this.http.post<RefreshTokenResponse>(`${this.config.authApiUrl}/api/auth/refresh`, {}, { withCredentials: true })
        )
      )
      .pipe(
        map((response) => {
          const nextAccessToken = response.access_token;
          if (!nextAccessToken) {
            throw new Error('Refresh response does not contain access_token');
          }
          this.authContext.setTokens(nextAccessToken, null);

          console.debug('[AUTH] REFRESH SUCCESS');
          return nextAccessToken;
        }),
        catchError((error: unknown) => {
          console.warn('[AUTH] REFRESH FAILED -> LOGOUT', error);
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1)
      );

    this.refreshInFlight$ = refresh$;
    return refresh$;
  }

  private hasXsrfCookie(): boolean {
    return document.cookie.includes('XSRF-TOKEN=');
  }
}

