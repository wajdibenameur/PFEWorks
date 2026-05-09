import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { MonitoringCameraPageComponent } from './features/monitoring/ui/monitoring-camera-page.component';
import { MonitoringDashboardPageComponent } from './features/monitoring/ui/monitoring-dashboard-page.component';
import { MonitoringObserviumPageComponent } from './features/monitoring/ui/monitoring-observium-page.component';
import { MonitoringZabbixPageComponent } from './features/monitoring/ui/monitoring-zabbix-page.component';
import { MonitoringZkBioPageComponent } from './features/monitoring/ui/monitoring-zkbio-page.component';
import { TicketAddPageComponent } from './features/tickets/ui/ticket-add-page.component';
import { TicketListPageComponent } from './features/tickets/ui/ticket-list-page.component';
import { TicketTrackingPageComponent } from './features/tickets/ui/ticket-tracking-page.component';
import { PlaceholderPageComponent } from './shared/pages/placeholder-page.component';
import { LoginPageComponent } from './features/auth/ui/login-page.component';
import { EditProfilePageComponent } from './features/auth/ui/edit-profile-page.component';
import { AuthGuard } from './core/auth/auth.guard';
import { RoleGuard } from './core/auth/role.guard';
import { UserManagementPageComponent } from './features/users/ui/user-management-page.component';
import { AdminPanelPageComponent } from './features/admin/ui/admin-panel-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        component: MonitoringDashboardPageComponent,
        data: { permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/zabbix',
        component: MonitoringZabbixPageComponent,
        data: { title: 'Zabbix', permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/observium',
        component: MonitoringObserviumPageComponent,
        data: { title: 'Observium', permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/camera',
        component: MonitoringCameraPageComponent,
        data: { title: 'Camera', permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/zkbio',
        component: MonitoringZkBioPageComponent,
        data: { title: 'ZKBio', permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/access-point',
        component: PlaceholderPageComponent,
        data: { title: 'Access Point', permissionsAny: ['VIEW_DASHBOARD'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'equipment',
        component: PlaceholderPageComponent,
        data: { title: 'Equipment Management', permissionsAny: ['VIEW_HOSTS'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      { path: 'profile', component: EditProfilePageComponent, data: { title: 'Edit Profile' } },
      {
        path: 'tickets/list',
        component: TicketListPageComponent,
        data: { title: 'Tickets - List', permissionsAny: ['VIEW_TICKETS'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'tickets/add',
        component: TicketAddPageComponent,
        data: { title: 'Tickets - Add', permissionsAny: ['CREATE_TICKET'], roles: ['superadmin', 'admin', 'support'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'tickets/tracking',
        component: TicketTrackingPageComponent,
        data: { title: 'Tickets - Tracking', permissionsAny: ['VIEW_TICKETS'], roles: ['superadmin', 'admin', 'support', 'viewer'] },
        canActivate: [RoleGuard]
      },
      // UX-only role hints. Backend authorization remains the source of truth.
      {
        path: 'users',
        component: UserManagementPageComponent,
        data: { title: 'Users', permissionsAny: ['VIEW_USERS'], roles: ['superadmin', 'admin'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'admin',
        component: AdminPanelPageComponent,
        data: { title: 'Admin Panel', permissionsAny: ['VIEW_USERS'], roles: ['superadmin', 'admin'] },
        canActivate: [RoleGuard]
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];

