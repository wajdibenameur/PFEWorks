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

type SnmpCategory =
  | 'SERVER'
  | 'ACCESS_CONTROL'
  | 'UPS'
  | 'PRINTER'
  | 'SWITCH'
  | 'ROUTER'
  | 'FIREWALL'
  | 'LOAD_BALANCER'
  | 'WIFI_ACCESS_POINT'
  | 'NETWORK_CONTROLLER'
  | 'OTHER';
type SnmpCoverage = 'native' | 'synthetic' | 'not_applicable' | 'unknown';

interface SnmpCategoryGroup {
  category: SnmpCategory;
  total: number;
  down: number;
  up: number;
  hosts: MonitoringHost[];
}

interface SnmpMetricGroup {
  key: string;
  label: string;
  sampleCount: number;
  latestValue: number | null;
  latestTimestamp: number | null;
  hosts: number;
}

type ProblemRecencyBadge = 'NEW' | 'RECENT' | null;
type ProblemTypeTone = 'critical' | 'warning' | 'info';
type ProblemSeverityTone = 'critical' | 'warning' | 'info' | 'neutral';
type ProblemFilterType = 'ALL' | 'CPU' | 'UPS' | 'PRINTER' | 'INTERFACE' | 'DOWN';

@Component({
  selector: 'app-monitoring-snmp-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-snmp-page.component.html',
  styleUrls: ['./monitoring-snmp-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringSnmpPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;
  private loadGeneration = 0;
  private readonly categoryOrder: SnmpCategory[] = [
    'SERVER',
    'ACCESS_CONTROL',
    'UPS',
    'PRINTER',
    'SWITCH',
    'ROUTER',
    'FIREWALL',
    'LOAD_BALANCER',
    'WIFI_ACCESS_POINT',
    'NETWORK_CONTROLLER'
  ];

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);
  readonly hosts = signal<MonitoringHost[]>([]);
  readonly problems = signal<MonitoringProblem[]>([]);
  readonly selectedProblemFilter = signal<ProblemFilterType>('ALL');
  readonly metrics = signal<UnifiedMonitoringMetric[]>([]);
  readonly sourceAvailability = signal<SourceAvailability | null>(null);
  readonly hostsFreshness = signal('snapshot_missing');
  readonly problemsFreshness = signal('snapshot_missing');
  readonly metricsFreshness = signal('snapshot_missing');
  readonly metricsCoverage = signal<SnmpCoverage>('unknown');
  readonly monitoringDegraded = signal(false);
  readonly nowMs = signal(Date.now());

  readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect SNMP', target: 'snmp' }
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
    totalHosts: this.sortedHosts().length,
    downHosts: this.sortedHosts().filter((host) => this.isDownStatus(this.normalizeStatus(host.status))).length,
    activeAlerts: this.problems().filter((problem) => problem.active).length,
    totalMetrics: this.metrics().length
  }));

  readonly sortedHosts = computed(() =>
    [...this.hosts()]
      .filter((host) => this.normalizeCategory(host.category) !== 'OTHER')
      .sort((left, right) =>
        this.hostLabel(left).localeCompare(this.hostLabel(right))
      )
  );

  readonly categoryGroups = computed<SnmpCategoryGroup[]>(() => {
    const groups = new Map<SnmpCategory, SnmpCategoryGroup>(
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
      if (this.isDownStatus(status)) {
        group.down += 1;
      }
      if (this.isUpStatus(status)) {
        group.up += 1;
      }

      group.hosts.push(host);
    }

    return this.categoryOrder.map((category) => groups.get(category)!);
  });

  readonly filteredProblems = computed(() =>
    this.problems().filter((problem) => this.matchesProblemFilter(problem, this.selectedProblemFilter()))
  );

  readonly sortedProblems = computed(() =>
    [...this.filteredProblems()].sort((left, right) =>
      this.problemTime(right) - this.problemTime(left)
    )
  );

  readonly metricGroups = computed<SnmpMetricGroup[]>(() => {
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
    window.setInterval(() => this.nowMs.set(Date.now()), 60000);
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
          extractApiErrorMessage(error, 'Unable to trigger SNMP collection.')
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
          hostsResponse.data.filter((host) => matchesMonitoringSource(host.source, 'SNMP'))
        );
        this.problems.set(
          problemsResponse.data.filter((problem) => matchesMonitoringSource(problem.source, 'SNMP'))
        );
        this.metrics.set(
          metricsResponse.data.filter((metric) => matchesMonitoringSource(metric.source, 'SNMP'))
        );
        this.sourceAvailability.set(findSourceAvailability(sourceHealth, 'SNMP'));
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

    this.realtime.monitoringProblemsForSource$('SNMP')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (snmpProblems) => {
        if (snmpProblems.length === 0) {
          return;
        }

        this.problems.set(this.mergeProblems(this.problems(), snmpProblems));
        if (this.shouldMarkLive(this.problemsFreshness())) {
          this.problemsFreshness.set('live');
        }
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'SNMP realtime problem stream is unavailable.')
        );
      }
    });

    this.realtime.monitoringMetricsForSource$('SNMP')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (snmpMetrics) => {
        if (snmpMetrics.length === 0) {
          return;
        }

        this.metrics.set(this.mergeMetrics(this.metrics(), snmpMetrics));
        if (this.shouldMarkLive(this.metricsFreshness())) {
          this.metricsFreshness.set('live');
        }
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'SNMP realtime metrics stream is unavailable.')
        );
      }
    });

    this.realtime.monitoringSourceHealth$('SNMP').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.sourceAvailability.set(incoming);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'SNMP source health stream is unavailable.')
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

  metricTrackBy(_: number, metric: SnmpMetricGroup): string {
    return metric.key;
  }

  hostAddress(host: MonitoringHost): string {
    return host.ip ?? 'IP_UNKNOWN';
  }

  hostCategory(host: MonitoringHost): SnmpCategory {
    return this.normalizeCategory(host.category);
  }

  hostIsDown(host: MonitoringHost): boolean {
    return this.isDownStatus(this.normalizeStatus(host.status));
  }

  metricValue(metric: SnmpMetricGroup): string {
    if (metric.latestValue == null) {
      return 'N/A';
    }
    return Number.isInteger(metric.latestValue)
      ? String(metric.latestValue)
      : metric.latestValue.toFixed(2);
  }

  metricTimestamp(metric: SnmpMetricGroup): string {
    if (!metric.latestTimestamp) {
      return 'Unknown timestamp';
    }
    return new Date(metric.latestTimestamp * 1000).toLocaleString();
  }

  problemLastCheck(problem: MonitoringProblem): string {
    if (problem.lastObservedAtFormatted && problem.lastObservedAtFormatted.trim().length > 0) {
      return problem.lastObservedAtFormatted;
    }
    if (problem.lastObservedAt) {
      return new Date(problem.lastObservedAt * 1000).toLocaleString();
    }
    return 'Unknown';
  }

  problemDetectedAt(problem: MonitoringProblem): string {
    if (problem.startedAtFormatted && problem.startedAtFormatted.trim().length > 0) {
      return problem.startedAtFormatted;
    }
    if (problem.startedAt) {
      return new Date(problem.startedAt * 1000).toLocaleString();
    }
    return 'Unknown';
  }

  problemRecencyBadge(problem: MonitoringProblem): ProblemRecencyBadge {
    const reference = problem.lastObservedAt ?? problem.startedAt;
    if (!reference) {
      return null;
    }
    const ageMs = this.nowMs() - reference * 1000;
    if (ageMs < 0 || ageMs > 10 * 60 * 1000) {
      return null;
    }

    const firstDetectionAgeMs = problem.startedAt ? this.nowMs() - problem.startedAt * 1000 : Number.POSITIVE_INFINITY;
    return firstDetectionAgeMs >= 0 && firstDetectionAgeMs <= 10 * 60 * 1000 ? 'NEW' : 'RECENT';
  }

  problemTypeLabel(problem: MonitoringProblem): string {
    const key = (problem.problemId ?? '').toLowerCase();
    const description = (problem.description ?? '').toLowerCase();

    if (key.includes('cpu-high') || description.includes('cpu utilization is high')) {
      return 'CPU High';
    }
    if (key.includes('memory-high') || description.includes('memory utilization is high')) {
      return 'Memory High';
    }
    if (key.includes('ups-battery-low') || description.includes('ups battery level is low')) {
      return 'UPS Battery Low';
    }
    if (key.includes('ups-load-high') || description.includes('ups output load is high')) {
      return 'UPS Load High';
    }
    if (key.includes('printer-toner-low') || description.includes('printer toner level is low')) {
      return 'Printer Toner Low';
    }
    if (key.includes('interface-utilization-') || description.includes('utilization is high')) {
      return 'Interface Saturation';
    }
    if (key.includes('interface-errors-') || description.includes('recent errors')) {
      return 'Interface Errors';
    }
    if (key.startsWith('obs-snmp-') && description.includes('device status is down')) {
      return 'Device Down';
    }
    if (key.startsWith('obs-snmp-') && description.includes('device status is degraded')) {
      return 'Device Degraded';
    }
    return 'Monitoring Alert';
  }

  readonly problemFilterOptions: Array<{ value: ProblemFilterType; label: string }> = [
    { value: 'ALL', label: 'All' },
    { value: 'CPU', label: 'CPU' },
    { value: 'UPS', label: 'UPS' },
    { value: 'PRINTER', label: 'Printer' },
    { value: 'INTERFACE', label: 'Interface' },
    { value: 'DOWN', label: 'Down' }
  ];

  setProblemFilter(filter: ProblemFilterType): void {
    this.selectedProblemFilter.set(filter);
  }

  problemTypeTone(problem: MonitoringProblem): ProblemTypeTone {
    const severity = Number.parseInt(problem.severity ?? '', 10);
    if (Number.isFinite(severity)) {
      return severity >= 5 ? 'critical' : severity >= 4 ? 'warning' : 'info';
    }
    return 'info';
  }

  problemSeverityLabel(problem: MonitoringProblem): string {
    const severity = Number.parseInt(problem.severity ?? '', 10);
    if (!Number.isFinite(severity)) {
      return problem.severity ?? 'Unknown';
    }
    if (severity >= 5) {
      return `Critical (${severity})`;
    }
    if (severity >= 4) {
      return `Warning (${severity})`;
    }
    if (severity >= 1) {
      return `Info (${severity})`;
    }
    return String(severity);
  }

  problemSeverityTone(problem: MonitoringProblem): ProblemSeverityTone {
    const severity = Number.parseInt(problem.severity ?? '', 10);
    if (!Number.isFinite(severity)) {
      return 'neutral';
    }
    if (severity >= 5) {
      return 'critical';
    }
    if (severity >= 4) {
      return 'warning';
    }
    if (severity >= 1) {
      return 'info';
    }
    return 'neutral';
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

  private normalizeCategory(category: string | null | undefined): SnmpCategory {
    const normalized = (category ?? '').toUpperCase();
    if (this.categoryOrder.includes(normalized as SnmpCategory)) {
      return normalized as SnmpCategory;
    }
    return 'OTHER';
  }

  categoryLabel(category: SnmpCategory): string {
    switch (category) {
      case 'SERVER':
        return 'Servers';
      case 'ACCESS_CONTROL':
        return 'Access Control';
      case 'UPS':
        return 'UPS / Power';
      case 'SWITCH':
        return 'Switches';
      case 'ROUTER':
        return 'Routers';
      case 'FIREWALL':
        return 'Firewalls';
      case 'LOAD_BALANCER':
        return 'Load Balancers';
      case 'WIFI_ACCESS_POINT':
        return 'Wi-Fi Access Points';
      case 'NETWORK_CONTROLLER':
        return 'Network Controllers';
      case 'PRINTER':
        return 'Printers';
      default:
        return 'Other';
    }
  }

  private normalizeStatus(status: string | null | undefined): string {
    const normalized = (status ?? '').toUpperCase();
    if (normalized === 'AVAILABLE' || normalized === 'NORMAL') {
      return 'UP';
    }
    if (normalized === 'UNAVAILABLE' || normalized === 'ERROR') {
      return 'DOWN';
    }
    return normalized;
  }

  private isDownStatus(status: string): boolean {
    return status === 'DOWN' || status === 'DEGRADED';
  }

  private isUpStatus(status: string): boolean {
    return status === 'UP';
  }

  private problemTime(problem: MonitoringProblem): number {
    return problem.lastObservedAt ?? problem.startedAt ?? problem.eventId ?? 0;
  }

  private matchesProblemFilter(problem: MonitoringProblem, filter: ProblemFilterType): boolean {
    if (filter === 'ALL') {
      return true;
    }
    const label = this.problemTypeLabel(problem).toUpperCase();
    switch (filter) {
      case 'CPU':
        return label.includes('CPU');
      case 'UPS':
        return label.includes('UPS');
      case 'PRINTER':
        return label.includes('PRINTER');
      case 'INTERFACE':
        return label.includes('INTERFACE');
      case 'DOWN':
        return label.includes('DOWN') || label.includes('DEGRADED');
      default:
        return true;
    }
  }

  private metricLabel(metricKey: string): string {
    const key = metricKey.toLowerCase();
    const catalog: Array<{ test: (k: string) => boolean; label: string }> = [
      { test: (k) => k === 'snmp.availability', label: 'SNMP Availability' },
      { test: (k) => k === 'snmp.cpu.percent', label: 'CPU Utilization (%)' },
      { test: (k) => k === 'snmp.memory.percent', label: 'Memory Utilization (%)' },
      { test: (k) => k === 'snmp.uptime.seconds', label: 'System Uptime (s)' },
      { test: (k) => /^snmp\.interface\.\d+\.in\.mbps$/.test(k), label: 'Interface Inbound Throughput (Mbps)' },
      { test: (k) => /^snmp\.interface\.\d+\.out\.mbps$/.test(k), label: 'Interface Outbound Throughput (Mbps)' },
      { test: (k) => /^snmp\.interface\.\d+\.utilization\.percent$/.test(k), label: 'Interface Utilization (%)' },
      { test: (k) => /^snmp\.interface\.\d+\.in\.errors$/.test(k), label: 'Interface Inbound Errors' },
      { test: (k) => /^snmp\.interface\.\d+\.out\.errors$/.test(k), label: 'Interface Outbound Errors' },
      { test: (k) => k === 'snmp.printer.status.code', label: 'Printer Status Code' },
      { test: (k) => k === 'snmp.printer.toner.percent', label: 'Printer Toner Level (%)' },
      { test: (k) => k === 'snmp.printer.pages.total', label: 'Printer Total Pages' },
      { test: (k) => k === 'snmp.ups.battery.status', label: 'UPS Battery Status' },
      { test: (k) => k === 'snmp.ups.battery.percent', label: 'UPS Battery Level (%)' },
      { test: (k) => k === 'snmp.ups.runtime.minutes', label: 'UPS Runtime Remaining (min)' },
      { test: (k) => k === 'snmp.ups.input.voltage', label: 'UPS Input Voltage (V)' },
      { test: (k) => k === 'snmp.ups.output.voltage', label: 'UPS Output Voltage (V)' },
      { test: (k) => k === 'snmp.ups.output.load.percent', label: 'UPS Output Load (%)' },
      { test: (k) => k === 'snmp.ups.output.frequency.hz', label: 'UPS Output Frequency (Hz)' },
      { test: (k) => k.startsWith('system.cpu.util'), label: 'CPU Utilization (%)' },
      { test: (k) => k.startsWith('system.cpu.load[percpu,avg1]'), label: 'CPU Load (1 min)' },
      { test: (k) => k.startsWith('system.cpu.load[percpu,avg5]'), label: 'CPU Load (5 min)' },
      { test: (k) => k.startsWith('system.cpu.load[percpu,avg15]'), label: 'CPU Load (15 min)' },
      { test: (k) => k.startsWith('system.cpu.load'), label: 'CPU Load' },
      { test: (k) => k.startsWith('vm.memory.size[pavailable]'), label: 'Memory Available (%)' },
      { test: (k) => k.startsWith('vm.memory.size[available]'), label: 'Memory Available' },
      { test: (k) => k.startsWith('vm.memory.size[used]'), label: 'Memory Used' },
      { test: (k) => k.startsWith('vm.memory.util'), label: 'Memory Utilization (%)' },
      { test: (k) => k.startsWith('system.swap'), label: 'Swap Usage' },
      { test: (k) => k.startsWith('vfs.fs.size[/,pused]'), label: 'Disk Used (%)' },
      { test: (k) => k.startsWith('vfs.fs.size[/,free]'), label: 'Disk Free Space' },
      { test: (k) => k.startsWith('vfs.fs.size[/,used]'), label: 'Disk Used Space' },
      { test: (k) => k.startsWith('icmppingsec'), label: 'Network Latency (s)' },
      { test: (k) => k.startsWith('icmppingloss'), label: 'Packet Loss (%)' },
      { test: (k) => k.startsWith('icmpping'), label: 'Ping Availability' },
      { test: (k) => k.startsWith('net.if.in.errors'), label: 'Inbound Network Errors' },
      { test: (k) => k.startsWith('net.if.out.errors'), label: 'Outbound Network Errors' },
      { test: (k) => k.startsWith('net.if.in'), label: 'Inbound Throughput' },
      { test: (k) => k.startsWith('net.if.out'), label: 'Outbound Throughput' },
      { test: (k) => k.startsWith('system.uptime'), label: 'System Uptime' },
      { test: (k) => k.startsWith('proc.num'), label: 'Process Count' },
      { test: (k) => k.startsWith('agent.ping'), label: 'Agent Availability' }
    ];
    const match = catalog.find((entry) => entry.test(key));
    if (match) {
      return match.label;
    }

    return metricKey
      .replace(/[\[\]\._]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private readMetadataValue(values: Record<string, string> | null | undefined): string {
    return values?.['SNMP'] ?? values?.['SNMP'] ?? 'snapshot_missing';
  }

  private readCoverageValue(
    values: Record<string, string> | null | undefined
  ): SnmpCoverage {
    const coverage = (values?.['SNMP'] ?? values?.['SNMP'] ?? '').toLowerCase();
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


