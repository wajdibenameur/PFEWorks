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

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: MonitoringDashboardPageComponent },
      { path: 'monitoring/zabbix', component: MonitoringZabbixPageComponent, data: { title: 'Zabbix' } },
      { path: 'monitoring/observium', component: MonitoringObserviumPageComponent, data: { title: 'Observium' } },
      { path: 'monitoring/camera', component: MonitoringCameraPageComponent, data: { title: 'Camera' } },
      { path: 'monitoring/zkbio', component: MonitoringZkBioPageComponent, data: { title: 'ZKBio' } },
      { path: 'monitoring/access-point', component: PlaceholderPageComponent, data: { title: 'Access Point' } },
      { path: 'equipment', component: PlaceholderPageComponent, data: { title: 'Equipment Management' } },
      { path: 'tickets/list', component: TicketListPageComponent, data: { title: 'Tickets - List' } },
      { path: 'tickets/add', component: TicketAddPageComponent, data: { title: 'Tickets - Add' } },
      { path: 'tickets/tracking', component: TicketTrackingPageComponent, data: { title: 'Tickets - Tracking' } },
      { path: 'users', component: PlaceholderPageComponent, data: { title: 'Users' } }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
