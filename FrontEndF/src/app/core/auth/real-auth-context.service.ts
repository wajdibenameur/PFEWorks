import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { Inject, Injectable, signal } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthContextPort, AuthUser } from '../../core/auth/auth-context.port';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';

interface CurrentUserPermissionsResponse {
  username: string;
  roles: string[];
  effectivePermissions: string[];
}

@Injectable()
export class RealAuthContextService implements AuthContextPort {
  private readonly _isAuthenticated$ = new BehaviorSubject<boolean>(false);
  private readonly _user$ = new BehaviorSubject<AuthUser | null>(null);
  private readonly accessToken = signal<string | null>(null);
  private readonly refreshToken = signal<string | null>(null);
  private readonly effectivePermissions = signal<Set<string>>(new Set());
  private readonly permissionsLoaded = signal<boolean>(false);
  private readonly http: HttpClient;

  readonly isAuthenticated$ = this._isAuthenticated$.asObservable();
  readonly user$ = this._user$.asObservable();

  constructor(
    httpBackend: HttpBackend,
    @Inject(APP_CONFIG) private readonly config: AppConfig
  ) {
    this.http = new HttpClient(httpBackend);

    const storedAccessToken = localStorage.getItem('accessToken');
    const storedRefreshToken = localStorage.getItem('refreshToken');

    if (storedAccessToken && this.isTokenUsable(storedAccessToken)) {
      this.setTokens(storedAccessToken, storedRefreshToken);
    } else if (storedRefreshToken) {
      this.refreshToken.set(storedRefreshToken);
      localStorage.removeItem('accessToken');
    } else if (storedAccessToken) {
      this.logout();
    }
  }

  getAccessToken(): string | null {
    const token = this.accessToken();
    if (!token) {
      return null;
    }

    if (!this.isTokenUsable(token)) {
      this.clearAccessToken();
      return null;
    }

    return token;
  }

  getRefreshToken(): string | null {
    return this.refreshToken();
  }

  setTokens(accessToken: string, refreshToken: string | null): void {
    if (!this.isTokenUsable(accessToken)) {
      this.clearAccessToken();
      return;
    }

    this.accessToken.set(accessToken);
    this.refreshToken.set(refreshToken);

    localStorage.setItem('accessToken', accessToken);
    if (refreshToken) {
      localStorage.setItem('refreshToken', refreshToken);
    } else {
      localStorage.removeItem('refreshToken');
    }

    // Decode user from token
    const user = this.decodeUserFromToken(accessToken);
    this._user$.next(user);
    this._isAuthenticated$.next(true);
    this.loadEffectivePermissions(accessToken);
  }

  logout(): void {
    this.clearAccessToken();
    this.refreshToken.set(null);
    localStorage.removeItem('refreshToken');
  }

  private clearAccessToken(): void {
    this.accessToken.set(null);
    this.effectivePermissions.set(new Set());
    this.permissionsLoaded.set(false);
    localStorage.removeItem('accessToken');
    this._user$.next(null);
    this._isAuthenticated$.next(false);
  }

  getRoles(): string[] {
    const user = this._user$.value;
    return user?.roles || [];
  }

  arePermissionsLoaded(): boolean {
    return this.permissionsLoaded();
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role.trim().toUpperCase());
  }

  hasPermission(permission: string): boolean {
    // UI hint only. Backend remains source of truth.
    return this.effectivePermissions().has(permission.trim().toUpperCase());
  }

  private decodeUserFromToken(token: string): AuthUser | null {
    try {
      const payload = this.parseTokenPayload(token);
      if (!payload) {
        return null;
      }

      const realmAccess = payload['realm_access'];
      const roles = this.extractRoles(realmAccess).map((role) =>
        String(role).trim().toUpperCase()
      );
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
          const permissions = new Set(
            (response.effectivePermissions ?? []).map((permission) => permission.trim().toUpperCase())
          );
          this.effectivePermissions.set(permissions);
          this.permissionsLoaded.set(true);
        },
        error: () => {
          this.effectivePermissions.set(new Set());
          this.permissionsLoaded.set(true);
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

