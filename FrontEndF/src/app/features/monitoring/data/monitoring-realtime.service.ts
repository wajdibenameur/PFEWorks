import { Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { ObserviumMetric } from '../../../core/models/observium-metric.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { StompClientService } from '../../../core/realtime/stomp-client.service';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';
import { ZkBioMetric } from '../../../core/models/zkbio-metric.model';
import { ZkBioProblem } from '../../../core/models/zkbio-problem.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import {
  matchesMonitoringSource,
  toZabbixProblem,
  toZkBioProblem
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

  observiumMetrics$(): Observable<ObserviumMetric[]> {
    return this.monitoringMetrics$().pipe(
      map((metrics) =>
        metrics
          .filter((metric) => metric.source === 'OBSERVIUM')
          .map((metric) => this.toObserviumMetric(metric))
      )
    );
  }

  zkbioMetrics$(): Observable<ZkBioMetric[]> {
    return this.monitoringMetrics$().pipe(
      map((metrics) =>
        metrics
          .filter((metric) => metric.source === 'ZKBIO')
          .map((metric) => this.toZkBioMetric(metric))
      )
    );
  }

  zkbioProblems$(): Observable<ZkBioProblem[]> {
    return this.monitoringProblemsForSource$('ZKBIO').pipe(
      map((problems) => problems.map((problem) => toZkBioProblem(problem)))
    );
  }

  zkbioAttendance$(): Observable<ZkBioAttendance[]> {
    return this.stomp.subscribe<ZkBioAttendance[]>('/topic/zkbio/attendance');
  }

  zkbioDevices$(): Observable<ServiceStatus[]> {
    return this.stomp.subscribe<ServiceStatus[]>('/topic/zkbio/devices');
  }

  zkbioStatus$(): Observable<ServiceStatus> {
    return this.stomp.subscribe<ServiceStatus>('/topic/zkbio/status');
  }

  private toObserviumMetric(metric: UnifiedMonitoringMetric): ObserviumMetric {
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

  private toZkBioMetric(metric: UnifiedMonitoringMetric): ZkBioMetric {
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

}

