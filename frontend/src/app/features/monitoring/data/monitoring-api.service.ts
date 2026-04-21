import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { ApiResponse } from '../../../core/models/api-response.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { DashboardAnomaly } from '../../../core/models/dashboard-anomaly.model';
import { DashboardOverview } from '../../../core/models/dashboard-overview.model';
import { DashboardPrediction } from '../../../core/models/dashboard-prediction.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { UnifiedMonitoringResponse } from '../../../core/models/unified-monitoring-response.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';

@Injectable({ providedIn: 'root' })
export class MonitoringApiService {
  private readonly monitoringBaseUrl: string;
  private readonly zkbioBaseUrl: string;
  private readonly dashboardBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.monitoringBaseUrl = `${config.apiBaseUrl}/api/monitoring`;
    this.zkbioBaseUrl = `${config.apiBaseUrl}/api/zkbio`;
    this.dashboardBaseUrl = `${config.apiBaseUrl}/dashboard`;
  }

  getSourceHealth(): Observable<SourceAvailability[]> {
    return this.http.get<SourceAvailability[]>(`${this.monitoringBaseUrl}/sources/health`);
  }

  getMonitoringHosts(): Observable<MonitoringHost[]> {
    return this.getMonitoringHostsResponse().pipe(map((response) => response.data));
  }

  getMonitoringProblems(): Observable<MonitoringProblem[]> {
    return this.getMonitoringProblemsResponse().pipe(map((response) => response.data));
  }

  // Temporary compatibility wrappers kept while the Zabbix workspace still
  // expects Zabbix-shaped view models built from unified monitoring payloads.
  getZabbixMonitoringProblems(): Observable<ZabbixProblem[]> {
    return this.getMonitoringProblemsResponse().pipe(
      map((response) =>
        response.data
          .filter((problem) => problem.source === 'ZABBIX')
          .map((problem) => this.toZabbixProblem(problem))
      )
    );
  }

  getZabbixMonitoringMetrics(): Observable<ZabbixMetric[]> {
    return this.getMonitoringMetricsResponse().pipe(
      map((response) =>
        response.data
          .filter((metric) => metric.source === 'ZABBIX')
          .map((metric) => this.toZabbixMetric(metric))
      )
    );
  }

  getMonitoringHostsResponse(): Observable<UnifiedMonitoringResponse<MonitoringHost[]>> {
    return this.http.get<UnifiedMonitoringResponse<MonitoringHost[]>>(`${this.monitoringBaseUrl}/hosts`);
  }

  getMonitoringProblemsResponse(): Observable<UnifiedMonitoringResponse<MonitoringProblem[]>> {
    return this.http.get<UnifiedMonitoringResponse<MonitoringProblem[]>>(`${this.monitoringBaseUrl}/problems`);
  }

  getMonitoringMetricsResponse(): Observable<UnifiedMonitoringResponse<UnifiedMonitoringMetric[]>> {
    return this.http.get<UnifiedMonitoringResponse<UnifiedMonitoringMetric[]>>(`${this.monitoringBaseUrl}/metrics`);
  }

  getZkBioStatus(): Observable<ServiceStatus> {
    return this.http.get<ServiceStatus>(`${this.zkbioBaseUrl}/status`);
  }

  getZkBioDevices(): Observable<ServiceStatus[]> {
    return this.http.get<ServiceStatus[]>(`${this.zkbioBaseUrl}/devices`);
  }

  getZkBioAttendance(): Observable<ZkBioAttendance[]> {
    return this.http.get<ZkBioAttendance[]>(`${this.zkbioBaseUrl}/attendance`);
  }

  getDashboardOverview(): Observable<DashboardOverview> {
    return this.http.get<DashboardOverview>(`${this.dashboardBaseUrl}/overview`);
  }

  getPredictions(): Observable<DashboardPrediction[]> {
    return this.http.get<DashboardPrediction[]>(`${this.dashboardBaseUrl}/predictions`);
  }

  getAnomalies(): Observable<DashboardAnomaly[]> {
    return this.http.get<DashboardAnomaly[]>(`${this.dashboardBaseUrl}/anomalies`);
  }

  triggerCollection(target: CollectionTarget): Observable<ApiResponse<void>> {
    if (target === 'all') {
      return this.http.post<ApiResponse<void>>(`${this.monitoringBaseUrl}/collect`, {});
    }
    if (target === 'zkbio') {
      return this.http.post<ApiResponse<void>>(`${this.zkbioBaseUrl}/collect`, {});
    }

    return this.http.post<ApiResponse<void>>(
      `${this.monitoringBaseUrl}/collect/${target}`,
      {}
    );
  }

  private toZabbixProblem(problem: MonitoringProblem): ZabbixProblem {
    return {
      problemId: problem.problemId ?? problem.id,
      host: problem.hostName ?? problem.hostId ?? 'UNKNOWN',
      port: problem.port ?? null,
      hostId: problem.hostId ?? null,
      description: problem.description ?? 'No description',
      severity: problem.severity ?? 'UNKNOWN',
      active: problem.active,
      source: problem.source,
      eventId: problem.eventId ?? 0,
      ip: problem.ip ?? null,
      startedAt: problem.startedAt ?? null,
      startedAtFormatted: problem.startedAtFormatted ?? null,
      resolvedAt: problem.resolvedAt ?? null,
      resolvedAtFormatted: problem.resolvedAtFormatted ?? null,
      status: problem.status ?? (problem.active ? 'ACTIVE' : 'RESOLVED')
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
