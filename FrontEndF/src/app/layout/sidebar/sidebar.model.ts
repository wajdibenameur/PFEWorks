export type VisibilityRule = 'ALL' | 'ADMIN_AREA';

export interface NavItem {
  label: string;
  route?: string;
  icon?: string;
  section?: string;
  visibility?: VisibilityRule;
  requiredAnyPermissions?: string[];
  fallbackRoles?: string[];
  children?: NavItem[];
}

export const SIDEBAR_NAV_ITEMS: NavItem[] = [
  {
    label: 'Dashboard',
    route: '/dashboard',
    icon: 'dashboard',
    section: 'Overview',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_DASHBOARD'],
    fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER']
  },
  {
    label: 'Monitoring',
    icon: 'monitoring',
    section: 'Monitoring',
    visibility: 'ALL',
    children: [
      { label: 'Zabbix', route: '/monitoring/zabbix', visibility: 'ALL', requiredAnyPermissions: ['VIEW_DASHBOARD'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] },
      { label: 'Observium', route: '/monitoring/observium', visibility: 'ALL', requiredAnyPermissions: ['VIEW_DASHBOARD'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] },
      { label: 'Camera', route: '/monitoring/camera', visibility: 'ALL', requiredAnyPermissions: ['VIEW_DASHBOARD'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] },
      { label: 'ZKBio', route: '/monitoring/zkbio', visibility: 'ALL', requiredAnyPermissions: ['VIEW_DASHBOARD'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] },
      { label: 'Access Point', route: '/monitoring/access-point', visibility: 'ALL', requiredAnyPermissions: ['VIEW_DASHBOARD'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] }
    ]
  },
  {
    label: 'Equipment Management',
    route: '/equipment',
    icon: 'equipment',
    section: 'Operations',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_HOSTS'],
    fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER']
  },
  {
    label: 'Tickets',
    icon: 'tickets',
    visibility: 'ALL',
    children: [
      { label: 'List', route: '/tickets/list', visibility: 'ALL', requiredAnyPermissions: ['VIEW_TICKETS'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] },
      { label: 'Add', route: '/tickets/add', visibility: 'ALL', requiredAnyPermissions: ['CREATE_TICKET'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT'] },
      { label: 'Tracking', route: '/tickets/tracking', visibility: 'ALL', requiredAnyPermissions: ['VIEW_TICKETS'], fallbackRoles: ['SUPERADMIN', 'ADMIN', 'SUPPORT', 'VIEWER'] }
    ]
  },
  {
    label: 'Users',
    route: '/users',
    icon: 'users',
    section: 'Administration',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_USERS'],
    fallbackRoles: ['SUPERADMIN', 'ADMIN']
  },
  {
    label: 'Admin Panel',
    route: '/admin',
    icon: 'admin',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_USERS'],
    fallbackRoles: ['SUPERADMIN', 'ADMIN']
  }
];

