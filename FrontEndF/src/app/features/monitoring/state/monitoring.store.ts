import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CameraDevice } from '../../../core/models/camera-device.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import { RealtimeConnectionStore } from '../../../core/realtime/realtime-connection.store';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import {
  AssetCategory,
  DataCoverageVm,
  GlobalAssetVm,
  GlobalKpiVm,
  MonitoringSource,
  ProblemSummaryVm,
  RealtimeDataState,
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
  private readonly config = inject<AppConfig>(APP_CONFIG);
  private readonly realtimeConnection = inject(RealtimeConnectionStore);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;
  private loadGeneration = 0;
  private heartbeatIntervalId: number | null = null;
  private lastNoDeltaLogBucket = -1;

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);
  readonly nowMs = signal(Date.now());
  readonly lastBackendRefreshAtMs = signal<number | null>(null);
  readonly lastWebSocketUpdateAtMs = signal<number | null>(null);
  readonly lastMetricUpdateAtMs = signal<number | null>(null);
  readonly wsEventCounter = signal(0);

  readonly hosts = signal<MonitoringHost[]>([]);
  readonly cameraDevices = signal<CameraDevice[]>([]);
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
    this.buildAssets(this.hosts(), this.cameraDevices(), this.problems(), this.metrics(), this.sourceAvailability())
  );

  readonly websocketUiState = computed<'Connected' | 'Reconnecting' | 'Disconnected'>(() => {
    const status = this.realtimeConnection.status();
    if (status === 'CONNECTED') {
      return 'Connected';
    }
    if (status === 'CONNECTING') {
      return 'Reconnecting';
    }
    return 'Disconnected';
  });

  readonly lastBackendRefreshLabel = computed(() => this.relativeFromMs(this.lastBackendRefreshAtMs()));
  readonly lastWebSocketUpdateLabel = computed(() => this.relativeFromMs(this.lastWebSocketUpdateAtMs()));
  readonly lastMetricUpdateLabel = computed(() => this.relativeFromMs(this.lastMetricUpdateAtMs()));

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
      (['ZABBIX', 'SNMP', 'CAMERA'] as MonitoringSource[]).map((source) => [
        source,
        this.assets().filter((asset) => asset.source === source)
      ])
    );

    return (['ZABBIX', 'SNMP', 'CAMERA'] as MonitoringSource[]).map((source) => {
      const availability = availabilityMap.get(source);
      const assets = source === 'CAMERA'
        ? this.buildCameraAssets(this.cameraDevices())
        : assetsBySource.get(source) ?? [];
      const coverage = this.readSourceCoverage(source);
      const availabilityStatus = this.mapAvailability(availability);
      const noteParts = [
        `Hosts: ${source === 'CAMERA' ? 'persisted' : this.readDatasetFreshness('hosts', source)}`,
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
      detail: 'Built from unified /api/monitoring/hosts and /api/monitoring/problems across Zabbix, SNMP, and Camera.'
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
    this.startHeartbeat();
    this.destroyRef.onDestroy(() => {
      this.clearScheduledRefreshes();
      this.stopHeartbeat();
    });
  }

  loadSnapshot(): void {
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
            extractApiErrorMessage(error, 'Unable to load monitoring metrics snapshot.')
          );
          return of({ data: [] as UnifiedMonitoringMetric[], degraded: true, freshness: {}, coverage: {} });
        })
      ),
      cameraDevices: this.api.getCameraDevices().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load persisted camera inventory.')
          );
          return of([] as CameraDevice[]);
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
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ hostsResponse, problemsResponse, metricsResponse, cameraDevices, sourceHealth }) => {
        if (currentLoadGeneration !== this.loadGeneration) {
          return;
        }

        this.hosts.set(this.mergeHosts([], hostsResponse.data));
        this.cameraDevices.set(cameraDevices);
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
        this.lastBackendRefreshAtMs.set(Date.now());
        this.lastMetricUpdateAtMs.set(this.resolveLatestMetricTimestampMs(this.metrics()));
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
        console.debug('WS EVENT RECEIVED', 'problems', incoming.length);
        this.problems.set(this.mergeProblems(this.problems(), incoming));
        this.problemsFreshness.update((freshness) => this.markRealtimeFreshness(freshness));
        this.lastWebSocketUpdateAtMs.set(Date.now());
        this.wsEventCounter.update((value) => value + 1);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime problem stream is currently unavailable.')
        );
      }
    });

    this.realtime.monitoringMetrics$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        console.debug('WS EVENT RECEIVED', 'metrics', incoming.length);
        this.metrics.set(this.mergeMetrics(this.metrics(), incoming));
        this.metricsFreshness.update((freshness) => this.markRealtimeFreshness(freshness));
        this.lastWebSocketUpdateAtMs.set(Date.now());
        this.wsEventCounter.update((value) => value + 1);
        this.lastMetricUpdateAtMs.set(this.resolveLatestMetricTimestampMs(this.metrics()));
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
        this.lastWebSocketUpdateAtMs.set(Date.now());
        this.wsEventCounter.update((value) => value + 1);
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
    if (incoming.length === 0) {
      console.debug('KPI UNCHANGED', 'no incoming metrics delta');
    }
    const map = new Map<string, UnifiedMonitoringMetric>();

    for (const metric of existing) {
      map.set(this.metricKey(metric), metric);
    }
    for (const metric of incoming) {
      map.set(this.metricKey(metric), metric);
    }
    const merged = Array.from(map.values());
    if (merged.length !== existing.length || incoming.length > 0) {
      console.debug('KPI UPDATED', `existing=${existing.length} incoming=${incoming.length} merged=${merged.length}`);
    }
    return merged;
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
    cameraDevices: CameraDevice[],
    problems: MonitoringProblem[],
    metrics: UnifiedMonitoringMetric[],
    availability: SourceAvailability[]
  ): GlobalAssetVm[] {
    const hostMap = new Map<string, HostAccumulator>();
    const availabilityMap = new Map(
      availability.map((entry) => [entry.source.toUpperCase(), this.mapAvailability(entry)])
    );

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

    for (const camera of cameraDevices) {
      const key = this.assetCorrelationKey(
        'CAMERA',
        camera.ip,
        camera.name,
        camera.ip,
        camera.id != null ? String(camera.id) : null
      );
      hostMap.set(key, {
        hostId: camera.ip ?? (camera.id != null ? String(camera.id) : null),
        hostname: this.normalizeHost(camera.name ?? camera.ip ?? (camera.id != null ? `CAMERA-${camera.id}` : null)),
        ip: camera.ip ?? null,
        port: camera.port ?? null,
        source: 'CAMERA',
        category: camera.category ?? 'CAMERA',
        status: camera.reachable ? 'UP' : (camera.status ?? 'DOWN'),
        lastCheck: camera.lastScanAt ?? null,
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
        const staleThresholdMs = entry.source === 'CAMERA'
          ? this.config.hostsStaleThresholdMs
          : this.config.metricsStaleThresholdMs;
        const metricTimestampMs = entry.lastMetricTimestamp != null ? entry.lastMetricTimestamp * 1000 : null;
        const availabilityStatus = availabilityMap.get(entry.source) ?? 'UNKNOWN';
        const realtimeState = this.resolveRealtimeState(metricTimestampMs, staleThresholdMs, availabilityStatus);

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
          lastMetricTimestamp: entry.lastMetricTimestamp,
          realtimeState,
          realtimeLabel: this.toRealtimeLabel(realtimeState),
          lastMetricLabel: this.relativeFromMs(metricTimestampMs)
        } as GlobalAssetVm;
      })
      .sort((a, b) => a.hostname.localeCompare(b.hostname));
  }

  private buildCameraAssets(cameraDevices: CameraDevice[]): GlobalAssetVm[] {
    return cameraDevices.map((camera) => ({
      id: `CAMERA:${camera.ip ?? camera.id ?? 'UNKNOWN_CAMERA'}`,
      hostname: this.normalizeHost(camera.name ?? camera.ip ?? (camera.id != null ? `CAMERA-${camera.id}` : null)),
      lastCheck: camera.lastScanAt ?? null,
      ip: camera.ip ?? '--',
      port: camera.port ?? null,
      source: 'CAMERA',
      category: 'CAMERA',
      status: camera.reachable ? 'UP' : this.normalizeAssetStatus(camera.status ?? 'DOWN'),
      hasActiveAlert: false,
      problemCount: 0,
      lastMetricTimestamp: null,
      realtimeState: this.resolveRealtimeState(
        camera.lastScanAt ? this.parseDateStringToMs(camera.lastScanAt) : null,
        this.config.hostsStaleThresholdMs,
        'AVAILABLE'
      ),
      realtimeLabel: this.toRealtimeLabel(
        this.resolveRealtimeState(
          camera.lastScanAt ? this.parseDateStringToMs(camera.lastScanAt) : null,
          this.config.hostsStaleThresholdMs,
          'AVAILABLE'
        )
      ),
      lastMetricLabel: this.relativeFromMs(camera.lastScanAt ? this.parseDateStringToMs(camera.lastScanAt) : null)
    }));
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

  private parseDateStringToMs(value: string | null | undefined): number | null {
    if (!value) {
      return null;
    }
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? null : parsed;
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

  getFreshnessLabel(freshness: string | null | undefined): string {
    if (!freshness) {
      return 'Unknown';
    }
    switch (freshness.toLowerCase()) {
      case 'live':
        return 'Live data';
      case 'snapshot_fallback':
        return 'Snapshot fallback';
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

  private resolveRealtimeState(
    metricTimestampMs: number | null,
    staleThresholdMs: number,
    availabilityStatus: 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN'
  ): RealtimeDataState {
    if (availabilityStatus === 'UNAVAILABLE') {
      return 'offline';
    }

    const wsStatus = this.realtimeConnection.status();
    if (wsStatus === 'DISCONNECTED' || wsStatus === 'ERROR') {
      return 'disconnected';
    }

    if (metricTimestampMs == null) {
      return 'unchanged';
    }

    const ageMs = Math.max(0, this.nowMs() - metricTimestampMs);
    if (ageMs <= this.config.freshDataWindowMs) {
      return 'fresh';
    }
    if (ageMs > staleThresholdMs) {
      return 'stale';
    }
    return 'unchanged';
  }

  private toRealtimeLabel(state: RealtimeDataState): string {
    switch (state) {
      case 'fresh':
        return 'Fresh';
      case 'unchanged':
        return 'Unchanged';
      case 'stale':
        return 'Stale';
      case 'offline':
        return 'Offline';
      case 'disconnected':
        return 'Disconnected';
      default:
        return 'Unknown';
    }
  }

  private relativeFromMs(timestampMs: number | null): string {
    if (!timestampMs) {
      return 'No update yet';
    }
    const diff = Math.max(0, this.nowMs() - timestampMs);
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) {
      return `Updated ${seconds}s ago`;
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return `Updated ${minutes} min ago`;
    }
    const date = new Date(timestampMs);
    const hh = `${date.getHours()}`.padStart(2, '0');
    const mm = `${date.getMinutes()}`.padStart(2, '0');
    return `No update since ${hh}:${mm}`;
  }

  private resolveLatestMetricTimestampMs(metrics: UnifiedMonitoringMetric[]): number | null {
    let maxTimestamp = 0;
    for (const metric of metrics) {
      const ts = metric.timestamp ?? 0;
      if (ts > maxTimestamp) {
        maxTimestamp = ts;
      }
    }
    return maxTimestamp > 0 ? maxTimestamp * 1000 : null;
  }

  private startHeartbeat(): void {
    this.heartbeatIntervalId = window.setInterval(() => {
      this.nowMs.set(Date.now());
      const wsStatus = this.realtimeConnection.status();
      if (wsStatus === 'CONNECTED') {
        console.debug('WS HEARTBEAT OK');
        const lastWs = this.lastWebSocketUpdateAtMs();
        if (lastWs != null) {
          const gap = this.nowMs() - lastWs;
          if (gap > this.config.wsNoDeltaThresholdMs) {
            const bucket = Math.floor(gap / this.config.wsNoDeltaThresholdMs);
            if (bucket !== this.lastNoDeltaLogBucket) {
              console.debug('WS NO DELTA RECEIVED', `gapMs=${gap}`);
              this.lastNoDeltaLogBucket = bucket;
            }
          } else {
            this.lastNoDeltaLogBucket = -1;
          }
        }
      }

      for (const asset of this.assets()) {
        if (asset.realtimeState === 'stale') {
          console.debug('KPI MARKED STALE', asset.id);
        }
      }
    }, 5000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatIntervalId == null) {
      return;
    }
    window.clearInterval(this.heartbeatIntervalId);
    this.heartbeatIntervalId = null;
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

  private markRealtimeFreshness(currentFreshness: Record<string, string>): Record<string, string> {
    const next = { ...currentFreshness };
    for (const [source, currentState] of Object.entries(currentFreshness)) {
      if (currentState !== 'database_snapshot_fallback' &&
          currentState !== 'memory_snapshot_fallback' &&
          currentState !== 'snapshot_fallback' &&
          currentState !== 'snapshot_missing' &&
          currentState !== 'redis_fallback') {
        next[source] = 'live';
      }
    }
    return next;
  }
}

