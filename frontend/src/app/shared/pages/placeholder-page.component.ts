import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-placeholder-page',
  imports: [CommonModule],
  templateUrl: './placeholder-page.component.html',
  styleUrl: './placeholder-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlaceholderPageComponent {
  readonly title = input<string>('Coming soon');
  readonly description = input<string>('This section is planned but not implemented yet.');
  private readonly route = inject(ActivatedRoute);
  readonly routeTitle = toSignal(
    this.route.data.pipe(map((data) => data['title'] as string | undefined))
  );
}
