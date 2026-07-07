import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay } from 'rxjs/operators';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';
import { AUTH_CONTEXT, AuthContextPort } from './auth-context.port';

interface RefreshTokenResponse {
  access_token: string;
  refresh_token?: string | null;
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

    const refreshToken = this.authContext.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('Refresh token unavailable'));
    }

    console.debug('[AUTH] REFRESH START');
    const refresh$: Observable<string> = this.http
      .post<RefreshTokenResponse>(`${this.config.authApiUrl}/api/auth/refresh`, {
        refreshToken
      })
      .pipe(
        map((response) => {
          const nextAccessToken = response.access_token;
          if (!nextAccessToken) {
            throw new Error('Refresh response does not contain access_token');
          }
          this.authContext.setTokens(nextAccessToken, response.refresh_token ?? refreshToken);

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
}

