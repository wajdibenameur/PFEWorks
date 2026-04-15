import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CollectionTarget } from '../../../core/models/collection-target.model';

export interface CollectionActionVm {
  label: string;
  target: CollectionTarget;
}

@Component({
  selector: 'app-collection-control-bar',
  imports: [CommonModule, DatePipe],
  templateUrl: './collection-control-bar.component.html',
  styleUrl: './collection-control-bar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CollectionControlBarComponent {
  readonly actions = input.required<CollectionActionVm[]>();
  readonly isLoading = input<boolean>(false);
  readonly lastRefresh = input<Date | null>(null);
  readonly collectClicked = output<CollectionTarget>();
  readonly refreshClicked = output<void>();
}
