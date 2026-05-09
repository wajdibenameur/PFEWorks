import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import {
  AdminCreateUserPayload,
  AdminRole,
  AdminUpdateUserPayload,
  AdminUser,
  LocalAdminUserView,
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
    return this.http.get<AdminUser[]>(`${this.authAdminBaseUrl}/users`);
  }

  getRoles(): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(`${this.authAdminBaseUrl}/roles`);
  }

  createUser(payload: AdminCreateUserPayload): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.authAdminBaseUrl}/users`, payload);
  }

  updateUser(userId: string, payload: AdminUpdateUserPayload): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.authAdminBaseUrl}/users/${userId}`, payload);
  }

  updateUserStatus(userId: string, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.authAdminBaseUrl}/users/${userId}/status`, { enabled });
  }

  getLocalUsers(): Observable<LocalAdminUserView[]> {
    return this.http.get<LocalAdminUserView[]>(`${this.monitoringAdminBaseUrl}/users`);
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
}

