import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface GlobalKpiVm {
  totalMonitoredAssets: number;
  totalDownAssets: number;
  serversTotal: number;
  serversDown: number;
  printersTotal: number;
  printersDown: number;
}

@Component({
  selector: 'app-global-kpi-strip',
  imports: [CommonModule],
  templateUrl: './global-kpi-strip.component.html',
  styleUrl: './global-kpi-strip.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GlobalKpiStripComponent {
  readonly kpi = input.required<GlobalKpiVm>();
}
