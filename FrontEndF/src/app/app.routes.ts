import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { MonitoringCameraPageComponent } from './features/monitoring/ui/monitoring-camera-page.component';
import { MonitoringDashboardPageComponent } from './features/monitoring/ui/monitoring-dashboard-page.component';
import { MonitoringSnmpPageComponent } from './features/monitoring/ui/monitoring-snmp-page.component';
import { MonitoringZabbixPageComponent } from './features/monitoring/ui/monitoring-zabbix-page.component';
import { MonitoringZkBioPageComponent } from './features/monitoring/ui/monitoring-zkbio-page.component';
import { EquipmentManagementPageComponent } from './features/monitoring/ui/equipment-management-page.component';
import { TicketTrackingPageComponent } from './features/tickets/ui/ticket-tracking-page.component';
import { TicketListPageComponent } from './features/tickets/ui/ticket-list-page.component';
import { TicketAddPageComponent } from './features/tickets/ui/ticket-add-page.component';
import { PlaceholderPageComponent } from './shared/pages/placeholder-page.component';
import { LoginPageComponent } from './features/auth/ui/login-page.component';
import { EditProfilePageComponent } from './features/auth/ui/edit-profile-page.component';
import { AuthGuard } from './core/auth/auth.guard';
import { RoleGuard } from './core/auth/role.guard';
import { UserManagementPageComponent } from './features/users/ui/user-management-page.component';
import { AdminPanelPageComponent } from './features/admin/ui/admin-panel-page.component';
import { NotificationCenterPageComponent } from './features/notifications/ui/notification-center-page.component';

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
        data: { permissionsAny: ['VIEW_DASHBOARD'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/zabbix',
        component: MonitoringZabbixPageComponent,
        data: { title: 'Zabbix', permissionsAny: ['VIEW_ZABBIX'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/snmp',
        component: MonitoringSnmpPageComponent,
        data: { title: 'SNMP', permissionsAny: ['VIEW_SNMP'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/camera',
        component: MonitoringCameraPageComponent,
        data: { title: 'Camera', permissionsAny: ['VIEW_CAMERA'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/zkbio',
        component: MonitoringZkBioPageComponent,
        data: { title: 'ZKBio', permissionsAny: ['VIEW_ZKBIO'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'monitoring/access-point',
        component: PlaceholderPageComponent,
        data: { title: 'Access Point', permissionsAny: ['VIEW_ACCESS_POINT'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'equipment',
        component: EquipmentManagementPageComponent,
        data: { title: 'Equipment Management', permissionsAny: ['VIEW_HOSTS'] },
        canActivate: [RoleGuard]
      },
      { path: 'profile', component: EditProfilePageComponent, data: { title: 'Edit Profile' } },
      { path: 'tickets', pathMatch: 'full', redirectTo: 'tickets/list' },
      {
        path: 'tickets/list',
        component: TicketListPageComponent,
        data: { title: 'Liste des tickets', permissionsAny: ['VIEW_TICKETS'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'tickets/add',
        component: TicketAddPageComponent,
        data: { title: 'Creer un ticket', permissionsAny: ['CREATE_TICKET'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'tickets/tracking',
        component: TicketTrackingPageComponent,
        data: { title: 'Tickets', permissionsAny: ['VIEW_TICKETS'] },
        canActivate: [RoleGuard]
      },
      // UX-only role hints. Backend authorization remains the source of truth.
      {
        path: 'users',
        component: UserManagementPageComponent,
        data: { title: 'Users', permissionsAny: ['VIEW_USERS'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'admin',
        component: AdminPanelPageComponent,
        data: { title: 'Admin Panel', permissionsAny: ['VIEW_USERS'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'notifications',
        component: NotificationCenterPageComponent,
        data: { title: 'Notifications', permissionsAny: ['VIEW_TICKETS'] },
        canActivate: [RoleGuard]
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('./features/chat/ui/chat-room-page.component').then((m) => m.ChatRoomPageComponent),
        data: { title: 'Incident Chat', permissionsAny: ['VIEW_TICKETS'] },
        canActivate: [RoleGuard]
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];

