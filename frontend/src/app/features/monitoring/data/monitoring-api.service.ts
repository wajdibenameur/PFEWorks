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
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';
import { ZkBioProblem } from '../../../core/models/zkbio-problem.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';

@Injectable({ providedIn: 'root' })
export class MonitoringApiService {
  private readonly monitoringBaseUrl: string;
  private readonly zabbixBaseUrl: string;
  private readonly zkbioBaseUrl: string;
  private readonly dashboardBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.monitoringBaseUrl = `${config.apiBaseUrl}/api/monitoring`;
    this.zabbixBaseUrl = `${config.apiBaseUrl}/api/zabbix`;
    this.zkbioBaseUrl = `${config.apiBaseUrl}/api/zkbio`;
    this.dashboardBaseUrl = `${config.apiBaseUrl}/dashboard`;
  }

  getActiveProblems(): Observable<ZabbixProblem[]> {
    return this.http.get<ZabbixProblem[]>(`${this.zabbixBaseUrl}/active`);
  }

  getMetrics(): Observable<ZabbixMetric[]> {
    return this.http.get<ZabbixMetric[]>(`${this.zabbixBaseUrl}/metrics`);
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

  getMonitoringMetrics(): Observable<UnifiedMonitoringMetric[]> {
    return this.getMonitoringMetricsResponse().pipe(map((response) => response.data));
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

  getZkBioProblems(): Observable<ZkBioProblem[]> {
    return this.http.get<ZkBioProblem[]>(`${this.zkbioBaseUrl}/problems`);
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
}
