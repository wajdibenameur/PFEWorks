import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { ZabbixWorkspaceStore } from '../state/zabbix-workspace.store';

@Component({
  selector: 'app-monitoring-zabbix-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-zabbix-page.component.html',
  styleUrl: './monitoring-zabbix-page.component.scss',
  providers: [ZabbixWorkspaceStore],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringZabbixPageComponent implements OnInit {
  protected readonly facade = inject(ZabbixWorkspaceStore);
  protected readonly expandedMetricCategories = new Set<string>();

  protected readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect Zabbix', target: 'zabbix' }
  ];

  ngOnInit(): void {
    this.facade.loadSnapshot();
    this.facade.bindRealtime();
  }

  protected triggerCollection(target: CollectionTarget): void {
    this.facade.triggerCollection(target);
  }

  protected updateProblemSearch(event: Event): void {
    this.facade.updateProblemSearch((event.target as HTMLInputElement).value);
  }

  protected updateProblemSeverity(event: Event): void {
    this.facade.updateProblemSeverityFilter(
      (event.target as HTMLSelectElement).value as 'ALL' | '5' | '4'
    );
  }

  protected updateProblemStatus(event: Event): void {
    this.facade.updateProblemStatusFilter(
      (event.target as HTMLSelectElement).value as 'ALL' | 'ACTIVE' | 'RESOLVED'
    );
  }

  protected updateMetricSearch(event: Event): void {
    this.facade.updateMetricSearch((event.target as HTMLInputElement).value);
  }

  protected hasValidTimestamp(value: number | null | undefined): value is number {
    return typeof value === 'number' && value > 0;
  }

  protected formatDuration(
    startedAt: number | null | undefined,
    resolvedAt: number | null | undefined,
    isActive: boolean
  ): string {
    if (!this.hasValidTimestamp(startedAt)) {
      return 'Duration unavailable';
    }

    const end = this.hasValidTimestamp(resolvedAt)
      ? resolvedAt
      : isActive
        ? Math.floor(Date.now() / 1000)
        : null;

    if (!end || end <= startedAt) {
      return isActive ? 'In progress' : 'Duration unavailable';
    }

    const totalMinutes = Math.floor((end - startedAt) / 60);
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;

    const parts: string[] = [];
    if (days > 0) {
      parts.push(`${days}d`);
    }
    if (hours > 0) {
      parts.push(`${hours}h`);
    }
    if (minutes > 0 || parts.length === 0) {
      parts.push(`${minutes}m`);
    }
    return parts.join(' ');
  }

  protected hostHealthStatus(
    activeIncidents: number,
    anomalyCount: number,
    riskStatus: string | null | undefined
  ): 'Healthy' | 'Warning' | 'Critical' {
    if (activeIncidents > 0 || riskStatus === 'Risk') {
      return 'Critical';
    }
    if (anomalyCount > 0 || riskStatus === 'Watch') {
      return 'Warning';
    }
    return 'Healthy';
  }

  protected healthTone(status: 'Healthy' | 'Warning' | 'Critical'): 'healthy' | 'warning' | 'critical' {
    if (status === 'Critical') {
      return 'critical';
    }
    if (status === 'Warning') {
      return 'warning';
    }
    return 'healthy';
  }

  protected visibleMetricsFor<T extends { label: string }>(category: string, metrics: T[]): T[] {
    if (this.expandedMetricCategories.has(category)) {
      return metrics;
    }
    return this.prioritizeMetrics(metrics).slice(0, 6);
  }

  protected canExpandMetrics(category: string, total: number): boolean {
    return !this.expandedMetricCategories.has(category) && total > 6;
  }

  protected canCollapseMetrics(category: string, total: number): boolean {
    return this.expandedMetricCategories.has(category) && total > 6;
  }

  protected toggleMetrics(category: string): void {
    if (this.expandedMetricCategories.has(category)) {
      this.expandedMetricCategories.delete(category);
      return;
    }
    this.expandedMetricCategories.add(category);
  }

  protected hiddenMetricsCount(category: string, total: number): number {
    if (this.expandedMetricCategories.has(category) || total <= 6) {
      return 0;
    }
    return total - Math.min(total, 6);
  }

  protected updateHostSearch(event: Event): void {
    this.facade.updateHostSearch((event.target as HTMLInputElement).value);
  }

  protected selectHost(hostKey: string): void {
    this.facade.selectHost(hostKey);
  }

  private prioritizeMetrics<T extends { label: string }>(metrics: T[]): T[] {
    const relevant: T[] = [];
    const secondary: T[] = [];

    for (const metric of metrics) {
      if (this.isBusinessRelevantMetric(metric.label)) {
        relevant.push(metric);
      } else {
        secondary.push(metric);
      }
    }

    return [...relevant, ...secondary];
  }

  private isBusinessRelevantMetric(label: string): boolean {
    const normalized = label.toLowerCase();
    return [
      'availability',
      'ping',
      'packet loss',
      'latency',
      'cpu',
      'memory',
      'disk',
      'interface',
      'uptime'
    ].some((keyword) => normalized.includes(keyword));
  }
}
