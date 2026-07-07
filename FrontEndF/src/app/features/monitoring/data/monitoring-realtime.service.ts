import { Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SnmpMetric } from '../../../core/models/snmp-metric.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { StompClientService } from '../../../core/realtime/stomp-client.service';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import {
  matchesMonitoringSource,
  toZabbixProblem
} from './monitoring-source.utils';

@Injectable({ providedIn: 'root' })
export class MonitoringRealtimeService {
  constructor(private readonly stomp: StompClientService) {}

  monitoringProblemsForZabbix$(): Observable<ZabbixProblem[]> {
    return this.monitoringProblemsForSource$('ZABBIX').pipe(
      map((problems) => problems.map((problem) => toZabbixProblem(problem)))
    );
  }

  monitoringMetricsForZabbix$(): Observable<ZabbixMetric[]> {
    return this.monitoringMetricsForSource$('ZABBIX').pipe(
      map((metrics) => metrics.map((metric) => this.toZabbixMetric(metric)))
    );
  }

  monitoringProblems$(): Observable<MonitoringProblem[]> {
    return this.stomp.subscribe<MonitoringProblem[]>('/topic/monitoring/problems');
  }

  monitoringMetrics$(): Observable<UnifiedMonitoringMetric[]> {
    return this.stomp.subscribe<UnifiedMonitoringMetric[]>('/topic/monitoring/metrics');
  }

  monitoringSources$(): Observable<SourceAvailability> {
    return this.stomp.subscribe<SourceAvailability>('/topic/monitoring/sources');
  }

  monitoringProblemsForSource$(source: string): Observable<MonitoringProblem[]> {
    return this.monitoringProblems$().pipe(
      map((problems) => problems.filter((problem) => matchesMonitoringSource(problem.source, source)))
    );
  }

  monitoringMetricsForSource$(source: string): Observable<UnifiedMonitoringMetric[]> {
    return this.monitoringMetrics$().pipe(
      map((metrics) => metrics.filter((metric) => matchesMonitoringSource(metric.source, source)))
    );
  }

  monitoringSourceHealth$(source: string): Observable<SourceAvailability> {
    return this.monitoringSources$().pipe(
      filter((availability) => matchesMonitoringSource(availability.source, source))
    );
  }

  snmpMetrics$(): Observable<SnmpMetric[]> {
    return this.monitoringMetrics$().pipe(
      map((metrics) =>
        metrics
          .filter((metric) => metric.source === 'SNMP')
          .map((metric) => this.toSnmpMetric(metric))
      )
    );
  }

  private toSnmpMetric(metric: UnifiedMonitoringMetric): SnmpMetric {
    return {
      hostId: metric.hostId,
      hostName: metric.hostName,
      itemId: metric.itemId,
      metricKey: metric.metricKey,
      value: metric.value,
      timestamp: metric.timestamp,
      ip: metric.ip,
      port: metric.port
    };
  }

  private toZabbixMetric(metric: UnifiedMonitoringMetric): ZabbixMetric {
    return {
      hostId: metric.hostId,
      hostName: metric.hostName,
      itemId: metric.itemId,
      metricKey: metric.metricKey,
      value: metric.value ?? 0,
      timestamp: metric.timestamp ?? 0,
      ip: metric.ip,
      port: metric.port
    };
  }
}

