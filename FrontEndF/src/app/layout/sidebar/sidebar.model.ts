export type VisibilityRule = 'ALL' | 'ADMIN_AREA';

export interface NavItem {
  label: string;
  route?: string;
  icon?: string;
  section?: string;
  visibility?: VisibilityRule;
  requiredAnyPermissions?: string[];
  children?: NavItem[];
}

export const SIDEBAR_NAV_ITEMS: NavItem[] = [
  {
    label: 'Dashboard',
    route: '/dashboard',
    icon: 'dashboard',
    section: 'Overview',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_DASHBOARD']
  },
  {
    label: 'Monitoring',
    icon: 'monitoring',
    section: 'Monitoring',
    visibility: 'ALL',
    children: [
      { label: 'Zabbix', route: '/monitoring/zabbix', visibility: 'ALL', requiredAnyPermissions: ['VIEW_ZABBIX'] },
      { label: 'Observium', route: '/monitoring/observium', visibility: 'ALL', requiredAnyPermissions: ['VIEW_OBSERVIUM'] },
      { label: 'Camera', route: '/monitoring/camera', visibility: 'ALL', requiredAnyPermissions: ['VIEW_CAMERA'] },
      { label: 'ZKBio', route: '/monitoring/zkbio', visibility: 'ALL', requiredAnyPermissions: ['VIEW_ZKBIO'] },
      { label: 'Access Point', route: '/monitoring/access-point', visibility: 'ALL', requiredAnyPermissions: ['VIEW_ACCESS_POINT'] }
    ]
  },
  {
    label: 'Equipment Management',
    route: '/equipment',
    icon: 'equipment',
    section: 'Operations',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_HOSTS']
  },
  {
    label: 'Tickets',
    route: '/tickets/list',
    icon: 'tickets',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_TICKETS']
  },
  {
    label: 'Incident Chat',
    route: '/chat',
    icon: 'chat',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_TICKETS']
  },
  {
    label: 'Users',
    route: '/users',
    icon: 'users',
    section: 'Administration',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_USERS']
  },
  {
    label: 'Admin Panel',
    route: '/admin',
    icon: 'admin',
    visibility: 'ALL',
    requiredAnyPermissions: ['VIEW_USERS']
  }
];

