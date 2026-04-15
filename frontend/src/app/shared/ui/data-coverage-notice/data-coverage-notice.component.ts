import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type CoverageStatus = 'REAL' | 'PARTIAL' | 'ESTIMATED';

export interface DataCoverageVm {
  title: string;
  status: CoverageStatus;
  detail: string;
}

@Component({
  selector: 'app-data-coverage-notice',
  imports: [CommonModule],
  templateUrl: './data-coverage-notice.component.html',
  styleUrl: './data-coverage-notice.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DataCoverageNoticeComponent {
  readonly items = input.required<DataCoverageVm[]>();
}
