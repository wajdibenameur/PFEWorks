import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import {
  AdminCreateUserPayload,
  AdminRole,
  AdminUpdateUserPayload,
  AdminUser,
  LocalAdminUserView,
  MergedAdminUser,
  SyncAllLocalUsersResponse,
  UserPermissionsView
} from '../../../core/models/admin-user.model';
import { AdminApiService } from '../../admin/data/admin-api.service';
import { extractApiError, extractApiErrorMessage } from '../../../core/http/http-error.utils';

type UserStatus = 'ACTIVE' | 'INACTIVE';
type EditorMode = 'create' | 'edit';

const ALL_PERMISSIONS = [
  'VIEW_DASHBOARD',
  'VIEW_ZABBIX',
  'VIEW_SNMP',
  'VIEW_CAMERA',
  'VIEW_ZKBIO',
  'VIEW_ACCESS_POINT',
  'VIEW_METRICS',
  'VIEW_ALERTS',
  'VIEW_LOGS',
  'EXPORT_DASHBOARD',
  'REFRESH_DASHBOARD',
  'MANAGE_DASHBOARD',
  'VIEW_HOSTS',
  'MANAGE_HOSTS',
  'EDIT_HOST',
  'DELETE_HOST',
  'VIEW_TICKETS',
  'VIEW_ALL_TICKETS',
  'VIEW_ASSIGNED_TICKETS',
  'CREATE_TICKET',
  'EDIT_TICKET',
  'DELETE_TICKET',
  'ASSIGN_TICKET',
  'VALIDATE_TICKET',
  'ADD_COMMENT',
  'EDIT_COMMENT',
  'DELETE_COMMENT',
  'VIEW_USERS',
  'EDIT_USER',
  'DELETE_USER',
  'ACTIVATE_USER',
  'DEACTIVATE_USER',
  'MANAGE_USERS',
  'VIEW_ROLES',
  'MANAGE_ROLES',
  'ASSIGN_ROLE_TO_USER',
  'REMOVE_ROLE_FROM_USER',
  'VIEW_PERMISSIONS',
  'MANAGE_PERMISSIONS'
] as const;

