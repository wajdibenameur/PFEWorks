import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import {
  AssetCategory,
  DataCoverageVm,
  GlobalAssetVm,
  GlobalKpiVm,
  MonitoringSource,
  ProblemSummaryVm,
  SourceHealthVm
} from './global-monitoring.models';

interface HostAccumulator {
  hostId: string | null;
  hostname: string;
  ip: string | null;
  port: number | null;
  lastCheck: string | null;
  source: MonitoringSource;
  category: string | null;
  status: string | null;
  metricKeys: Set<string>;
  problemCount: number;
  lastMetricTimestamp: number | null;
}

type DatasetMetadata = Record<string, string>;
type DatasetKind = 'hosts' | 'problems' | 'metrics';

@Injectable()
export class MonitoringStore {
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);

  readonly hosts = signal<MonitoringHost[]>([]);
  readonly problems = signal<MonitoringProblem[]>([]);
  readonly metrics = signal<UnifiedMonitoringMetric[]>([]);
  readonly sourceAvailability = signal<SourceAvailability[]>([]);
  readonly hostsFreshness = signal<Record<string, string>>({});
  readonly problemsFreshness = signal<Record<string, string>>({});
  readonly metricsFreshness = signal<Record<string, string>>({});
  readonly hostsCoverage = signal<Record<string, string>>({});
  readonly problemsCoverage = signal<Record<string, string>>({});
  readonly metricsCoverage = signal<Record<string, string>>({});
  readonly unifiedDegraded = signal(false);

  readonly assets = computed<GlobalAssetVm[]>(() =>
    this.buildAssets(this.hosts(), this.problems(), this.metrics())
  );

  readonly kpi = computed<GlobalKpiVm>(() => {
    const assets = this.assets();
    const downAssets = assets.filter((asset) => asset.status === 'DOWN');
    const servers = assets.filter((asset) => asset.category === 'SERVER');
    const printers = assets.filter((asset) => asset.category === 'PRINTER');

    return {
      totalMonitoredAssets: assets.length,
      totalDownAssets: downAssets.length,
      serversTotal: servers.length,
      serversDown: servers.filter((asset) => asset.status === 'DOWN').length,
      printersTotal: printers.length,
      printersDown: printers.filter((asset) => asset.status === 'DOWN').length
    };
  });

  readonly problemSummary = computed<ProblemSummaryVm>(() => {
    const problems = this.problems();
    const severeProblems = problems.filter((problem) => this.isDashboardProblem(problem));
    return {
      totalAlerts: severeProblems.length,
      critical: severeProblems.filter((problem) => this.severityRank(problem.severity) >= 5).length,
      high: severeProblems.filter((problem) => this.severityRank(problem.severity) === 4).length,
      medium: problems.filter((problem) => this.severityRank(problem.severity) === 3).length,
      warning: problems.filter((problem) => this.severityRank(problem.severity) === 2).length,
      info: problems.filter((problem) => this.severityRank(problem.severity) <= 1).length
    };
  });

  readonly sourceHealth = computed<SourceHealthVm[]>(() => {
    const availabilityMap = new Map(
      this.sourceAvailability().map((entry) => [entry.source.toUpperCase(), entry])
    );
    const assetsBySource = new Map<MonitoringSource, GlobalAssetVm[]>(
      (['ZABBIX', 'OBSERVIUM', 'CAMERA', 'ZKBIO'] as MonitoringSource[]).map((source) => [
        source,
        this.assets().filter((asset) => asset.source === source)
      ])
    );

    return (['ZABBIX', 'OBSERVIUM', 'CAMERA', 'ZKBIO'] as MonitoringSource[]).map((source) => {
      const availability = availabilityMap.get(source);
      const assets = assetsBySource.get(source) ?? [];
      const coverage = this.readSourceCoverage(source);
      const availabilityStatus = this.mapAvailability(availability);
      const noteParts = [
        `Hosts: ${this.readDatasetFreshness('hosts', source)}`,
        `Problems: ${this.readDatasetFreshness('problems', source)}`,
        source === 'CAMERA'
          ? null
          : `Metrics: ${
              coverage === 'not_applicable'
                ? 'not_applicable'
                : this.readDatasetFreshness('metrics', source)
            }`
      ].filter((value): value is string => Boolean(value));

      if (availability?.lastError) {
        noteParts.push(`Last error: ${availability.lastError}`);
      }
      if (availabilityStatus === 'DEGRADED') {
        noteParts.push('Serving the latest snapshot fallback while the live source is degraded.');
      }
      if (source === 'CAMERA') {
        noteParts.push('Camera currently contributes only to the unified hosts inventory.');
      }

      return {
        source,
        total: assets.length,
        down: assets.filter((asset) => asset.status === 'DOWN').length,
        coverage,
        availability: availabilityStatus,
        note: noteParts.join(' ')
      };
    });
  });

  readonly topAlertHosts = computed(() =>
    this.assets()
      .filter((asset) => asset.problemCount > 0)
      .sort((a, b) => b.problemCount - a.problemCount)
      .slice(0, 8)
  );

  readonly dataCoverage = computed<DataCoverageVm[]>(() => [
    {
      title: 'Global KPIs',
      status: 'REAL',
      detail: 'Built from unified /api/monitoring/hosts and /api/monitoring/problems across Zabbix, Observium, ZKBio, and Camera.'
    },
    {
      title: 'Asset Categories',
      status: 'PARTIAL',
      detail:
        'Uses backend host categories when available and falls back to lightweight frontend inference for partial records.'
    },
    {
      title: 'Alerts Summary',
      status: 'REAL',
      detail: 'Built from unified monitoring problems and the active realtime /topic/monitoring/problems stream.'
    },
    {
      title: 'Realtime Coverage',
      status: 'PARTIAL',
      detail:
        'Problems, metrics, and source availability update in realtime; hosts refresh through snapshots because no /topic/monitoring/hosts topic is currently published.'
    }
  ]);

  constructor(
    private readonly api: MonitoringApiService,
    private readonly realtime: MonitoringRealtimeService
  ) {
    this.destroyRef.onDestroy(() => this.clearScheduledRefreshes());
  }

  loadSnapshot(): void {
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
            extractApiErrorMessage(error, 'Unable to load monitoring metrics snapshot.')
          );
          return of({ data: [] as UnifiedMonitoringMetric[], degraded: true, freshness: {}, coverage: {} });
        })
      ),
      sourceHealth: this.api.getSourceHealth().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load source health status.')
          );
          return of([]);
        })
      )
    }).subscribe({
      next: ({ hostsResponse, problemsResponse, metricsResponse, sourceHealth }) => {
        this.hosts.set(this.mergeHosts([], hostsResponse.data));
        this.problems.set(this.mergeProblems([], problemsResponse.data));
        this.metrics.set(this.mergeMetrics([], metricsResponse.data));
        this.sourceAvailability.set(sourceHealth);
        this.hostsFreshness.set(hostsResponse.freshness);
        this.problemsFreshness.set(problemsResponse.freshness);
        this.metricsFreshness.set(metricsResponse.freshness);
        this.hostsCoverage.set(hostsResponse.coverage);
        this.problemsCoverage.set(problemsResponse.coverage);
        this.metricsCoverage.set(metricsResponse.coverage);
        this.unifiedDegraded.set(
          hostsResponse.degraded || problemsResponse.degraded || metricsResponse.degraded
        );
        this.lastRefresh.set(new Date());
        this.isLoading.set(false);
      }
    });
  }

  bindRealtime(): void {
    if (this.realtimeBound) {
      return;
    }
    this.realtimeBound = true;

    this.realtime.monitoringProblems$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.problems.set(this.mergeProblems(this.problems(), incoming));
        this.problemsFreshness.update((freshness) => this.markRealtimeFreshness(freshness, incoming));
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime problem stream is currently unavailable.')
        );
      }
    });

    this.realtime.monitoringMetrics$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.metrics.set(this.mergeMetrics(this.metrics(), incoming));
        this.metricsFreshness.update((freshness) => this.markRealtimeFreshness(freshness, incoming));
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime metrics stream is currently unavailable.')
        );
      }
    });

    this.realtime.monitoringSources$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.sourceAvailability.set(
          this.mergeSourceAvailability(this.sourceAvailability(), incoming)
        );
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime source availability stream is currently unavailable.')
        );
      }
    });
  }

  triggerCollection(target: CollectionTarget): void {
    this.errorMessage.set(null);

    this.api.triggerCollection(target).subscribe({
      next: () => this.scheduleSnapshotRefresh(),
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(
            error,
            `Collection request failed for "${target}". Verify backend endpoint status.`
          )
        );
      }
    });
  }

  private mapAvailability(entry: SourceAvailability | undefined): 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN' {
    if (!entry) {
      return 'UNKNOWN';
    }
    if (
      entry.status === 'AVAILABLE' ||
      entry.status === 'DEGRADED' ||
      entry.status === 'UNAVAILABLE'
    ) {
      return entry.status;
    }
    return entry.available ? 'AVAILABLE' : 'UNAVAILABLE';
  }

  private mergeSourceAvailability(
    existing: SourceAvailability[],
    incoming: SourceAvailability
  ): SourceAvailability[] {
    const normalizedIncoming = {
      ...incoming,
      source: incoming.source.toUpperCase()
    };

    const map = new Map(existing.map((entry) => [entry.source.toUpperCase(), entry]));
    map.set(normalizedIncoming.source, normalizedIncoming);
    return Array.from(map.values());
  }

  private mergeHosts(existing: MonitoringHost[], incoming: MonitoringHost[]): MonitoringHost[] {
    const map = new Map<string, MonitoringHost>();

    for (const host of existing) {
      map.set(this.hostEntityKey(host.source, host.hostId, host.name, host.id), host);
    }
    for (const host of incoming) {
      map.set(this.hostEntityKey(host.source, host.hostId, host.name, host.id), host);
    }

    return Array.from(map.values());
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
    if (problem.problemId) {
      return `${problem.source}:${problem.problemId}`;
    }
    return `${problem.source}:${String(problem.eventId ?? problem.description)}`;
  }

  private metricKey(metric: UnifiedMonitoringMetric): string {
    return `${metric.source}:${metric.hostId}:${metric.itemId}:${metric.timestamp}`;
  }

  private hostEntityKey(
    source: string | null | undefined,
    hostId: string | null | undefined,
    name: string | null | undefined,
    id: string | null | undefined
  ): string {
    return `${(source ?? 'UNKNOWN').toUpperCase()}:${hostId ?? name ?? id ?? 'UNKNOWN_HOST'}`;
  }

  private buildAssets(
    hosts: MonitoringHost[],
    problems: MonitoringProblem[],
    metrics: UnifiedMonitoringMetric[]
  ): GlobalAssetVm[] {
    const hostMap = new Map<string, HostAccumulator>();

    for (const host of hosts) {
      const key = this.assetCorrelationKey(
        host.source,
        host.ip,
        host.name,
        host.hostId,
        host.id
      );
      hostMap.set(key, {
        hostId: host.hostId,
        hostname: this.normalizeHost(host.name ?? host.hostId ?? host.id),
        ip: host.ip ?? null,
        port: host.port ?? null,
        source: (host.source?.toUpperCase() ?? 'ZABBIX') as MonitoringSource,
        category: host.category ?? null,
        status: host.status ?? null,
        lastCheck: host.lastCheck ?? null,
        metricKeys: new Set<string>(),
        problemCount: 0,
        lastMetricTimestamp: null
      });
    }

    for (const metric of metrics) {
      const key = this.assetCorrelationKey(
        metric.source,
        metric.ip,
        metric.hostName,
        metric.hostId,
        metric.id
      );
      const current = hostMap.get(key);
      if (!current) {
        continue;
      }

      if (!current.ip) {
        current.ip = metric.ip ?? current.ip;
      }
      if (current.port == null && metric.port != null) {
        current.port = metric.port;
      }
      current.metricKeys.add(metric.metricKey);
      current.lastMetricTimestamp = Math.max(
        current.lastMetricTimestamp ?? 0,
        metric.timestamp || 0
      );

      hostMap.set(key, current);
    }

    for (const problem of problems) {
      const key = this.assetCorrelationKey(
        problem.source,
        problem.ip,
        problem.hostName,
        problem.hostId,
        problem.id
      );
      const current = hostMap.get(key);
      if (!current) {
        continue;
      }

      if (!current.ip) {
        current.ip = problem.ip ?? current.ip;
      }
      if (current.port == null && problem.port != null) {
        current.port = problem.port;
      }
      if (this.isDashboardProblem(problem)) {
        current.problemCount += 1;
      }
      hostMap.set(key, current);
    }

    return Array.from(hostMap.values())
      .map((entry) => {
        const category = this.normalizeAssetCategory(entry.category, entry.hostname, entry.metricKeys);
        const hasActiveAlert = entry.problemCount > 0;
        const backendStatus = this.normalizeAssetStatus(entry.status);
        const finalStatus =
          hasActiveAlert || backendStatus === 'DOWN'
            ? 'DOWN'
            : backendStatus === 'UP'
              ? 'UP'
              : 'UNKNOWN';

        return {
          id: `${entry.source}:${entry.hostId ?? entry.hostname}`,
          hostname: entry.hostname,
          lastCheck: entry.lastCheck,
          ip: entry.ip ?? '--',
          port: entry.port,
          source: entry.source,
          category,
          status: finalStatus,
          hasActiveAlert,
          problemCount: entry.problemCount,
          lastMetricTimestamp: entry.lastMetricTimestamp
        } as GlobalAssetVm;
      })
      .sort((a, b) => a.hostname.localeCompare(b.hostname));
  }

  private assetCorrelationKey(
    source: string | null | undefined,
    ip: string | null | undefined,
    name: string | null | undefined,
    hostId: string | null | undefined,
    id: string | null | undefined
  ): string {
    const normalizedSource = (source ?? 'UNKNOWN').toUpperCase();
    const normalizedIp = this.normalizeEntityValue(ip);
    if (normalizedIp) {
      return `${normalizedSource}:IP:${normalizedIp}`;
    }

    const normalizedName = this.normalizeEntityValue(name);
    if (normalizedName) {
      return `${normalizedSource}:NAME:${normalizedName}`;
    }

    return `${normalizedSource}:ID:${hostId ?? id ?? 'UNKNOWN_HOST'}`;
  }

  private normalizeEntityValue(value: string | null | undefined): string | null {
    if (!value) {
      return null;
    }

    const normalized = value.trim();
    if (!normalized || normalized === 'IP_UNKNOWN' || normalized === 'UNKNOWN_HOST') {
      return null;
    }

    return normalized.toUpperCase();
  }

  private normalizeHost(host: string | null | undefined): string {
    if (!host || !host.trim()) {
      return 'UNKNOWN_HOST';
    }
    return host.trim();
  }

  private normalizeAssetCategory(
    category: string | null,
    hostname: string,
    metricKeys: Set<string>
  ): AssetCategory {
    const normalizedCategory = (category ?? '').toUpperCase();
    if (normalizedCategory.includes('PRINT')) {
      return 'PRINTER';
    }
    if (normalizedCategory.includes('CAM')) {
      return 'CAMERA';
    }
    if (normalizedCategory.includes('ACCESS') || normalizedCategory.includes('DOOR')) {
      return 'ACCESS_CONTROL';
    }
    if (normalizedCategory.includes('SERVER') || normalizedCategory.includes('HOST')) {
      return 'SERVER';
    }
    return this.inferCategory(hostname, metricKeys);
  }

  private inferCategory(hostname: string, metricKeys: Set<string>): AssetCategory {
    const host = hostname.toLowerCase();
    const keys = Array.from(metricKeys).join('|').toLowerCase();
    const fingerprint = `${host}|${keys}`;

    if (fingerprint.includes('printer') || fingerprint.includes('print') || fingerprint.includes('hp-')) {
      return 'PRINTER';
    }
    if (fingerprint.includes('camera') || fingerprint.includes('cam') || fingerprint.includes('rtsp')) {
      return 'CAMERA';
    }
    if (fingerprint.includes('zk') || fingerprint.includes('access') || fingerprint.includes('door')) {
      return 'ACCESS_CONTROL';
    }
    if (hostname === 'UNKNOWN_HOST') {
      return 'UNKNOWN';
    }
    return 'SERVER';
  }

  private normalizeAssetStatus(status: string | null | undefined): 'UP' | 'DOWN' | 'UNKNOWN' {
    const normalized = (status ?? '').toUpperCase();
    if (['UP', 'AVAILABLE', 'NORMAL'].includes(normalized)) {
      return 'UP';
    }
    if (['DOWN', 'UNAVAILABLE', 'DEGRADED', 'ERROR', 'ACTIVE'].includes(normalized)) {
      return 'DOWN';
    }
    return 'UNKNOWN';
  }

  private readSourceCoverage(source: MonitoringSource): SourceHealthVm['coverage'] {
    const coverage = this.readDatasetCoverage('metrics', source).toLowerCase();
    if (coverage === 'native' || coverage === 'synthetic' || coverage === 'not_applicable') {
      return coverage;
    }
    return 'unknown';
  }

  private readDatasetFreshness(dataset: DatasetKind, source: MonitoringSource): string {
    return this.readDatasetMetadata(this.freshnessSignal(dataset), source, 'snapshot_missing');
  }

  private readDatasetCoverage(dataset: DatasetKind, source: MonitoringSource): string {
    return this.readDatasetMetadata(this.coverageSignal(dataset), source, '');
  }

  private readDatasetMetadata(
    metadata: DatasetMetadata,
    source: MonitoringSource,
    fallback: string
  ): string {
    return metadata[source] ?? fallback;
  }

  private freshnessSignal(dataset: DatasetKind): DatasetMetadata {
    switch (dataset) {
      case 'hosts':
        return this.hostsFreshness();
      case 'problems':
        return this.problemsFreshness();
      case 'metrics':
        return this.metricsFreshness();
    }
  }

  private coverageSignal(dataset: DatasetKind): DatasetMetadata {
    switch (dataset) {
      case 'hosts':
        return this.hostsCoverage();
      case 'problems':
        return this.problemsCoverage();
      case 'metrics':
        return this.metricsCoverage();
    }
  }

  private markRealtimeFreshness(
    current: Record<string, string>,
    incoming: Array<{ source: string | null | undefined }>
  ): Record<string, string> {
    const next = { ...current };
    for (const source of new Set(
      incoming
        .map((item) => item.source?.toUpperCase())
        .filter((source): source is string => Boolean(source))
    )) {
      next[source] = 'live';
    }
    return next;
  }

  private severityRank(value: string | null | undefined): number {
    const normalized = (value ?? '').toUpperCase();
    if (normalized === 'DISASTER' || normalized === 'CRITICAL') {
      return 5;
    }
    if (normalized === 'HIGH') {
      return 4;
    }
    if (normalized === 'AVERAGE' || normalized === 'MEDIUM') {
      return 3;
    }
    if (normalized === 'WARNING' || normalized === 'WARN') {
      return 2;
    }
    if (normalized === 'INFO' || normalized === 'INFORMATION') {
      return 1;
    }
    return Number(value ?? 0) || 0;
  }

  private isDashboardProblem(problem: MonitoringProblem): boolean {
    return this.severityRank(problem.severity) >= 4;
  }

  private scheduleSnapshotRefresh(): void {
    // Backend collection endpoints answer immediately, while the actual work runs asynchronously.
    // We delay the snapshot reload slightly so the frontend reads the updated state more reliably.
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
