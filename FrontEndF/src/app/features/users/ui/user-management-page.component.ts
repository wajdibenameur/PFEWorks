import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import {
  AdminCreateUserPayload,
  AdminRole,
  AdminUpdateUserPayload,
  AdminUser,
  LocalAdminUserView,
  MergedAdminUser,
  UserPermissionsView
} from '../../../core/models/admin-user.model';
import { AdminApiService } from '../../admin/data/admin-api.service';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';

type UserStatus = 'ACTIVE' | 'INACTIVE';
type EditorMode = 'create' | 'edit';

const ALL_PERMISSIONS = [
  'VIEW_DASHBOARD',
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
  private readonly adminApi = inject(AdminApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AUTH_CONTEXT);

  readonly users = signal<MergedAdminUser[]>([]);
  readonly roles = signal<AdminRole[]>([]);
  readonly localUserViews = signal<LocalAdminUserView[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
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

  readonly roleFilter = signal<string>('ALL');
  readonly statusFilter = signal<string>('ALL');

  readonly userForm = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    firstName: [''],
    lastName: [''],
    role: ['VIEWER', [Validators.required]],
    enabled: [true],
    password: ['', [Validators.minLength(8)]]
  });

  ngOnInit(): void {
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

  readonly activeCount = computed(() => this.users().filter((user) => user.enabled).length);
  readonly inactiveCount = computed(() => this.users().filter((user) => !user.enabled).length);
  readonly adminCount = computed(() =>
    this.users().filter((user) => ['SUPERADMIN', 'ADMIN'].includes(this.primaryRole(user))).length
  );

  readonly drawerTitle = computed(() =>
    this.editorMode() === 'create' ? 'Ajouter un utilisateur' : 'Modifier un utilisateur'
  );

  readonly submitLabel = computed(() =>
    this.editorMode() === 'create' ? 'Creer le compte' : 'Enregistrer les modifications'
  );

  readonly permissionChoices = computed(() => ALL_PERMISSIONS);

  protected primaryRole(user: MergedAdminUser): string {
    return user.roles[0] ?? user.localRole ?? 'VIEWER';
  }

  protected userStatus(user: MergedAdminUser): UserStatus {
    return user.enabled ? 'ACTIVE' : 'INACTIVE';
  }

  protected displayName(user: MergedAdminUser): string {
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    return fullName || user.username;
  }

  protected localSyncState(user: MergedAdminUser): string {
    return user.localUserId ? `Local #${user.localUserId}` : 'Not yet synced in Backend';
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
      role: 'VIEWER',
      enabled: true,
      password: ''
    });
    this.userForm.controls.password.addValidators([Validators.required, Validators.minLength(8)]);
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
      role: this.primaryRole(user),
      enabled: user.enabled,
      password: ''
    });
    this.userForm.controls.password.removeValidators(Validators.required);
    this.userForm.controls.password.addValidators([Validators.minLength(8)]);
    this.userForm.controls.password.updateValueAndValidity();
    this.drawerOpen.set(true);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected openPermissionsDrawer(user: MergedAdminUser): void {
    if (!this.canManagePermissions() || !user.localUserId) {
      return;
    }

    this.selectedPermissionUser.set(user);
    this.selectedGrantPermission.set(ALL_PERMISSIONS[0]);
    this.selectedRevokePermission.set(ALL_PERMISSIONS[0]);
    this.permissionDrawerOpen.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.adminApi.getUserPermissions(user.localUserId).subscribe({
      next: (permissions) => {
        this.applyPermissionUpdate(permissions);
      },
      error: (error) => {
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
      this.errorMessage.set('Cette action requiert MANAGE_USERS.');
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.userForm.markAllAsTouched();

    if (this.userForm.invalid) {
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
        role: rawValue.role,
        enabled: rawValue.enabled
      };

      this.adminApi.createUser(payload).subscribe({
        next: (createdUser) => {
          this.rebuildUsers([createdUser, ...this.users()]);
          this.successMessage.set(`Utilisateur ${createdUser.username} cree avec succes.`);
          this.isSaving.set(false);
          this.drawerOpen.set(false);
        },
        error: (error) => {
          this.errorMessage.set(extractApiErrorMessage(error, 'Unable to create user.'));
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
      role: rawValue.role,
      enabled: rawValue.enabled
    };

    if (rawValue.password.trim()) {
      payload.password = rawValue.password.trim();
    }

    this.adminApi.updateUser(userId, payload).subscribe({
      next: (updatedUser) => {
        this.replaceAuthUser(updatedUser);
        this.successMessage.set(`Utilisateur ${updatedUser.username} mis a jour avec succes.`);
        this.isSaving.set(false);
        this.drawerOpen.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to update user.'));
        this.isSaving.set(false);
      }
    });
  }

  protected toggleUserStatus(user: MergedAdminUser): void {
    if (!this.canManageUsers()) {
      this.errorMessage.set('Cette action requiert MANAGE_USERS.');
      return;
    }

    this.successMessage.set(null);
    this.errorMessage.set(null);

    this.adminApi.updateUserStatus(user.id, !user.enabled).subscribe({
      next: (updatedUser) => {
        this.replaceAuthUser(updatedUser);
        this.successMessage.set(
          `Utilisateur ${updatedUser.username} ${updatedUser.enabled ? 'active' : 'desactive'} avec succes.`
        );
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to update user status.'));
      }
    });
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
        this.successMessage.set(`Permission ${this.selectedGrantPermission()} ajoutee a ${user.username}.`);
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
        this.successMessage.set(`Permission ${this.selectedRevokePermission()} retiree a ${user.username}.`);
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
        this.successMessage.set(`Ajout personnalise ${permission} annule pour ${user.username}.`);
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
        this.successMessage.set(`Retrait personnalise ${permission} annule pour ${user.username}.`);
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

    this.adminApi.getUsers().subscribe({
      next: (users) => {
        authUsers = users;
        tryFinalize();
      },
      error: (error) => {
        this.errorMessage.set(extractApiErrorMessage(error, 'Unable to load admin users.'));
        this.isLoading.set(false);
      }
    });

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

  private mergeUser(authUser: AdminUser, localUser: LocalAdminUserView | null): MergedAdminUser {
    return {
      ...authUser,
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

  private nullableValue(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }
}
