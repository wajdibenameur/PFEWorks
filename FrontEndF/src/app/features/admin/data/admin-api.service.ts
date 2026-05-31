import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import {
  AdminCreateUserPayload,
  AdminRole,
  AdminUpdateUserPayload,
  AdminUser,
  LocalAdminUserView,
  SyncAllLocalUsersResponse,
  SyncLocalUserPayload,
  UserPermissionsView
} from '../../../core/models/admin-user.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly authAdminBaseUrl: string;
  private readonly monitoringAdminBaseUrl: string;
  private readonly monitoringBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.authAdminBaseUrl = `${config.authApiUrl}/api/admin`;
    this.monitoringAdminBaseUrl = `${config.monitoringApiUrl}/api/admin`;
    this.monitoringBaseUrl = `${config.monitoringApiUrl}/api/monitoring`;
  }

  getUsers(): Observable<AdminUser[]> {
    return this.http.get<unknown[]>(`${this.authAdminBaseUrl}/users`).pipe(
      map((users) => users.map((user) => this.normalizeAdminUser(user)))
    );
  }

  getRoles(): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(`${this.authAdminBaseUrl}/roles`);
  }

  createUser(payload: AdminCreateUserPayload): Observable<AdminUser> {
    return this.http.post<unknown>(`${this.authAdminBaseUrl}/users`, payload).pipe(
      map((user) => this.normalizeAdminUser(user))
    );
  }

  updateUser(userId: string, payload: AdminUpdateUserPayload): Observable<AdminUser> {
    return this.http.put<unknown>(`${this.authAdminBaseUrl}/users/${userId}`, payload).pipe(
      map((user) => this.normalizeAdminUser(user))
    );
  }

  updateUserStatus(userId: string, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<unknown>(`${this.authAdminBaseUrl}/users/${userId}/status`, { enabled }).pipe(
      map((user) => this.normalizeAdminUser(user))
    );
  }

  forceLogoutUser(userId: string): Observable<AdminUser> {
    return this.http.post<unknown>(`${this.authAdminBaseUrl}/users/${userId}/force-logout`, {}).pipe(
      map((user) => this.normalizeAdminUser(user))
    );
  }

  deleteUser(userId: string): Observable<void> {
    return this.http.delete<void>(`${this.authAdminBaseUrl}/users/${userId}`);
  }

  getLocalUsers(): Observable<LocalAdminUserView[]> {
    return this.http.get<LocalAdminUserView[]>(`${this.monitoringAdminBaseUrl}/users`);
  }

  syncLocalUser(payload: SyncLocalUserPayload): Observable<UserPermissionsView> {
    return this.http.post<UserPermissionsView>(`${this.monitoringAdminBaseUrl}/users/sync-local`, payload);
  }

  syncAllLocalUsers(): Observable<SyncAllLocalUsersResponse> {
    return this.http.post<SyncAllLocalUsersResponse>(`${this.monitoringAdminBaseUrl}/users/sync-all-local`, {});
  }

  getUserPermissions(userId: number): Observable<UserPermissionsView> {
    return this.http.get<UserPermissionsView>(`${this.monitoringAdminBaseUrl}/users/${userId}/permissions`);
  }

  grantUserPermission(userId: number, permission: string): Observable<UserPermissionsView> {
    return this.http.post<UserPermissionsView>(
      `${this.monitoringAdminBaseUrl}/users/${userId}/permissions/grant`,
      { permission }
    );
  }

  revokeUserPermission(userId: number, permission: string): Observable<UserPermissionsView> {
    return this.http.post<UserPermissionsView>(
      `${this.monitoringAdminBaseUrl}/users/${userId}/permissions/revoke`,
      { permission }
    );
  }

  removeGrantedPermission(userId: number, permission: string): Observable<UserPermissionsView> {
    return this.http.delete<UserPermissionsView>(
      `${this.monitoringAdminBaseUrl}/users/${userId}/permissions/grant/${permission}`
    );
  }

  removeRevokedPermission(userId: number, permission: string): Observable<UserPermissionsView> {
    return this.http.delete<UserPermissionsView>(
      `${this.monitoringAdminBaseUrl}/users/${userId}/permissions/revoke/${permission}`
    );
  }

  getSourceHealth(): Observable<SourceAvailability[]> {
    return this.http.get<SourceAvailability[]>(`${this.monitoringBaseUrl}/sources/health`);
  }

  private normalizeAdminUser(input: unknown): AdminUser {
    const source = (input ?? {}) as Record<string, unknown>;

    const enabledRaw =
      source['enabled'] ??
      source['isEnabled'] ??
      source['active'] ??
      source['isActive'] ??
      source['status'];

    const enabled =
      typeof enabledRaw === 'boolean'
        ? enabledRaw
        : typeof enabledRaw === 'number'
          ? enabledRaw === 1
          : String(enabledRaw ?? '')
              .trim()
              .toUpperCase() === 'ACTIVE' ||
            String(enabledRaw ?? '')
              .trim()
              .toLowerCase() === 'true';

    return {
      id: String(source['id'] ?? ''),
      username: String(source['username'] ?? ''),
      email: String(source['email'] ?? ''),
      firstName: this.nullable(source['firstName']),
      lastName: this.nullable(source['lastName']),
      phone: this.nullable(source['phone']),
      address: this.nullable(source['address']),
      city: this.nullable(source['city']),
      zipCode: this.nullable(source['zipCode']),
      position: this.nullable(source['position']),
      enabled,
      connected: Boolean(source['connected']),
      roles: Array.isArray(source['roles']) ? source['roles'].map((role) => String(role)) : []
    };
  }

  private nullable(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null;
    }
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
}

