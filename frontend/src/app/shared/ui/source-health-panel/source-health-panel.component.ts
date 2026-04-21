import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type SourceCoverage = 'native' | 'synthetic' | 'not_applicable' | 'unknown';

export interface SourceHealthVm {
  source: string;
  total: number | null;
  down: number | null;
  coverage: SourceCoverage;
  availability: 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN';
  note: string;
}

@Component({
  selector: 'app-source-health-panel',
  imports: [CommonModule],
  templateUrl: './source-health-panel.component.html',
  styleUrl: './source-health-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SourceHealthPanelComponent {
  readonly items = input.required<SourceHealthVm[]>();

  coverageLabel(coverage: SourceCoverage): string {
    switch (coverage) {
      case 'native':
        return 'Native metrics';
      case 'synthetic':
        return 'Synthetic metrics';
      case 'not_applicable':
        return 'Hosts only';
      default:
        return 'Unknown coverage';
    }
  }
}
