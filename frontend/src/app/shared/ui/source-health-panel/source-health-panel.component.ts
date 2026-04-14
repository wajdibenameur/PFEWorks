import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type SourceCoverage = 'REAL' | 'MISSING_BACKEND_READ_ENDPOINT';

export interface SourceHealthVm {
  source: string;
  total: number | null;
  down: number | null;
  coverage: SourceCoverage;
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
}
