export type VisibilityRule = 'ALL' | 'SUPERADMIN';

export interface NavItem {
  label: string;
  route?: string;
  icon?: string;
  visibility?: VisibilityRule;
  children?: NavItem[];
}

export const SIDEBAR_NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', route: '/dashboard', icon: 'dashboard', visibility: 'ALL' },
  {
    label: 'Monitoring',
    icon: 'monitoring',
    visibility: 'ALL',
    children: [
      { label: 'Zabbix', route: '/monitoring/zabbix', visibility: 'ALL' },
      { label: 'Observium', route: '/monitoring/observium', visibility: 'ALL' },
      { label: 'Camera', route: '/monitoring/camera', visibility: 'ALL' },
      { label: 'ZKBio', route: '/monitoring/zkbio', visibility: 'ALL' },
      { label: 'Access Point', route: '/monitoring/access-point', visibility: 'ALL' }
    ]
  },
  { label: 'Equipment Management', route: '/equipment', icon: 'equipment', visibility: 'ALL' },
  {
    label: 'Tickets',
    icon: 'tickets',
    visibility: 'ALL',
    children: [
      { label: 'List', route: '/tickets/list', visibility: 'ALL' },
      { label: 'Add', route: '/tickets/add', visibility: 'ALL' },
      { label: 'Tracking', route: '/tickets/tracking', visibility: 'ALL' }
    ]
  },
  { label: 'Users', route: '/users', icon: 'users', visibility: 'SUPERADMIN' }
];
