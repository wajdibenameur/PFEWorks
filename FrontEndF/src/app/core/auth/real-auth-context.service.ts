import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthContextPort, AuthUser } from '../../core/auth/auth-context.port';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';

interface CurrentUserPermissionsResponse {
  username: string;
  roles: string[];
  effectivePermissions: string[];
}

interface CurrentUserResponse {
  id: number | null;
  keycloakId: string | null;
  username: string;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  roles: string[];
}

@Injectable()
export class RealAuthContextService implements AuthContextPort {
  private readonly _isAuthenticated$ = new BehaviorSubject<boolean>(false);
  private readonly _user$ = new BehaviorSubject<AuthUser | null>(null);
  private readonly accessToken = signal<string | null>(null);
  private readonly effectivePermissions = signal<Set<string>>(new Set());
  private readonly permissionsLoaded = signal<boolean>(false);
  private logoutInProgress = false;

  readonly isAuthenticated$ = this._isAuthenticated$.asObservable();
  readonly user$ = this._user$.asObservable();
  private readonly authDebug = true;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    @Inject(APP_CONFIG) private readonly config: AppConfig
  ) {}

  getAccessToken(): string | null {
    const token = this.accessToken();
    if (!token) {
      return null;
    }

    if (!this.isTokenUsable(token)) {
      this.accessToken.set(null);
      if (this.authDebug) {
        console.info('[AUTH] ACCESS TOKEN EXPIRED');
      }
      return null;
    }

    return token;
  }

  getRefreshToken(): string | null {
    return null;
  }

  setTokens(accessToken: string, refreshToken: string | null): void {
    if (!this.isTokenUsable(accessToken)) {
      if (this.authDebug) {
        console.warn('[AUTH] TOKEN UPDATE REJECTED (INVALID ACCESS TOKEN)');
      }
      return;
    }

    this.accessToken.set(accessToken);
    void refreshToken;

    const fallbackUser = this.decodeUserFromToken(accessToken);
    if (this.authDebug) {
      console.info('[AUTH] TOKEN UPDATED');
    }
    this._user$.next(fallbackUser);
    this._isAuthenticated$.next(true);
    this.loadCurrentUser(accessToken, fallbackUser);
    this.loadEffectivePermissions(accessToken);
  }

  logout(): void {
    if (this.logoutInProgress) {
      return;
    }
    this.logoutInProgress = true;
    this.http.post(`${this.config.authApiUrl}/api/auth/logout`, {}, { withCredentials: true }).subscribe({
      error: () => {
        // Local logout must proceed even if backend revoke fails.
      },
      complete: () => {
        this.logoutInProgress = false;
      }
    });
    this.clearAccessToken();
  }

  private clearAccessToken(): void {
    this.accessToken.set(null);
    this.effectivePermissions.set(new Set());
    this.permissionsLoaded.set(false);
    this._user$.next(null);
    this._isAuthenticated$.next(false);
    this.logoutInProgress = false;
    void this.router.navigate(['/login']);
  }

  getRoles(): string[] {
    const user = this._user$.value;
    return user?.roles || [];
  }

  arePermissionsLoaded(): boolean {
    return this.permissionsLoaded();
  }

  hasRole(role: string): boolean {
    const wanted = role.trim().toUpperCase();
    const has = this.getRoles().includes(wanted);
    if (this.authDebug) {
      console.debug('[AUTH UI] hasRole', wanted, '=>', has, 'roles=', this.getRoles());
    }
    return has;
  }

  hasPermission(permission: string): boolean {
    // UI hint only. Backend remains source of truth.
    const wanted = permission.trim().toUpperCase();
    const has = this.effectivePermissions().has(wanted);
    if (this.authDebug) {
      console.debug(`[AUTH UI] permission check ${wanted} => ${has}`);
    }
    return has;
  }

  private decodeUserFromToken(token: string): AuthUser | null {
    try {
      const payload = this.parseTokenPayload(token);
      if (!payload) {
        return null;
      }

      const realmAccess = payload['realm_access'];

const technicalRoles = [
  'OFFLINE_ACCESS',
  'UMA_AUTHORIZATION',
  'DEFAULT-ROLES-MY-REALM'
];

      const roles = this.extractRoles(realmAccess)
  .map((role) => String(role).trim().toUpperCase())
  .filter((role) => !technicalRoles.includes(role));

const subject = this.readStringClaim(payload, 'sub');
      
      const preferredUsername = this.readStringClaim(payload, 'preferred_username');
      if (!subject) {
        return null;
      }

      return {
        id: subject,
        username: preferredUsername || subject,
        roles
      };
    } catch {
      return null;
    }
  }

  private loadEffectivePermissions(accessToken: string): void {
    this.permissionsLoaded.set(false);

    const headers = new HttpHeaders({
      Authorization: `Bearer ${accessToken}`
    });

    this.http
      .get<CurrentUserPermissionsResponse>(`${this.config.monitoringApiUrl}/api/auth/me/permissions`, { headers })
      .subscribe({
        next: (response) => {
          if (this.authDebug) {
            console.info('[AUTH UI] /api/auth/me/permissions response=', response);
          }
          const permissions = new Set(
            (response.effectivePermissions ?? []).map((permission) => permission.trim().toUpperCase())
          );
          this.effectivePermissions.set(permissions);
          this.permissionsLoaded.set(true);
          if (this.authDebug) {
            console.info('[AUTH UI] effectivePermissions set=', Array.from(permissions.values()));
          }
        },
        error: (error) => {
          if (this.authDebug) {
            console.error('[AUTH UI] permissions load failed', error);
          }
          this.effectivePermissions.set(new Set());
          this.permissionsLoaded.set(true);
        }
      });
  }

  private loadCurrentUser(accessToken: string, fallbackUser: AuthUser | null): void {
    const headers = new HttpHeaders({
      Authorization: `Bearer ${accessToken}`
    });

    this.http.get<CurrentUserResponse>(`${this.config.monitoringApiUrl}/api/auth/me`, { headers }).subscribe({
      next: (response) => {
        const backendRoles = (response.roles ?? []).map((role) => role.trim().toUpperCase());
        const resolvedId = String(response.id ?? '').trim() || response.keycloakId?.trim() || fallbackUser?.id;
        if (!resolvedId) {
          return;
        }

        this._user$.next({
          id: resolvedId,
          username: response.username ?? fallbackUser?.username ?? resolvedId,
          roles: backendRoles.length > 0 ? backendRoles : (fallbackUser?.roles ?? [])
        });
      },
      error: (error) => {
        if (this.authDebug) {
          console.warn('[AUTH UI] /api/auth/me failed, keeping JWT fallback user', error);
        }
      }
    });
  }

  private isTokenUsable(token: string): boolean {
    const payload = this.parseTokenPayload(token);
    if (!payload) {
      return false;
    }

    const exp = this.readNumberClaim(payload, 'exp');
    if (exp === null) {
      return true;
    }

    return exp * 1000 > Date.now();
  }

  private parseTokenPayload(token: string): Record<string, unknown> | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return null;
      }

      const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=');
      return JSON.parse(atob(padded)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private extractRoles(realmAccess: unknown): string[] {
    if (!realmAccess || typeof realmAccess !== 'object') {
      return [];
    }

    const roles = (realmAccess as Record<string, unknown>)['roles'];
    return Array.isArray(roles) ? roles.filter((role): role is string => typeof role === 'string') : [];
  }

  private readStringClaim(payload: Record<string, unknown>, claim: string): string | null {
    const value = payload[claim];
    return typeof value === 'string' && value.trim() ? value : null;
  }

  private readNumberClaim(payload: Record<string, unknown>, claim: string): number | null {
    const value = payload[claim];
    return typeof value === 'number' ? value : null;
  }
}
