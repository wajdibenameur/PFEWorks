import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import {
  findSourceAvailability,
  matchesMonitoringSource
} from '../data/monitoring-source.utils';

type ObserviumCategory = 'PRINTER' | 'SERVER' | 'SWITCH' | 'ACCESS_POINT' | 'OTHER';
type ObserviumCoverage = 'native' | 'synthetic' | 'not_applicable' | 'unknown';

interface ObserviumCategoryGroup {
  category: ObserviumCategory;
  total: number;
  down: number;
  up: number;
  hosts: MonitoringHost[];
}

interface ObserviumMetricGroup {
  key: string;
  label: string;
  sampleCount: number;
  latestValue: number | null;
  latestTimestamp: number | null;
  hosts: number;
}

@Component({
  selector: 'app-monitoring-observium-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-observium-page.component.html',
  styleUrls: ['./monitoring-observium-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringObserviumPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;
  private loadGeneration = 0;
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
  readonly metrics = signal<UnifiedMonitoringMetric[]>([]);
  readonly sourceAvailability = signal<SourceAvailability | null>(null);
  readonly hostsFreshness = signal('snapshot_missing');
  readonly problemsFreshness = signal('snapshot_missing');
  readonly metricsFreshness = signal('snapshot_missing');
  readonly metricsCoverage = signal<ObserviumCoverage>('unknown');
  readonly monitoringDegraded = signal(false);

  readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect Observium', target: 'observium' }
  ];

  getFreshnessLabel(freshness: string | null | undefined): string {
    if (!freshness) {
      return 'Unknown';
    }
    switch (freshness.toLowerCase()) {
      case 'live':
        return 'Live data';
      case 'database_snapshot_fallback':
        return 'Database snapshot fallback';
      case 'memory_snapshot_fallback':
        return 'Memory snapshot fallback';
      case 'redis_fallback':
        return 'Redis snapshot fallback';
      case 'snapshot_missing':
        return 'No snapshot available';
      case 'persisted':
        return 'Persisted data';
      default:
        return freshness;
    }
  }

  private shouldMarkLive(currentFreshness: string): boolean {
    return (
      currentFreshness !== 'database_snapshot_fallback' &&
      currentFreshness !== 'memory_snapshot_fallback' &&
      currentFreshness !== 'snapshot_missing' &&
      currentFreshness !== 'redis_fallback'
    );
  }

  readonly kpi = computed(() => ({
    totalHosts: this.hosts().length,
    downHosts: this.hosts().filter((host) => (host.status ?? '').toUpperCase() === 'DOWN').length,
    activeAlerts: this.problems().filter((problem) => problem.active).length,
    totalMetrics: this.metrics().length
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

  readonly metricGroups = computed<ObserviumMetricGroup[]>(() => {
    const grouped = new Map<string, UnifiedMonitoringMetric[]>();

    for (const metric of this.metrics()) {
      const bucket = grouped.get(metric.metricKey) ?? [];
      bucket.push(metric);
      grouped.set(metric.metricKey, bucket);
    }

    return Array.from(grouped.entries())
      .map(([metricKey, metrics]) => {
        const sorted = [...metrics].sort((left, right) => (right.timestamp ?? 0) - (left.timestamp ?? 0));
        const latest = sorted[0] ?? null;
        const hosts = new Set(metrics.map((metric) => metric.hostId || metric.hostName || metric.id));

        return {
          key: metricKey,
          label: this.metricLabel(metricKey),
          sampleCount: metrics.length,
          latestValue: latest?.value ?? null,
          latestTimestamp: latest?.timestamp ?? null,
          hosts: hosts.size
        };
      })
      .sort((left, right) => (right.latestTimestamp ?? 0) - (left.latestTimestamp ?? 0));
  });

  constructor(
    private readonly api: MonitoringApiService,
    private readonly realtime: MonitoringRealtimeService
  ) {
    this.destroyRef.onDestroy(() => this.clearScheduledRefreshes());
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
        this.scheduleSnapshotRefresh();
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Unable to trigger Observium collection.')
        );
      }
    });
  }

  private loadSnapshot(): void {
    const currentLoadGeneration = ++this.loadGeneration;
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      hostsResponse: this.api.getMonitoringHostsResponse().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load monitoring hosts.')
          );
          return of({ data: [] as MonitoringHost[], degraded: true, freshness: {}, coverage: {} });
        })
      ),
      problemsResponse: this.api.getMonitoringProblemsResponse().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load monitoring problems.')
          );
          return of({ data: [] as MonitoringProblem[], degraded: true, freshness: {}, coverage: {} });
        })
      ),
      metricsResponse: this.api.getMonitoringMetricsResponse().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load monitoring metrics.')
          );
          return of({ data: [] as UnifiedMonitoringMetric[], degraded: true, freshness: {}, coverage: {} });
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
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ hostsResponse, problemsResponse, metricsResponse, sourceHealth }) => {
        if (currentLoadGeneration !== this.loadGeneration) {
          return;
        }

        this.hosts.set(
          hostsResponse.data.filter((host) => matchesMonitoringSource(host.source, 'OBSERVIUM'))
        );
        this.problems.set(
          problemsResponse.data.filter((problem) => matchesMonitoringSource(problem.source, 'OBSERVIUM'))
        );
        this.metrics.set(
          metricsResponse.data.filter((metric) => matchesMonitoringSource(metric.source, 'OBSERVIUM'))
        );
        this.sourceAvailability.set(findSourceAvailability(sourceHealth, 'OBSERVIUM'));
        this.hostsFreshness.set(this.readMetadataValue(hostsResponse.freshness));
        this.problemsFreshness.set(this.readMetadataValue(problemsResponse.freshness));
        this.metricsFreshness.set(this.readMetadataValue(metricsResponse.freshness));
        this.metricsCoverage.set(this.readCoverageValue(metricsResponse.coverage));
        this.monitoringDegraded.set(
          hostsResponse.degraded || problemsResponse.degraded || metricsResponse.degraded
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

    this.realtime.monitoringProblemsForSource$('OBSERVIUM')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (observiumProblems) => {
        if (observiumProblems.length === 0) {
          return;
        }

        this.problems.set(this.mergeProblems(this.problems(), observiumProblems));
        if (this.shouldMarkLive(this.problemsFreshness())) {
          this.problemsFreshness.set('live');
        }
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Observium realtime problem stream is unavailable.')
        );
      }
    });

    this.realtime.monitoringMetricsForSource$('OBSERVIUM')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (observiumMetrics) => {
        if (observiumMetrics.length === 0) {
          return;
        }

        this.metrics.set(this.mergeMetrics(this.metrics(), observiumMetrics));
        if (this.shouldMarkLive(this.metricsFreshness())) {
          this.metricsFreshness.set('live');
        }
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Observium realtime metrics stream is unavailable.')
        );
      }
    });

    this.realtime.monitoringSourceHealth$('OBSERVIUM').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
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

  private mergeMetrics(existing: UnifiedMonitoringMetric[], incoming: UnifiedMonitoringMetric[]): UnifiedMonitoringMetric[] {
    const map = new Map<string, UnifiedMonitoringMetric>();

    for (const metric of existing) {
      map.set(this.metricKey(metric), metric);
    }

    for (const metric of incoming) {
      map.set(this.metricKey(metric), metric);
    }

    return Array.from(map.values());
  }

  private problemKey(problem: MonitoringProblem): string {
    return problem.problemId ?? problem.id ?? String(problem.eventId ?? problem.description ?? 'UNKNOWN');
  }

  private metricKey(metric: UnifiedMonitoringMetric): string {
    return `${metric.hostId}:${metric.itemId}:${metric.timestamp}`;
  }

  private hostLabel(host: MonitoringHost): string {
    return host.name ?? host.hostId ?? host.id;
  }

  hostTrackBy(_: number, host: MonitoringHost): string {
    return host.id;
  }

  metricTrackBy(_: number, metric: ObserviumMetricGroup): string {
    return metric.key;
  }

  hostAddress(host: MonitoringHost): string {
    return host.ip ?? 'IP_UNKNOWN';
  }

  hostCategory(host: MonitoringHost): ObserviumCategory {
    return this.normalizeCategory(host.category);
  }

  metricValue(metric: ObserviumMetricGroup): string {
    if (metric.latestValue == null) {
      return 'N/A';
    }
    return Number.isInteger(metric.latestValue)
      ? String(metric.latestValue)
      : metric.latestValue.toFixed(2);
  }

  metricTimestamp(metric: ObserviumMetricGroup): string {
    if (!metric.latestTimestamp) {
      return 'Unknown timestamp';
    }
    return new Date(metric.latestTimestamp * 1000).toLocaleString();
  }

  coverageLabel(): string {
    switch (this.metricsCoverage()) {
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

  private metricLabel(metricKey: string): string {
    return metricKey
      .replace(/[\[\]\._]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private readMetadataValue(values: Record<string, string> | null | undefined): string {
    return values?.['OBSERVIUM'] ?? 'snapshot_missing';
  }

  private readCoverageValue(
    values: Record<string, string> | null | undefined
  ): ObserviumCoverage {
    const coverage = (values?.['OBSERVIUM'] ?? '').toLowerCase();
    if (coverage === 'native' || coverage === 'synthetic' || coverage === 'not_applicable') {
      return coverage;
    }
    return 'unknown';
  }

  private scheduleSnapshotRefresh(): void {
    this.clearScheduledRefreshes();

    for (const delay of [1500, 5000]) {
      const timeoutId = window.setTimeout(() => this.loadSnapshot(), delay);
      this.refreshTimeoutIds.push(timeoutId);
    }
  }

  private clearScheduledRefreshes(): void {
    while (this.refreshTimeoutIds.length > 0) {
      const timeoutId = this.refreshTimeoutIds.pop();
      if (timeoutId != null) {
        window.clearTimeout(timeoutId);
      }
    }
  }
}


