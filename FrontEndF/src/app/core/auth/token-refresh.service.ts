import { HttpBackend, HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable, catchError, finalize, map, shareReplay, throwError } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';
import { StompClientService } from '../realtime/stomp-client.service';
import { AUTH_CONTEXT, AuthContextPort } from './auth-context.port';

interface RefreshTokenResponse {
  access_token: string;
  refresh_token?: string | null;
}

@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private readonly http: HttpClient;
  private refreshInFlight$: Observable<string> | null = null;

  constructor(
    httpBackend: HttpBackend,
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(AUTH_CONTEXT) private readonly authContext: AuthContextPort,
    private readonly stompClientService: StompClientService
  ) {
    this.http = new HttpClient(httpBackend);
  }

  refreshAccessToken(): Observable<string> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const refreshToken = this.authContext.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('Refresh token is not available'));
    }

    this.refreshInFlight$ = this.http
      .post<RefreshTokenResponse>(`${this.config.authApiUrl}/api/auth/refresh`, {
        refreshToken
      })
      .pipe(
        map((response) => {
          const nextAccessToken = response.access_token;
          const nextRefreshToken = response.refresh_token ?? refreshToken;
          this.authContext.setTokens(nextAccessToken, nextRefreshToken);
          this.stompClientService.reconnect();
          return nextAccessToken;
        }),
        catchError((error: unknown) => {
          this.stompClientService.disconnect();
          this.authContext.logout();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1)
      );

    return this.refreshInFlight$;
  }
}

