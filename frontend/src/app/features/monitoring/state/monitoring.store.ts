import { Injectable, computed, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
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
  hostname: string;
  ip: string;
  port: number | null;
  source: MonitoringSource;
  metricKeys: Set<string>;
  problemCount: number;
  lastMetricTimestamp: number | null;
}

@Injectable()
export class MonitoringStore {
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);

  readonly problems = signal<ZabbixProblem[]>([]);
  readonly metrics = signal<ZabbixMetric[]>([]);

  readonly assets = computed<GlobalAssetVm[]>(() =>
    this.buildAssets(this.problems(), this.metrics())
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
    return {
      totalAlerts: problems.length,
      critical: problems.filter((problem) => problem.severity === '5').length,
      high: problems.filter((problem) => problem.severity === '4').length,
      medium: problems.filter((problem) => problem.severity === '3').length,
      warning: problems.filter((problem) => problem.severity === '2').length,
      info: problems.filter((problem) => problem.severity === '1').length
    };
  });

  readonly sourceHealth = computed<SourceHealthVm[]>(() => {
    const zabbixAssets = this.assets().filter((asset) => asset.source === 'ZABBIX');
    return [
      {
        source: 'ZABBIX',
        total: zabbixAssets.length,
        down: zabbixAssets.filter((asset) => asset.status === 'DOWN').length,
        coverage: 'REAL',
        note: 'Computed from /api/zabbix/active + /api/zabbix/metrics'
      },
      {
        source: 'OBSERVIUM',
        total: null,
        down: null,
        coverage: 'MISSING_BACKEND_READ_ENDPOINT',
        note: 'Collection endpoint exists but no read endpoint is currently exposed'
      },
      {
        source: 'CAMERA',
        total: null,
        down: null,
        coverage: 'MISSING_BACKEND_READ_ENDPOINT',
        note: 'Collection endpoint exists but no read endpoint is currently exposed'
      },
      {
        source: 'ZKBIO',
        total: null,
        down: null,
        coverage: 'MISSING_BACKEND_READ_ENDPOINT',
        note: 'No read endpoint currently connected in frontend'
      }
    ];
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
      status: 'PARTIAL',
      detail: 'Totals are currently derived from Zabbix read endpoints only.'
    },
    {
      title: 'Server/Printer Breakdown',
      status: 'ESTIMATED',
      detail:
        'Category is inferred from hostname and metric keys until backend provides a unified typed asset endpoint.'
    },
    {
      title: 'Alerts Summary',
      status: 'REAL',
      detail: 'Built from /api/zabbix/active and realtime problem streams.'
    },
    {
      title: 'Asset Inventory',
      status: 'PARTIAL',
      detail:
        'Hostnames, addresses, and IPs come from current Zabbix endpoints. Other sources need read APIs.'
    }
  ]);

  constructor(
    private readonly api: MonitoringApiService,
    private readonly realtime: MonitoringRealtimeService
  ) {}

  loadSnapshot(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      problems: this.api.getActiveProblems(),
      metrics: this.api.getMetrics()
    }).subscribe({
      next: ({ problems, metrics }) => {
        this.problems.set(this.mergeProblems([], problems));
        this.metrics.set(this.mergeMetrics([], metrics));
        this.lastRefresh.set(new Date());
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set(
          'Unable to load monitoring data. Check backend availability and CORS settings.'
        );
        this.isLoading.set(false);
      }
    });
  }

  bindRealtime(): void {
    this.realtime.problems$().subscribe({
      next: (incoming) => {
        this.problems.set(this.mergeProblems(this.problems(), incoming));
      }
    });

    this.realtime.metrics$().subscribe({
      next: (incoming) => {
        this.metrics.set(this.mergeMetrics(this.metrics(), incoming));
      }
    });
  }

  triggerCollection(target: CollectionTarget): void {
    this.errorMessage.set(null);

    this.api.triggerCollection(target).subscribe({
      next: () => this.loadSnapshot(),
      error: () => {
        this.errorMessage.set(
          `Collection request failed for "${target}". Verify backend endpoint status.`
        );
      }
    });
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

  private problemKey(problem: ZabbixProblem): string {
    if (problem.problemId) {
      return problem.problemId;
    }
    return String(problem.eventId ?? problem.description);
  }

  private metricKey(metric: ZabbixMetric): string {
    return `${metric.hostId}:${metric.itemId}:${metric.timestamp}`;
  }

  private buildAssets(problems: ZabbixProblem[], metrics: ZabbixMetric[]): GlobalAssetVm[] {
    const hostMap = new Map<string, HostAccumulator>();

    for (const metric of metrics) {
      const host = this.normalizeHost(metric.hostName || metric.hostId);
      const current = hostMap.get(host) ?? {
        hostname: host,
        ip: metric.ip || 'IP_UNKNOWN',
        port: metric.port ?? null,
        source: 'ZABBIX' as MonitoringSource,
        metricKeys: new Set<string>(),
        problemCount: 0,
        lastMetricTimestamp: null
      };

      if (!current.ip || current.ip === 'IP_UNKNOWN') {
        current.ip = metric.ip || current.ip;
      }
      if (current.port == null && metric.port != null) {
        current.port = metric.port;
      }
      current.metricKeys.add(metric.metricKey);
      current.lastMetricTimestamp = Math.max(
        current.lastMetricTimestamp ?? 0,
        metric.timestamp || 0
      );

      hostMap.set(host, current);
    }

    for (const problem of problems) {
      const host = this.normalizeHost(problem.host || problem.hostId || problem.problemId);
      const current = hostMap.get(host) ?? {
        hostname: host,
        ip: problem.ip || 'IP_UNKNOWN',
        port: problem.port ?? null,
        source: 'ZABBIX' as MonitoringSource,
        metricKeys: new Set<string>(),
        problemCount: 0,
        lastMetricTimestamp: null
      };

      if (!current.ip || current.ip === 'IP_UNKNOWN') {
        current.ip = problem.ip || current.ip;
      }
      if (current.port == null && problem.port != null) {
        current.port = problem.port;
      }
      current.problemCount += 1;
      hostMap.set(host, current);
    }

    return Array.from(hostMap.values())
      .map((entry) => {
        const category = this.inferCategory(entry.hostname, entry.metricKeys);
        const hasActiveAlert = entry.problemCount > 0;

        return {
          id: `${entry.source}:${entry.hostname}`,
          hostname: entry.hostname,
          address:
            entry.ip && entry.port != null && entry.ip !== 'IP_UNKNOWN'
              ? `${entry.ip}:${entry.port}`
              : entry.ip,
          ip: entry.ip,
          port: entry.port,
          source: entry.source,
          category,
          status: hasActiveAlert ? 'DOWN' : 'UP',
          hasActiveAlert,
          problemCount: entry.problemCount,
          lastMetricTimestamp: entry.lastMetricTimestamp
        } as GlobalAssetVm;
      })
      .sort((a, b) => a.hostname.localeCompare(b.hostname));
  }

  private normalizeHost(host: string | null | undefined): string {
    if (!host || !host.trim()) {
      return 'UNKNOWN_HOST';
    }
    return host.trim();
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
}
