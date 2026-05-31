export interface AdminUser {
  id: string;
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;

  phone: string | null;
  address: string | null;
  city: string | null;
  zipCode: string | null;

  position: string | null;

  enabled: boolean;
  connected: boolean;
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
  phone: string | null;
  position: string | null;
  role: string;
  enabled: boolean;
  address?: string | null;
  city?: string | null;
  zipCode?: string | null;
}

export interface AdminUpdateUserPayload {
  username: string;
  email: string;
  password?: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  position: string | null;
  role: string;
  enabled: boolean;
  address?: string | null;
  city?: string | null;
  zipCode?: string | null;
}

export interface LocalAdminUserView {
  id: number;
  username: string;
  email: string;
  role: string;
  enabled?: boolean | null;
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

export interface SyncLocalUserPayload {
  username: string;
  email: string;
  role: string;
}

export interface SyncAllLocalUsersResponse {
  totalKeycloakUsers: number;
  synchronizedUsers: number;
  createdUsers: number;
  updatedUsers: number;
  skippedUsers: number;
  failedUsers: number;
}

export interface MergedAdminUser extends AdminUser {
  localUserId: number | null;
  localRole: string | null;
  rolePermissions: string[];
  extraPermissions: string[];
  revokedPermissions: string[];
  effectivePermissions: string[];
}
