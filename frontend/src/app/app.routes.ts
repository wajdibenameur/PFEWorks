import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { MonitoringDashboardPageComponent } from './features/monitoring/ui/monitoring-dashboard-page.component';
import { MonitoringObserviumPageComponent } from './features/monitoring/ui/monitoring-observium-page.component';
import { MonitoringZabbixPageComponent } from './features/monitoring/ui/monitoring-zabbix-page.component';
import { MonitoringZkBioPageComponent } from './features/monitoring/ui/monitoring-zkbio-page.component';
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
      { path: 'monitoring/camera', component: PlaceholderPageComponent, data: { title: 'Camera' } },
      { path: 'monitoring/zkbio', component: MonitoringZkBioPageComponent, data: { title: 'ZKBio' } },
      { path: 'monitoring/access-point', component: PlaceholderPageComponent, data: { title: 'Access Point' } },
      { path: 'equipment', component: PlaceholderPageComponent, data: { title: 'Equipment Management' } },
      { path: 'tickets/list', component: PlaceholderPageComponent, data: { title: 'Tickets - List' } },
      { path: 'tickets/add', component: PlaceholderPageComponent, data: { title: 'Tickets - Add' } },
      { path: 'tickets/tracking', component: PlaceholderPageComponent, data: { title: 'Tickets - Tracking' } },
      { path: 'users', component: PlaceholderPageComponent, data: { title: 'Users' } }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