@Component({
  selector: 'app-user-management-page',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './user-management-page.component.html',
  styleUrls: ['./user-management-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserManagementPageComponent implements OnInit {
  private static readonly USERNAME_NO_SPACE_PATTERN = /^\S+$/;
  private static readonly PASSWORD_SPECIAL_CHAR_PATTERN = /^(?=.*[^A-Za-z0-9]).+$/;

  private readonly adminApi = inject(AdminApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly destroyRef = inject(DestroyRef);

  readonly users = signal<MergedAdminUser[]>([]);
  readonly roles = signal<AdminRole[]>([]);
  readonly localUserViews = signal<LocalAdminUserView[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isSyncAllRunning = signal(false);
  readonly isPermissionsSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly drawerOpen = signal(false);
  readonly editorMode = signal<EditorMode>('create');
  readonly editingUserId = signal<string | null>(null);
  readonly permissionDrawerOpen = signal(false);
  readonly selectedPermissionUser = signal<MergedAdminUser | null>(null);
  readonly selectedGrantPermission = signal<string>(ALL_PERMISSIONS[0]);
  readonly selectedRevokePermission = signal<string>(ALL_PERMISSIONS[0]);
  readonly currentAuthUsername = signal<string | null>(null);

  readonly roleFilter = signal<string>('ALL');
  readonly statusFilter = signal<string>('ALL');

  readonly userForm = this.formBuilder.nonNullable.group({
    username: [
      '',
      [Validators.required, Validators.minLength(8), Validators.pattern(UserManagementPageComponent.USERNAME_NO_SPACE_PATTERN)]
    ],
    email: ['', [Validators.required, Validators.email]],
    firstName: [''],
    lastName: [''],
    phone: [''],
    position: [''],
    role: ['VIEWER', [Validators.required]],
    enabled: [false],
    password: ['', [Validators.minLength(8), Validators.pattern(UserManagementPageComponent.PASSWORD_SPECIAL_CHAR_PATTERN)]]
  });

  ngOnInit(): void {
    this.auth.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((user) => {
      this.currentAuthUsername.set(user?.username ?? null);
    });
    this.loadReferenceData();
  }

  readonly permissionsLoaded = computed(() => this.auth.arePermissionsLoaded());
  readonly canViewUsers = computed(() => this.auth.hasPermission('VIEW_USERS'));
  readonly canManageUsers = computed(() => this.auth.hasPermission('MANAGE_USERS'));
  readonly canManagePermissions = computed(() => this.auth.hasPermission('MANAGE_PERMISSIONS'));

  readonly filteredUsers = computed(() =>
    this.users().filter((user) => {
      const matchesRole = this.roleFilter() === 'ALL' || this.primaryRole(user) === this.roleFilter();
      const matchesStatus = this.statusFilter() === 'ALL' || this.userStatus(user) === this.statusFilter();
      return matchesRole && matchesStatus;
    })
  );

  readonly activeCount = computed(() => this.users().filter((user) => user.enabled === true).length);
  readonly inactiveCount = computed(() => this.users().filter((user) => user.enabled !== true).length);
  readonly adminCount = computed(() =>
    this.users().filter((user) => ['SUPERADMIN', 'ADMIN'].includes(this.primaryRole(user))).length
  );

  readonly drawerTitle = computed(() =>
    this.editorMode() === 'create' ? 'Add a user' : 'Edit user'
  );

  readonly submitLabel = computed(() =>
    this.editorMode() === 'create' ? 'Create account' : 'Save changes'
  );

  readonly permissionChoices = computed(() => ALL_PERMISSIONS);

  protected primaryRole(user: MergedAdminUser): string {
    const roles = Array.isArray(user.roles) ? user.roles : [];
    return roles[0] ?? user.localRole ?? 'VIEWER';
  }

  protected userStatus(user: MergedAdminUser): UserStatus {
    return user.enabled === true ? 'ACTIVE' : 'INACTIVE';
  }

  protected displayName(user: MergedAdminUser): string {
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return fullName || user.username;
  }

  protected localSyncState(user: MergedAdminUser): string {
    return user.localUserId ? `Local #${user.localUserId}` : 'Not yet synced in backend';
  }

  protected syncAllLocalUsers(): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('You are not allowed to manage users.');
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isSyncAllRunning.set(true);

    this.adminApi.syncAllLocalUsers().subscribe({
      next: (result) => {
        this.successMessage.set(this.formatSyncAllMessage(result));
        this.loadReferenceData();
        this.isSyncAllRunning.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to synchronize users from Keycloak.'));
        this.isSyncAllRunning.set(false);
      }
    });
  }

  protected connectionStatus(user: MergedAdminUser): string {
    return this.isUserConnected(user) ? 'CONNECTED' : 'OFFLINE';
  }

  protected isUserConnected(user: MergedAdminUser): boolean {
    if (user.connected === true) {
      return true;
    }

    const currentUsername = this.currentAuthUsername();
    if (!currentUsername) {
      return false;
    }

    return currentUsername.trim().toLowerCase() === (user.username ?? '').trim().toLowerCase();
  }

  protected openCreateDrawer(): void {
    if (!this.canManageUsers()) {
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.editorMode.set('create');
    this.editingUserId.set(null);
    this.userForm.reset({
      username: '',
      email: '',
      firstName: '',
      lastName: '',
      phone: '',
      position: '',
      role: 'VIEWER',
      enabled: false,
      password: ''
    });
    this.userForm.controls.password.addValidators([
      Validators.required,
      Validators.minLength(8),
      Validators.pattern(UserManagementPageComponent.PASSWORD_SPECIAL_CHAR_PATTERN)
    ]);
    this.userForm.controls.password.updateValueAndValidity();
    this.drawerOpen.set(true);
  }

  protected openEditDrawer(user: MergedAdminUser): void {
    if (!this.canManageUsers()) {
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.editorMode.set('edit');
    this.editingUserId.set(user.id);
    this.userForm.reset({
      username: user.username,
      email: user.email,
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      phone: user.phone ?? '',
      position: user.position ?? '',
      role: this.primaryRole(user),
      enabled: user.enabled,
      password: ''
    });
    this.userForm.controls.password.removeValidators(Validators.required);
    this.userForm.controls.password.addValidators([
      Validators.minLength(8),
      Validators.pattern(UserManagementPageComponent.PASSWORD_SPECIAL_CHAR_PATTERN)
    ]);
    this.userForm.controls.password.updateValueAndValidity();
    this.drawerOpen.set(true);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected openPermissionsDrawer(user: MergedAdminUser): void {
    if (!this.canManagePermissions()) {
      return;
    }

    this.selectedPermissionUser.set(user);
    this.selectedGrantPermission.set(ALL_PERMISSIONS[0]);
    this.selectedRevokePermission.set(ALL_PERMISSIONS[0]);
    this.permissionDrawerOpen.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    if (user.localUserId) {
      this.loadUserPermissions(user.localUserId);
      return;
    }

    this.adminApi
      .syncLocalUser({
        username: user.username,
        email: user.email,
        role: this.primaryRole(user)
      })
      .subscribe({
        next: (permissions) => {
          this.applyPermissionUpdate(permissions);
        },
        error: (error) => {
          this.permissionDrawerOpen.set(false);
          this.selectedPermissionUser.set(null);
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to synchronize local user permissions.'));
        }
      });
  }

  private loadUserPermissions(localUserId: number): void {
    this.adminApi.getUserPermissions(localUserId).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
      },
      error: (error) => {
        this.permissionDrawerOpen.set(false);
        this.selectedPermissionUser.set(null);
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load user permissions.'));
      }
    });
  }

  protected closePermissionsDrawer(): void {
    this.permissionDrawerOpen.set(false);
    this.selectedPermissionUser.set(null);
  }

  protected submitForm(): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('You are not allowed to manage users.');
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.userForm.markAllAsTouched();

    if (this.userForm.invalid) {
      this.errorMessage.set(this.buildUserFormValidationMessage());
      return;
    }

    const rawValue = this.userForm.getRawValue();
    this.isSaving.set(true);

    if (this.editorMode() === 'create') {
      const payload: AdminCreateUserPayload = {
        username: rawValue.username.trim(),
        email: rawValue.email.trim(),
        password: rawValue.password,
        firstName: this.nullableValue(rawValue.firstName),
        lastName: this.nullableValue(rawValue.lastName),
        phone: this.nullableValue(rawValue.phone),
        position: this.nullableValue(rawValue.position),
        role: rawValue.role,
        enabled: rawValue.enabled
      };

      this.adminApi.createUser(payload).subscribe({
        next: (createdUser) => {
          this.rebuildUsers([createdUser, ...this.users()]);
          this.successMessage.set(`User ${createdUser.username} was created successfully.`);
          this.isSaving.set(false);
          this.userForm.reset({
            username: '',
            email: '',
            firstName: '',
            lastName: '',
            phone: '',
            position: '',
            role: 'VIEWER',
            enabled: false,
            password: ''
          });
        },
        error: (error) => {
          this.errorMessage.set(this.mapUserMutationError(error, 'create'));
          this.isSaving.set(false);
        }
      });
      return;
    }

    const userId = this.editingUserId();
    if (!userId) {
      this.errorMessage.set('No user selected for editing.');
      this.isSaving.set(false);
      return;
    }

    const payload: AdminUpdateUserPayload = {
      username: rawValue.username.trim(),
      email: rawValue.email.trim(),
      firstName: this.nullableValue(rawValue.firstName),
      lastName: this.nullableValue(rawValue.lastName),
      phone: this.nullableValue(rawValue.phone),
      position: this.nullableValue(rawValue.position),
      role: rawValue.role,
      enabled: rawValue.enabled
    };

    if (rawValue.password.trim()) {
      payload.password = rawValue.password.trim();
    }

    this.adminApi.updateUser(userId, payload).subscribe({
      next: (updatedUser) => {
        this.replaceAuthUser(updatedUser);
        this.successMessage.set(`User ${updatedUser.username} was updated successfully.`);
        this.isSaving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.mapUserMutationError(error, 'edit'));
        this.isSaving.set(false);
      }
    });
  }

  protected toggleUserStatus(user: MergedAdminUser): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('You are not allowed to manage users.');
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);

    this.adminApi.updateUserStatus(user.id, user.enabled !== true).subscribe({
      next: (updatedUser) => {
        this.loadUsers();
        if (updatedUser.enabled) {
          this.successMessage.set(
            `User ${updatedUser.username} was activated successfully. Information email was sent.`
          );
        } else {
          this.successMessage.set(`User ${updatedUser.username} was deactivated successfully.`);
        }
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to update user status.'));
      }
    });
  }

  protected forceLogout(user: MergedAdminUser): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('You are not allowed to manage users.');
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);

    this.adminApi.forceLogoutUser(user.id).subscribe({
      next: (updatedUser) => {
        this.replaceAuthUser(updatedUser);
        this.successMessage.set(
          `User ${updatedUser.username} was logged out successfully. Their active sessions were terminated and they must sign in again.`
        );
      },
      error: (error) => {
        this.errorMessage.set(this.mapForceLogoutError(error, user.username));
      }
    });
  }

  protected deleteUser(user: MergedAdminUser): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('You are not allowed to manage users.');
      return;
    }

    if (this.isSystemTechnicalUser(user)) {
      this.errorMessage.set('The SYSTEM technical account cannot be deleted.');
      return;
    }

    const confirmed = window.confirm(
      `Confirm deletion of user "${user.username}"? This action cannot be undone.`
    );
    if (!confirmed) {
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);

    this.adminApi.deleteUser(user.id).subscribe({
      next: () => {
        this.loadReferenceData();
        this.successMessage.set(`User ${user.username} was deleted successfully.`);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to delete user.'));
      }
    });
  }

  protected isSystemTechnicalUser(user: MergedAdminUser): boolean {
    const normalizedUsername = (user.username ?? '').trim().toUpperCase();
    const normalizedEmail = (user.email ?? '').trim().toLowerCase();
    const normalizedRole = this.primaryRole(user).trim().toUpperCase();

    return (
      normalizedUsername === 'SYSTEM' ||
      normalizedEmail === 'system@monitoring.local' ||
      normalizedRole === 'SYSTEM'
    );
  }

  protected grantSelectedPermission(): void {
    const user = this.selectedPermissionUser();
    if (!user?.localUserId) {
      return;
    }

    this.isPermissionsSaving.set(true);
    this.adminApi.grantUserPermission(user.localUserId, this.selectedGrantPermission()).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
        this.successMessage.set(`Permission ${this.selectedGrantPermission()} was granted to ${user.username}.`);
        this.isPermissionsSaving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to grant permission.'));
        this.isPermissionsSaving.set(false);
      }
    });
  }

  protected revokeSelectedPermission(): void {
    const user = this.selectedPermissionUser();
    if (!user?.localUserId) {
      return;
    }

    this.isPermissionsSaving.set(true);
    this.adminApi.revokeUserPermission(user.localUserId, this.selectedRevokePermission()).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
        this.successMessage.set(`Permission ${this.selectedRevokePermission()} was revoked from ${user.username}.`);
        this.isPermissionsSaving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to revoke permission.'));
        this.isPermissionsSaving.set(false);
      }
    });
  }

  protected removeGrantedPermission(permission: string): void {
    const user = this.selectedPermissionUser();
    if (!user?.localUserId) {
      return;
    }

    this.isPermissionsSaving.set(true);
    this.adminApi.removeGrantedPermission(user.localUserId, permission).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
        this.successMessage.set(`Custom granted permission ${permission} was removed for ${user.username}.`);
        this.isPermissionsSaving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to remove granted permission.'));
        this.isPermissionsSaving.set(false);
      }
    });
  }

  protected removeRevokedPermission(permission: string): void {
    const user = this.selectedPermissionUser();
    if (!user?.localUserId) {
      return;
    }

    this.isPermissionsSaving.set(true);
    this.adminApi.removeRevokedPermission(user.localUserId, permission).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
        this.successMessage.set(`Custom revoked permission ${permission} was cleared for ${user.username}.`);
        this.isPermissionsSaving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to remove revoked permission.'));
        this.isPermissionsSaving.set(false);
      }
    });
  }

  protected roleDescription(roleName: string): string {
    return this.roles().find((role) => role.name === roleName)?.description ?? 'No description available.';
  }

  private loadReferenceData(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    let authUsers: AdminUser[] | null = null;
    let roles: AdminRole[] | null = null;
    let localUsers: LocalAdminUserView[] | null = null;

    const tryFinalize = () => {
      if (!authUsers || !roles || !localUsers) {
        return;
      }

      this.roles.set(roles);
      this.localUserViews.set(localUsers);
      this.rebuildUsers(authUsers);
      this.isLoading.set(false);
    };

    this.loadUsers(
      (users) => {
        authUsers = users;
        tryFinalize();
      },
      () => this.isLoading.set(false)
    );

    this.adminApi.getRoles().subscribe({
      next: (incomingRoles) => {
        roles = incomingRoles;
        tryFinalize();
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load application roles.'));
        this.isLoading.set(false);
      }
    });

    this.adminApi.getLocalUsers().subscribe({
      next: (users) => {
        localUsers = users;
        tryFinalize();
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load local permission view.'));
        this.isLoading.set(false);
      }
    });
  }

  private rebuildUsers(authUsers: AdminUser[]): void {
    const localByUsername = new Map(
      this.localUserViews().map((user) => [user.username.toLowerCase(), user] as const)
    );

    const mergedUsers = authUsers
      .map((user) => this.mergeUser(user, localByUsername.get(user.username.toLowerCase()) ?? null))
      .sort((left, right) => left.username.localeCompare(right.username));

    this.users.set(mergedUsers);
  }

  private loadUsers(onLoaded?: (users: AdminUser[]) => void, onError?: () => void): void {
    this.adminApi.getUsers().subscribe({
      next: (users) => {
        this.rebuildUsers(users);
        onLoaded?.(users);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load admin users.'));
        onError?.();
      }
    });
  }

  private mergeUser(authUser: AdminUser, localUser: LocalAdminUserView | null): MergedAdminUser {
    const normalizedRoles = Array.isArray(authUser.roles)
      ? authUser.roles.filter((role): role is string => typeof role === 'string' && role.trim().length > 0)
      : [];

    return {
      ...authUser,
      roles: normalizedRoles,
      localUserId: localUser?.id ?? null,
      localRole: localUser?.role ?? null,
      rolePermissions: localUser?.rolePermissions ?? [],
      extraPermissions: localUser?.extraPermissions ?? [],
      revokedPermissions: localUser?.revokedPermissions ?? [],
      effectivePermissions: localUser?.effectivePermissions ?? []
    };
  }

  private replaceAuthUser(updatedUser: AdminUser): void {
    const authUsers = this.users().map((user) =>
      user.id === updatedUser.id
        ? {
            ...updatedUser,
            localUserId: user.localUserId,
            localRole: user.localRole,
            rolePermissions: user.rolePermissions,
            extraPermissions: user.extraPermissions,
            revokedPermissions: user.revokedPermissions,
            effectivePermissions: user.effectivePermissions
          }
        : user
    );

    this.users.set(authUsers.sort((left, right) => left.username.localeCompare(right.username)));
  }

  private applyPermissionUpdate(permissions: UserPermissionsView): void {
    this.localUserViews.update((views) => {
      const nextView: LocalAdminUserView = {
        id: permissions.userId,
        username: permissions.username,
        email: views.find((view) => view.id === permissions.userId)?.email ?? '',
        role: permissions.role,
        rolePermissions: permissions.rolePermissions,
        extraPermissions: permissions.extraPermissions,
        revokedPermissions: permissions.revokedPermissions,
        effectivePermissions: permissions.effectivePermissions
      };

      const withoutCurrent = views.filter((view) => view.id !== permissions.userId);
      return [...withoutCurrent, nextView];
    });

    this.users.update((users) =>
      users.map((user) => {
        const sameLocalUser =
          user.localUserId === permissions.userId ||
          user.username.toLowerCase() === permissions.username.toLowerCase();

        if (!sameLocalUser) {
          return user;
        }

        const updatedUser: MergedAdminUser = {
          ...user,
          localUserId: permissions.userId,
          localRole: permissions.role,
          rolePermissions: permissions.rolePermissions,
          extraPermissions: permissions.extraPermissions,
          revokedPermissions: permissions.revokedPermissions,
          effectivePermissions: permissions.effectivePermissions
        };

        this.selectedPermissionUser.set(updatedUser);
        return updatedUser;
      })
    );
  }

  private formatSyncAllMessage(result: SyncAllLocalUsersResponse): string {
    return (
      `Synchronization completed: ${result.synchronizedUsers}/${result.totalKeycloakUsers} users synchronized ` +
      `(created=${result.createdUsers}, updated=${result.updatedUsers}, skipped=${result.skippedUsers}, failed=${result.failedUsers}).`
    );
  }

  private nullableValue(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }

  private buildUserFormValidationMessage(): string {
    const usernameErrors = this.userForm.controls.username.errors;
    if (usernameErrors?.['required']) {
      return 'Username is required.';
    }
    if (usernameErrors?.['minlength']) {
      return 'Username must be at least 8 characters long.';
    }
    if (usernameErrors?.['pattern']) {
      return 'Username must not contain spaces.';
    }

    const emailErrors = this.userForm.controls.email.errors;
    if (emailErrors?.['required']) {
      return 'Email is required.';
    }
    if (emailErrors?.['email']) {
      return 'Email format is invalid.';
    }

    const passwordErrors = this.userForm.controls.password.errors;
    if (passwordErrors?.['required'] && this.editorMode() === 'create') {
      return 'Password is required.';
    }
    if (passwordErrors?.['minlength']) {
      return 'Password must be at least 8 characters long.';
    }
    if (passwordErrors?.['pattern']) {
      return 'Password must include at least one special character.';
    }

    return 'Some fields are invalid. Please check your input and try again.';
  }

  private mapUserMutationError(error: unknown, mode: EditorMode): string {
    const apiError = extractApiError(error);
    const sourceText = `${apiError?.source ?? ''}`.toLowerCase();
    const codeText = `${apiError?.errorCode ?? ''}`.toLowerCase();
    const messageText = `${apiError?.message ?? ''}`.toLowerCase();
    const combined = `${sourceText} ${codeText} ${messageText}`;

    if (combined.includes('email') && (combined.includes('exist') || combined.includes('already'))) {
      return 'This email already exists. Please use another email.';
    }

    if (
      (combined.includes('username') || combined.includes('user name') || combined.includes('login')) &&
      (combined.includes('exist') || combined.includes('already') || combined.includes('duplicate') || combined.includes('taken'))
    ) {
      return 'This username already exists. Please choose another username.';
    }

    const fallback =
      mode === 'create'
        ? 'Unable to create user.'
        : 'Unable to update user.';
    return extractApiErrorMessage(error, fallback);
  }

  private mapForceLogoutError(error: unknown, username: string): string {
    const apiError = extractApiError(error);
    const sourceText = `${apiError?.source ?? ''}`.toLowerCase();
    const codeText = `${apiError?.errorCode ?? ''}`.toLowerCase();
    const messageText = `${apiError?.message ?? ''}`.toLowerCase();
    const combined = `${sourceText} ${codeText} ${messageText}`;

    if (combined.includes('forbidden') || combined.includes('access denied') || combined.includes('not allowed')) {
      return `Unable to force logout for ${username}: you do not have permission to perform this action.`;
    }

    if (
      combined.includes('not found') ||
      combined.includes('user_not_found') ||
      combined.includes('resource_not_found')
    ) {
      return `Unable to force logout for ${username}: user was not found in the identity provider.`;
    }

    if (combined.includes('session') && (combined.includes('not found') || combined.includes('already'))) {
      return `Unable to force logout for ${username}: no active session was found to terminate.`;
    }

    return extractApiErrorMessage(error, `Unable to force logout for ${username}. Please try again.`);
  }
}
