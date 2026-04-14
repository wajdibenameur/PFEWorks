import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { CollectionActionVm, CollectionControlBarComponent } from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { GlobalKpiStripComponent } from '../../../shared/ui/global-kpi-strip/global-kpi-strip.component';
import { SourceHealthPanelComponent } from '../../../shared/ui/source-health-panel/source-health-panel.component';
import { AlertSummaryPanelComponent } from '../../../shared/ui/alert-summary-panel/alert-summary-panel.component';
import { AssetInventoryTableComponent } from '../../../shared/ui/asset-inventory-table/asset-inventory-table.component';
import { DataCoverageNoticeComponent } from '../../../shared/ui/data-coverage-notice/data-coverage-notice.component';
import { MonitoringStore } from '../state/monitoring.store';

@Component({
  selector: 'app-monitoring-dashboard-page',
  imports: [
    CommonModule,
    CollectionControlBarComponent,
    GlobalKpiStripComponent,
    SourceHealthPanelComponent,
    AlertSummaryPanelComponent,
    AssetInventoryTableComponent,
    DataCoverageNoticeComponent
  ],
  templateUrl: './monitoring-dashboard-page.component.html',
  styleUrl: './monitoring-dashboard-page.component.scss',
  providers: [MonitoringStore],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringDashboardPageComponent implements OnInit {
  protected readonly facade = inject(MonitoringStore);

  protected readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect All', target: 'all' },
    { label: 'Collect Zabbix', target: 'zabbix' },
    { label: 'Collect Observium', target: 'observium' },
    { label: 'Collect Camera', target: 'camera' }
  ];

  ngOnInit(): void {
    this.facade.loadSnapshot();
    this.facade.bindRealtime();
  }

  protected triggerCollection(target: CollectionTarget): void {
    this.facade.triggerCollection(target);
  }
}
