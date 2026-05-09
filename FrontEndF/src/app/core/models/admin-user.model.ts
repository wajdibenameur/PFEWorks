export interface AdminUser {
  id: string;
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  roles: string[];
}

export interface AdminRole {
  name: string;
  label: string;
  description: string;
}

export interface AdminCreateUserPayload {
  username: string;
  email: string;
  password: string;
  firstName: string | null;
  lastName: string | null;
  role: string;
  enabled: boolean;
}

export interface AdminUpdateUserPayload {
  username: string;
  email: string;
  password?: string;
  firstName: string | null;
  lastName: string | null;
  role: string;
  enabled: boolean;
}

export interface LocalAdminUserView {
  id: number;
  username: string;
  email: string;
  role: string;
  rolePermissions: string[];
  extraPermissions: string[];
  revokedPermissions: string[];
  effectivePermissions: string[];
}

export interface UserPermissionsView {
  userId: number;
  username: string;
  role: string;
  rolePermissions: string[];
  extraPermissions: string[];
  revokedPermissions: string[];
  effectivePermissions: string[];
}

export interface MergedAdminUser extends AdminUser {
  localUserId: number | null;
  localRole: string | null;
  rolePermissions: string[];
  extraPermissions: string[];
  revokedPermissions: string[];
  effectivePermissions: string[];
}
