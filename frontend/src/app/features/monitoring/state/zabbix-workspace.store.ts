import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { DashboardAnomaly } from '../../../core/models/dashboard-anomaly.model';
import { DashboardOverview } from '../../../core/models/dashboard-overview.model';
import { DashboardPrediction } from '../../../core/models/dashboard-prediction.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import {
  ZabbixAnomalyVm,
  ZabbixHostVm,
  ZabbixMetricGroupVm,
  ZabbixHostMetricVm,
  ZabbixMetricVm,
  ZabbixOverviewVm,
  ZabbixPredictionVm,
  ZabbixProblemVm,
  ZabbixQualityVm
} from './zabbix-workspace.models';

@Injectable()
export class ZabbixWorkspaceStore {
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);

  readonly problemSearch = signal('');
  readonly problemSeverityFilter = signal<'ALL' | '5' | '4'>('ALL');
  readonly problemStatusFilter = signal<'ALL' | 'ACTIVE' | 'RESOLVED'>('ALL');
  readonly metricSearch = signal('');
  readonly hostSearch = signal('');
  readonly selectedHostKey = signal<string | null>(null);

  readonly problems = signal<ZabbixProblem[]>([]);
  readonly metrics = signal<ZabbixMetric[]>([]);
  readonly predictions = signal<DashboardPrediction[]>([]);
  readonly anomalies = signal<DashboardAnomaly[]>([]);
  readonly sourceAvailability = signal<SourceAvailability[]>([]);
  readonly quality = signal<ZabbixQualityVm>({
    warnings: [],
    metricsWithoutTimestamp: 0,
    problemsWithoutStartedAt: 0,
    problemsWithoutResolvedAt: 0,
    severityDistribution: []
  });

  readonly zabbixSource = computed(() =>
    this.sourceAvailability().find((entry) => entry.source.toUpperCase() === 'ZABBIX')
  );

  readonly overview = computed<ZabbixOverviewVm>(() => {
    const hostKeys = new Set<string>();
    const downHostKeys = new Set<string>();

    for (const problem of this.problems()) {
      const key = this.hostKey(problem.hostId, problem.host);
      hostKeys.add(key);
      downHostKeys.add(key);
    }

    for (const metric of this.metrics()) {
      hostKeys.add(this.hostKey(metric.hostId, metric.hostName));
    }

    for (const prediction of this.predictions()) {
      hostKeys.add(this.hostKey(String(prediction.hostid), prediction.hostName));
    }

    const source = this.zabbixSource();
    const availability =
      source?.status === 'AVAILABLE' ||
      source?.status === 'DEGRADED' ||
      source?.status === 'UNAVAILABLE'
        ? source.status
        : source?.available
          ? 'AVAILABLE'
          : source
            ? 'UNAVAILABLE'
            : 'UNKNOWN';

    return {
      totalHosts: hostKeys.size,
      activeProblems: this.problems().length,
      downHosts: downHostKeys.size,
      totalMetrics: this.metrics().length,
      totalProblems: this.totalProblemCount(),
      riskyHosts: this.predictionRows().filter((prediction) => prediction.displayStatus === 'Risk').length,
      anomalies: this.anomalies().length,
      availability,
      availabilityNote:
        source?.message ??
        source?.lastError ??
        (availability === 'AVAILABLE'
          ? 'Live Zabbix snapshot and realtime streams are connected.'
          : 'Waiting for source health information.')
    };
  });

  readonly problemRows = computed<ZabbixProblemVm[]>(() =>
    this.problems()
      .map((problem) => ({
        ...problem,
        severityLabel: this.severityLabel(problem.severity),
        severityTone: this.severityTone(problem.severity),
        statusLabel: problem.status ?? (problem.active ? 'ACTIVE' : 'RESOLVED')
      }))
      .sort((left, right) => (right.startedAt ?? 0) - (left.startedAt ?? 0))
  );

  readonly filteredProblems = computed<ZabbixProblemVm[]>(() => {
    const search = this.problemSearch().trim().toLowerCase();
    const severity = this.problemSeverityFilter();
    const status = this.problemStatusFilter();

    return this.problemRows().filter((problem) => {
      const matchesSearch =
        !search ||
        [problem.host, problem.ip, problem.description, problem.hostId, problem.problemId]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(search));
      const matchesSeverity = severity === 'ALL' || problem.severity === severity;
      const matchesStatus = status === 'ALL' || problem.statusLabel === status;
      return matchesSearch && matchesSeverity && matchesStatus;
    });
  });

  readonly metricRows = computed<ZabbixMetricVm[]>(() =>
    [...this.metrics()]
      .sort((left, right) => (right.timestamp ?? 0) - (left.timestamp ?? 0))
      .map((metric) => ({
        ...metric,
        hostLabel: metric.hostName || metric.hostId
      }))
  );

  readonly filteredMetrics = computed<ZabbixMetricVm[]>(() => {
    const search = this.metricSearch().trim().toLowerCase();
    return this.metricRows().filter((metric) => {
      if (!search) {
        return true;
      }
      return [metric.hostLabel, metric.hostId, metric.metricKey, metric.itemId]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(search));
    });
  });

  readonly predictionRows = computed<ZabbixPredictionVm[]>(() =>
    [...this.predictions()]
      .sort((left, right) => right.probability - left.probability)
      .map((prediction) => {
        const key = this.hostKey(String(prediction.hostid), prediction.hostName);
        const assessment = this.computeHostAssessment(key, prediction);

        return {
          ...prediction,
          riskLevel: this.toRiskLevel(prediction.prediction, prediction.probability),
          displayStatus: assessment.status,
          riskTone: this.toPredictionTone(assessment.status),
          explanation: assessment.explanation
        };
      })
  );

  readonly hostRows = computed<ZabbixHostVm[]>(() => {
    const hosts = new Map<
      string,
      {
        key: string;
        hostId: string | null;
        hostName: string;
        ip: string | null;
        metricCount: number;
        totalProblems: number;
        activeProblems: number;
        anomalyCount: number;
        latestTimestamp: number | null;
        riskStatus: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable';
      }
    >();

    for (const metric of this.metrics()) {
      const key = this.hostKey(metric.hostId, metric.hostName);
      const existing = hosts.get(key) ?? {
        key,
        hostId: metric.hostId,
        hostName: metric.hostName || metric.hostId || 'Unknown host',
        ip: metric.ip,
        metricCount: 0,
        totalProblems: 0,
        activeProblems: 0,
        anomalyCount: 0,
        latestTimestamp: null,
        riskStatus: 'Prediction unavailable' as const
      };

      existing.metricCount += 1;
      existing.latestTimestamp = Math.max(existing.latestTimestamp ?? 0, metric.timestamp ?? 0);
      existing.ip = existing.ip || metric.ip;
      hosts.set(key, existing);
    }

    for (const problem of this.problems()) {
      const key = this.hostKey(problem.hostId, problem.host);
      const existing = hosts.get(key) ?? {
        key,
        hostId: problem.hostId,
        hostName: problem.host || problem.hostId || 'Unknown host',
        ip: problem.ip,
        metricCount: 0,
        totalProblems: 0,
        activeProblems: 0,
        anomalyCount: 0,
        latestTimestamp: null,
        riskStatus: 'Prediction unavailable' as const
      };

      existing.totalProblems += 1;
      if (problem.active || (problem.status ?? 'ACTIVE') === 'ACTIVE') {
        existing.activeProblems += 1;
      }
      existing.ip = existing.ip || problem.ip;
      hosts.set(key, existing);
    }

    for (const anomaly of this.anomalies()) {
      const key = this.hostKey(String(anomaly.hostid), anomaly.hostName);
      const existing = hosts.get(key) ?? {
        key,
        hostId: String(anomaly.hostid),
        hostName: anomaly.hostName || `Host ${anomaly.hostid}`,
        ip: null,
        metricCount: 0,
        totalProblems: 0,
        activeProblems: 0,
        anomalyCount: 0,
        latestTimestamp: null,
        riskStatus: 'Prediction unavailable' as const
      };

      existing.anomalyCount += 1;
      hosts.set(key, existing);
    }

    for (const prediction of this.predictions()) {
      const key = this.hostKey(String(prediction.hostid), prediction.hostName);
      const existing = hosts.get(key) ?? {
        key,
        hostId: String(prediction.hostid),
        hostName: prediction.hostName || `Host ${prediction.hostid}`,
        ip: null,
        metricCount: 0,
        totalProblems: 0,
        activeProblems: 0,
        anomalyCount: 0,
        latestTimestamp: null,
        riskStatus: 'Prediction unavailable' as const
      };
      hosts.set(key, existing);
    }

    const search = this.hostSearch().trim().toLowerCase();
    const predictionsByKey = new Map(
      this.predictions().map((prediction) => [
        this.hostKey(String(prediction.hostid), prediction.hostName),
        prediction
      ])
    );

    return Array.from(hosts.values())
      .filter((host) => {
        if (!search) {
          return true;
        }
        return [host.hostName, host.hostId, host.ip]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(search));
      })
      .map((host) => {
        const assessment = this.computeHostAssessment(host.key, predictionsByKey.get(host.key) ?? null);

        return {
        key: host.key,
        hostId: host.hostId,
        hostName: host.hostName,
        ip: host.ip,
        totalMetrics: host.metricCount,
        totalProblems: host.totalProblems,
        activeProblems: host.activeProblems,
        anomalyCount: host.anomalyCount,
        latestTimestamp: host.latestTimestamp,
        riskStatus: assessment.status,
        statusTone: this.toHostTone(assessment.status)
      };
      })
      .sort((left, right) => {
        const severityGap = right.activeProblems - left.activeProblems;
        if (severityGap !== 0) {
          return severityGap;
        }
        return left.hostName.localeCompare(right.hostName);
      });
  });

  readonly selectedHost = computed<ZabbixHostVm | null>(() => {
    const hosts = this.hostRows();
    if (!hosts.length) {
      return null;
    }

    const selectedKey = this.selectedHostKey();
    if (!selectedKey) {
      return hosts[0];
    }

    return hosts.find((host) => host.key === selectedKey) ?? hosts[0];
  });

  readonly selectedHostProblems = computed<ZabbixProblemVm[]>(() => {
    const selected = this.selectedHost();
    if (!selected) {
      return [];
    }
    return this.problemRows().filter((problem) => this.hostKey(problem.hostId, problem.host) === selected.key);
  });

  readonly selectedHostPrediction = computed<ZabbixPredictionVm | null>(() => {
    const selected = this.selectedHost();
    if (!selected) {
      return null;
    }
    return (
      this.predictionRows().find(
        (prediction) => this.hostKey(String(prediction.hostid), prediction.hostName) === selected.key
      ) ?? null
    );
  });

  readonly selectedHostAnomalies = computed<ZabbixAnomalyVm[]>(() => {
    const selected = this.selectedHost();
    if (!selected) {
      return [];
    }
    return this.anomalyRows().filter(
      (anomaly) => this.hostKey(String(anomaly.hostid), anomaly.hostName) === selected.key
    );
  });

  readonly selectedHostMetricGroups = computed<ZabbixMetricGroupVm[]>(() => {
    const selected = this.selectedHost();
    if (!selected) {
      return [];
    }

    const metrics = this.metrics()
      .filter((metric) => this.hostKey(metric.hostId, metric.hostName) === selected.key)
      .sort((left, right) => (right.timestamp ?? 0) - (left.timestamp ?? 0));

    const byMetricKey = new Map<string, ZabbixMetric[]>();
    for (const metric of metrics) {
      const bucket = byMetricKey.get(metric.metricKey) ?? [];
      bucket.push(metric);
      byMetricKey.set(metric.metricKey, bucket);
    }

    const groups = new Map<ZabbixMetricGroupVm['category'], ZabbixHostMetricVm[]>();

    for (const [metricKey, history] of byMetricKey.entries()) {
      const latest = history[0];
      if (!latest || latest.value == null) {
        continue;
      }
      const previous = history.find((metric) => metric.timestamp !== latest.timestamp && metric.value != null) ?? null;
      const category = this.metricCategory(metricKey);
      const item: ZabbixHostMetricVm = {
        metricKey,
        label: this.metricLabel(metricKey),
        currentValue: latest.value,
        previousValue: previous?.value ?? null,
        timestamp: latest.timestamp ?? null,
        trend:
          previous?.value == null
            ? 'flat'
            : latest.value > previous.value
              ? 'up'
              : latest.value < previous.value
                ? 'down'
                : 'flat'
      };

      const bucket = groups.get(category) ?? [];
      bucket.push(item);
      groups.set(category, bucket);
    }

    return Array.from(groups.entries())
      .map(([category, items]) => ({
        category,
        metrics: items.sort((left, right) => left.label.localeCompare(right.label))
      }))
      .sort((left, right) => left.category.localeCompare(right.category));
  });

  readonly anomalyRows = computed<ZabbixAnomalyVm[]>(() =>
    [...this.anomalies()]
      .sort((left, right) => right.anomalyScore - left.anomalyScore)
      .map((anomaly) => ({
        ...anomaly,
        scorePercent: Math.round(anomaly.anomalyScore * 100)
      }))
  );

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
      problems: this.api.getZabbixMonitoringProblems().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load active Zabbix problems.')
          );
          return of([]);
        })
      ),
      metrics: this.api.getZabbixMonitoringMetrics().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load Zabbix metrics snapshot.')
          );
          return of([]);
        })
      ),
      predictions: this.api.getPredictions().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load ML predictions.')
          );
          return of([]);
        })
      ),
      anomalies: this.api.getAnomalies().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load anomaly detection results.')
          );
          return of([]);
        })
      ),
      sourceHealth: this.api.getSourceHealth().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load source health status.')
          );
          return of([]);
        })
      ),
      overview: this.api.getDashboardOverview().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load data quality summary.')
          );
          return of(null);
        })
      )
    }).subscribe({
      next: ({ problems, metrics, predictions, anomalies, sourceHealth, overview }) => {
        this.problems.set(this.mergeProblems([], problems));
        this.metrics.set(this.mergeMetrics([], metrics));
        this.predictions.set(predictions);
        this.anomalies.set(anomalies);
        this.sourceAvailability.set(sourceHealth);
        this.quality.set(this.mapQuality(overview));
        this.ensureSelection();
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

    this.realtime.monitoringProblemsForZabbix$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.problems.set(this.mergeProblems(this.problems(), incoming));
        this.ensureSelection();
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime problem stream is currently unavailable.')
        );
      }
    });

    this.realtime.monitoringMetricsForZabbix$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.metrics.set(this.mergeMetrics(this.metrics(), incoming));
        this.ensureSelection();
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Realtime metrics stream is currently unavailable.')
        );
      }
    });

    this.realtime.sourceAvailability$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

  updateProblemSearch(value: string): void {
    this.problemSearch.set(value);
  }

  updateProblemSeverityFilter(value: 'ALL' | '5' | '4'): void {
    this.problemSeverityFilter.set(value);
  }

  updateProblemStatusFilter(value: 'ALL' | 'ACTIVE' | 'RESOLVED'): void {
    this.problemStatusFilter.set(value);
  }

  updateMetricSearch(value: string): void {
    this.metricSearch.set(value);
  }

  updateHostSearch(value: string): void {
    this.hostSearch.set(value);
    this.ensureSelection();
  }

  selectHost(hostKey: string): void {
    this.selectedHostKey.set(hostKey);
  }

  private mapQuality(overview: DashboardOverview | null): ZabbixQualityVm {
    const dataQuality = overview?.dataQuality ?? {};
    const problemsWithoutStartedAt = this.numberValue(dataQuality['problemsWithoutStartedAt']);
    const problemsWithoutResolvedAt = this.numberValue(dataQuality['problemsWithoutResolvedAt']);
    const metricsWithoutTimestamp = this.numberValue(dataQuality['metricsWithoutTimestamp']);
    const severityDistribution = this.mapSeverityDistribution(dataQuality['severityDistribution']);

    const warnings = [
      overview?.warning,
      this.stringValue(dataQuality['warning']),
      metricsWithoutTimestamp > 0
        ? `${metricsWithoutTimestamp} metrics are missing timestamps.`
        : null,
      problemsWithoutStartedAt > 0
        ? `${problemsWithoutStartedAt} problems are missing startedAt values.`
        : null,
      problemsWithoutResolvedAt > 0
        ? `${problemsWithoutResolvedAt} resolved problems are missing resolvedAt values.`
        : null
    ].filter((warning): warning is string => Boolean(warning));

    return {
      warnings,
      metricsWithoutTimestamp,
      problemsWithoutStartedAt,
      problemsWithoutResolvedAt,
      severityDistribution
    };
  }

  private mapSeverityDistribution(value: unknown): Array<{ severity: string; count: number }> {
    if (!value || typeof value !== 'object') {
      return [];
    }
    return Object.entries(value as Record<string, unknown>).map(([severity, count]) => ({
      severity,
      count: this.numberValue(count)
    }));
  }

  private numberValue(value: unknown): number {
    return typeof value === 'number' ? value : Number(value ?? 0) || 0;
  }

  private stringValue(value: unknown): string | null {
    return typeof value === 'string' && value.trim() ? value : null;
  }

  private mergeProblems(existing: ZabbixProblem[], incoming: ZabbixProblem[]): ZabbixProblem[] {
    const map = new Map<string, ZabbixProblem>();
    for (const problem of existing) {
      map.set(this.problemKey(problem), problem);
    }
    for (const problem of incoming) {
      map.set(this.problemKey(problem), problem);
    }
    return Array.from(map.values());
  }

  private mergeMetrics(existing: ZabbixMetric[], incoming: ZabbixMetric[]): ZabbixMetric[] {
    const map = new Map<string, ZabbixMetric>();
    for (const metric of existing) {
      map.set(this.metricKey(metric), metric);
    }
    for (const metric of incoming) {
      map.set(this.metricKey(metric), metric);
    }
    return Array.from(map.values());
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

  private problemKey(problem: ZabbixProblem): string {
    return problem.problemId || String(problem.eventId ?? problem.description);
  }

  private metricKey(metric: ZabbixMetric): string {
    return `${metric.hostId}:${metric.itemId}:${metric.timestamp}`;
  }

  private hostKey(hostId: string | null | undefined, hostName: string | null | undefined): string {
    return hostId || hostName || 'UNKNOWN_HOST';
  }

  private ensureSelection(): void {
    const hosts = this.hostRows();
    if (!hosts.length) {
      this.selectedHostKey.set(null);
      return;
    }

    const current = this.selectedHostKey();
    if (!current || !hosts.some((host) => host.key === current)) {
      this.selectedHostKey.set(hosts[0].key);
    }
  }

  private metricCategory(metricKey: string): ZabbixMetricGroupVm['category'] {
    const key = metricKey.toLowerCase();
    if (key.includes('cpu') || key.includes('processor') || key.includes('load')) {
      return 'CPU';
    }
    if (key.includes('memory') || key.includes('mem') || key.includes('swap')) {
      return 'Memory';
    }
    if (key.includes('net') || key.includes('if.') || key.includes('interface') || key.includes('traffic')) {
      return 'Network';
    }
    if (key.includes('disk') || key.includes('fs') || key.includes('vfs')) {
      return 'Disk';
    }
    if (key.includes('uptime') || key.includes('system') || key.includes('proc')) {
      return 'System';
    }
    return 'Other';
  }

  private metricLabel(metricKey: string): string {
    return metricKey
      .replace(/[\[\]\._]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private totalProblemCount(): number {
    const distribution = this.quality().severityDistribution;
    if (distribution.length) {
      return distribution.reduce((total, item) => total + item.count, 0);
    }
    return this.problems().length;
  }

  private toRiskLevel(predictionClass: number, probability: number): 1 | 2 | 3 {
    if (predictionClass >= 3 || probability >= 0.9) {
      return 3;
    }
    if (predictionClass >= 2 || probability >= 0.65) {
      return 2;
    }
    return 1;
  }

  private computeIncidentSignal(activeIncidents: number): 'LOW' | 'MEDIUM' | 'HIGH' {
    if (activeIncidents >= 4) {
      return 'HIGH';
    }
    if (activeIncidents >= 2) {
      return 'MEDIUM';
    }
    return 'LOW';
  }

  private computeSeveritySignal(maxSeverity: number): 'LOW' | 'MEDIUM' | 'HIGH' {
    if (maxSeverity >= 4) {
      return 'HIGH';
    }
    if (maxSeverity >= 2) {
      return 'MEDIUM';
    }
    return 'LOW';
  }

  private computeMetricSignal(metrics: ZabbixMetric[]): 'LOW' | 'MEDIUM' | 'HIGH' {
    const latestByKey = new Map<string, ZabbixMetric[]>();

    for (const metric of [...metrics].sort((left, right) => (right.timestamp ?? 0) - (left.timestamp ?? 0))) {
      const bucket = latestByKey.get(metric.metricKey) ?? [];
      bucket.push(metric);
      latestByKey.set(metric.metricKey, bucket);
    }

    let strongest: 'LOW' | 'MEDIUM' | 'HIGH' = 'LOW';

    for (const [metricKey, history] of latestByKey.entries()) {
      const latest = history[0];
      const previous = history.find((item) => item.timestamp !== latest.timestamp) ?? null;
      const normalizedKey = metricKey.toLowerCase();

      if (this.isPingMetric(normalizedKey) && latest.value === 0) {
        return 'HIGH';
      }

      if (this.isPacketLossMetric(normalizedKey)) {
        if (latest.value > 50) {
          return 'HIGH';
        }
        if (latest.value > 20) {
          strongest = this.maxSignal(strongest, 'MEDIUM');
        }
      }

      if (this.isLatencyMetric(normalizedKey)) {
        const previousValue = previous?.value ?? null;
        if (
          latest.value > 0.30 ||
          (previousValue != null && previousValue > 0 && latest.value >= previousValue * 1.5 && latest.value - previousValue > 0.05)
        ) {
          strongest = this.maxSignal(strongest, 'MEDIUM');
        }
      }

      if (this.isAvailabilityMetric(normalizedKey) && latest.value === 0) {
        return 'HIGH';
      }
    }

    return strongest;
  }

  private computeMLSignal(probability: number | null | undefined): 'LOW' | 'MEDIUM' | 'HIGH' {
    if (probability == null) {
      return 'LOW';
    }
    if (probability >= 0.85) {
      return 'HIGH';
    }
    if (probability >= 0.65) {
      return 'MEDIUM';
    }
    return 'LOW';
  }

  private computeFinalStatus(signals: Array<'LOW' | 'MEDIUM' | 'HIGH'>): 'Healthy' | 'Watch' | 'Risk' {
    if (signals.includes('HIGH')) {
      return 'Risk';
    }
    if (signals.includes('MEDIUM')) {
      return 'Watch';
    }
    return 'Healthy';
  }

  private computeHostAssessment(
    hostKey: string,
    prediction: DashboardPrediction | null
  ): {
    status: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable';
    explanation: string;
  } {
    const hostProblems = this.problems().filter((problem) => this.hostKey(problem.hostId, problem.host) === hostKey);
    const activeProblems = hostProblems.filter(
      (problem) => problem.active || (problem.status ?? 'ACTIVE') === 'ACTIVE'
    );
    const hostMetrics = this.metrics().filter(
      (metric) => this.hostKey(metric.hostId, metric.hostName) === hostKey
    );
    const anomalyCount = this.anomalies().filter(
      (anomaly) => this.hostKey(String(anomaly.hostid), anomaly.hostName) === hostKey
    ).length;

    const activeIncidents = activeProblems.length;
    const maxSeverity = activeProblems.reduce(
      (currentMax, problem) => Math.max(currentMax, Number(problem.severity ?? 0) || 0),
      0
    );

    const incidentSignal = this.computeIncidentSignal(activeIncidents);
    const severitySignal = this.computeSeveritySignal(maxSeverity);
    const metricSignal = this.computeMetricSignal(hostMetrics);
    let mlSignal = prediction ? this.computeMLSignal(prediction.probability) : 'LOW';

    if (
      mlSignal === 'HIGH' &&
      activeIncidents === 0 &&
      anomalyCount === 0 &&
      metricSignal !== 'HIGH'
    ) {
      mlSignal = 'MEDIUM';
    }

    const nonMlStatus = this.computeFinalStatus([incidentSignal, severitySignal, metricSignal]);

    if (!prediction) {
      if (nonMlStatus === 'Healthy') {
        return {
          status: 'Prediction unavailable',
          explanation: 'No ML prediction is available for this host. Current incidents and key metrics do not indicate an immediate issue.'
        };
      }

      return {
        status: nonMlStatus,
        explanation:
          nonMlStatus === 'Risk'
            ? 'This host already shows operational risk through incidents, severity, or critical health metrics.'
            : 'This host should be monitored because incidents, severity, or key metrics show early warning signs.'
      };
    }

    const finalStatus = this.computeFinalStatus([incidentSignal, severitySignal, metricSignal, mlSignal]);

    return {
      status: finalStatus,
      explanation: this.toPredictionExplanation(
        finalStatus,
        prediction.probability,
        activeIncidents,
        maxSeverity,
        metricSignal,
        anomalyCount,
        mlSignal
      )
    };
  }

  private toPredictionTone(
    status: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable'
  ): 'healthy' | 'watch' | 'risk' | 'unavailable' {
    switch (status) {
      case 'Risk':
        return 'risk';
      case 'Watch':
        return 'watch';
      case 'Prediction unavailable':
        return 'unavailable';
      default:
        return 'healthy';
    }
  }

  private toHostTone(
    status: 'Healthy' | 'Watch' | 'Risk' | 'Prediction unavailable'
  ): 'critical' | 'warning' | 'normal' | 'unavailable' {
    switch (status) {
      case 'Risk':
        return 'critical';
      case 'Watch':
        return 'warning';
      case 'Prediction unavailable':
        return 'unavailable';
      default:
        return 'normal';
    }
  }

  private toPredictionExplanation(
    status: 'Healthy' | 'Watch' | 'Risk',
    probability: number,
    activeIncidents: number,
    maxSeverity: number,
    metricSignal: 'LOW' | 'MEDIUM' | 'HIGH',
    anomalyCount: number,
    mlSignal: 'LOW' | 'MEDIUM' | 'HIGH'
  ): string {
    if (status === 'Risk') {
      return `Operational risk confirmed by ${activeIncidents} active incident(s), severity ${maxSeverity}, and ${metricSignal.toLowerCase()} metric health.`;
    }
    if (status === 'Watch') {
      if (mlSignal === 'HIGH' && activeIncidents === 0 && anomalyCount === 0 && metricSignal !== 'HIGH') {
        return 'High ML confidence was downgraded to Watch because there is no active incident, anomaly, or critical metric confirming the risk.';
      }
      return `Monitor this host: the current signals suggest caution, but not an immediate confirmed incident.`;
    }
    return `The host currently looks healthy. ML confidence is ${Math.round(probability * 100)}% and no strong operational signal confirms risk.`;
  }

  private maxSignal(
    current: 'LOW' | 'MEDIUM' | 'HIGH',
    incoming: 'LOW' | 'MEDIUM' | 'HIGH'
  ): 'LOW' | 'MEDIUM' | 'HIGH' {
    const rank = { LOW: 1, MEDIUM: 2, HIGH: 3 } as const;
    return rank[incoming] > rank[current] ? incoming : current;
  }

  private isPingMetric(metricKey: string): boolean {
    return metricKey.includes('icmpping') && !metricKey.includes('loss') && !metricKey.includes('sec');
  }

  private isPacketLossMetric(metricKey: string): boolean {
    return metricKey.includes('icmppingloss');
  }

  private isLatencyMetric(metricKey: string): boolean {
    return metricKey.includes('icmppingsec');
  }

  private isAvailabilityMetric(metricKey: string): boolean {
    return metricKey.includes('system') && metricKey.includes('availability');
  }

  private severityLabel(value: string): string {
    switch (value) {
      case '5':
        return 'Disaster';
      case '4':
        return 'High';
      case '3':
        return 'Average';
      case '2':
        return 'Warning';
      case '1':
        return 'Info';
      default:
        return value || 'Unknown';
    }
  }

  private severityTone(value: string): 'critical' | 'high' | 'medium' | 'warning' | 'info' | 'neutral' {
    switch (value) {
      case '5':
        return 'critical';
      case '4':
        return 'high';
      case '3':
        return 'medium';
      case '2':
        return 'warning';
      case '1':
        return 'info';
      default:
        return 'neutral';
    }
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
