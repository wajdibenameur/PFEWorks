import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';

type ObserviumCategory = 'PRINTER' | 'SERVER' | 'SWITCH' | 'ACCESS_POINT' | 'OTHER';

interface ObserviumCategoryGroup {
  category: ObserviumCategory;
  total: number;
  down: number;
  up: number;
  hosts: MonitoringHost[];
}

@Component({
  selector: 'app-monitoring-observium-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-observium-page.component.html',
  styleUrl: './monitoring-observium-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringObserviumPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private realtimeBound = false;
  private readonly categoryOrder: ObserviumCategory[] = [
    'PRINTER',
    'SERVER',
    'SWITCH',
    'ACCESS_POINT',
    'OTHER'
  ];

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);
  readonly hosts = signal<MonitoringHost[]>([]);
  readonly problems = signal<MonitoringProblem[]>([]);
  readonly sourceAvailability = signal<SourceAvailability | null>(null);

  readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect Observium', target: 'observium' }
  ];

  readonly kpi = computed(() => ({
    totalHosts: this.hosts().length,
    downHosts: this.hosts().filter((host) => (host.status ?? '').toUpperCase() === 'DOWN').length,
    activeAlerts: this.problems().filter((problem) => problem.active).length
  }));

  readonly sortedHosts = computed(() =>
    [...this.hosts()].sort((left, right) =>
      this.hostLabel(left).localeCompare(this.hostLabel(right))
    )
  );

  readonly categoryGroups = computed<ObserviumCategoryGroup[]>(() => {
    const groups = new Map<ObserviumCategory, ObserviumCategoryGroup>(
      this.categoryOrder.map((category) => [
        category,
        { category, total: 0, down: 0, up: 0, hosts: [] }
      ])
    );

    for (const host of this.sortedHosts()) {
      const category = this.normalizeCategory(host.category);
      const group = groups.get(category);

      if (!group) {
        continue;
      }

      group.total += 1;

      const status = this.normalizeStatus(host.status);
      if (status === 'DOWN') {
        group.down += 1;
      }
      if (status === 'UP') {
        group.up += 1;
      }

      group.hosts.push(host);
    }

    return this.categoryOrder.map((category) => groups.get(category)!);
  });

  readonly sortedProblems = computed(() =>
    [...this.problems()].sort((left, right) =>
      this.problemTime(right) - this.problemTime(left)
    )
  );

  constructor(
    private readonly api: MonitoringApiService,
    private readonly realtime: MonitoringRealtimeService
  ) {
    this.loadSnapshot();
    this.bindRealtime();
  }

  refresh(): void {
    this.loadSnapshot();
  }

  triggerCollection(target: CollectionTarget): void {
    this.errorMessage.set(null);

    this.api.triggerCollection(target).subscribe({
      next: () => {
        window.setTimeout(() => this.loadSnapshot(), 1500);
        window.setTimeout(() => this.loadSnapshot(), 5000);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Unable to trigger Observium collection.')
        );
      }
    });
  }

  private loadSnapshot(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      hosts: this.api.getMonitoringHosts().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load monitoring hosts.')
          );
          return of<MonitoringHost[]>([]);
        })
      ),
      problems: this.api.getMonitoringProblems().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load monitoring problems.')
          );
          return of<MonitoringProblem[]>([]);
        })
      ),
      sourceHealth: this.api.getSourceHealth().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load source health.')
          );
          return of<SourceAvailability[]>([]);
        })
      )
    }).subscribe({
      next: ({ hosts, problems, sourceHealth }) => {
        this.hosts.set(
          hosts.filter((host) => (host.source ?? '').toUpperCase() === 'OBSERVIUM')
        );
        this.problems.set(
          problems.filter((problem) => (problem.source ?? '').toUpperCase() === 'OBSERVIUM')
        );
        this.sourceAvailability.set(
          sourceHealth.find((entry) => entry.source.toUpperCase() === 'OBSERVIUM') ?? null
        );
        this.lastRefresh.set(new Date());
        this.isLoading.set(false);
      }
    });
  }

  private bindRealtime(): void {
    if (this.realtimeBound) {
      return;
    }
    this.realtimeBound = true;

    this.realtime.monitoringProblems$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        const observiumProblems = incoming.filter(
          (problem) => (problem.source ?? '').toUpperCase() === 'OBSERVIUM'
        );

        if (!observiumProblems.length) {
          return;
        }

        this.problems.set(this.mergeProblems(this.problems(), observiumProblems));
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Observium realtime problem stream is unavailable.')
        );
      }
    });

    this.realtime.sourceAvailability$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        if (incoming.source.toUpperCase() !== 'OBSERVIUM') {
          return;
        }
        this.sourceAvailability.set(incoming);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Observium source health stream is unavailable.')
        );
      }
    });
  }

  private mergeProblems(existing: MonitoringProblem[], incoming: MonitoringProblem[]): MonitoringProblem[] {
    const map = new Map<string, MonitoringProblem>();

    for (const problem of existing) {
      map.set(this.problemKey(problem), problem);
    }

    for (const problem of incoming) {
      map.set(this.problemKey(problem), problem);
    }

    return Array.from(map.values());
  }

  private problemKey(problem: MonitoringProblem): string {
    return problem.problemId ?? problem.id ?? String(problem.eventId ?? problem.description ?? 'UNKNOWN');
  }

  private hostLabel(host: MonitoringHost): string {
    return host.name ?? host.hostId ?? host.id;
  }

  hostTrackBy(_: number, host: MonitoringHost): string {
    return host.id;
  }

  hostAddress(host: MonitoringHost): string {
    return host.ip ?? 'IP_UNKNOWN';
  }

  hostCategory(host: MonitoringHost): ObserviumCategory {
    return this.normalizeCategory(host.category);
  }

  private normalizeCategory(category: string | null | undefined): ObserviumCategory {
    const normalized = (category ?? '').toUpperCase();
    if (this.categoryOrder.includes(normalized as ObserviumCategory)) {
      return normalized as ObserviumCategory;
    }
    return 'OTHER';
  }

  private normalizeStatus(status: string | null | undefined): string {
    return (status ?? '').toUpperCase();
  }

  private problemTime(problem: MonitoringProblem): number {
    return problem.startedAt ?? problem.eventId ?? 0;
  }
}
