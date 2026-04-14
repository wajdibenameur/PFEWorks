import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface ProblemSummaryVm {
  totalAlerts: number;
  critical: number;
  high: number;
  medium: number;
  warning: number;
  info: number;
}

export interface AlertHostVm {
  id: string;
  hostname: string;
  problemCount: number;
}

@Component({
  selector: 'app-alert-summary-panel',
  imports: [CommonModule],
  templateUrl: './alert-summary-panel.component.html',
  styleUrl: './alert-summary-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertSummaryPanelComponent {
  readonly summary = input.required<ProblemSummaryVm>();
  readonly topAlertHosts = input.required<AlertHostVm[]>();
}
