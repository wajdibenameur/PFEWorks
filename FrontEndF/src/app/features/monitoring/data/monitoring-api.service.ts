import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { ApiResponse } from '../../../core/models/api-response.model';
import { CameraDevice, CreateCameraDeviceRequest } from '../../../core/models/camera-device.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { DashboardAnomaly } from '../../../core/models/dashboard-anomaly.model';
import { DashboardOverview } from '../../../core/models/dashboard-overview.model';
import { DashboardPrediction } from '../../../core/models/dashboard-prediction.model';
import { MonitoringHost } from '../../../core/models/monitoring-host.model';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { CreateSnmpDeviceRequest, SnmpDevice } from '../../../core/models/snmp-device.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { UnifiedMonitoringResponse } from '../../../core/models/unified-monitoring-response.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';

@Injectable({ providedIn: 'root' })
export class MonitoringApiService {
  private readonly monitoringBaseUrl: string;
  private readonly cameraBaseUrl: string;
  private readonly zkbioBaseUrl: string;
  private readonly dashboardBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.monitoringBaseUrl = `${config.monitoringApiUrl}/api/monitoring`;
    this.cameraBaseUrl = `${config.monitoringApiUrl}/api/cameras`;
    this.zkbioBaseUrl = `${config.monitoringApiUrl}/api/zkbio`;
    this.dashboardBaseUrl = `${config.monitoringApiUrl}/dashboard`;
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

  getMonitoringHostsResponse(): Observable<UnifiedMonitoringResponse<MonitoringHost[]>> {
    return this.http.get<UnifiedMonitoringResponse<MonitoringHost[]>>(`${this.monitoringBaseUrl}/hosts`);
  }

  getMonitoringProblemsResponse(): Observable<UnifiedMonitoringResponse<MonitoringProblem[]>> {
    return this.http.get<UnifiedMonitoringResponse<MonitoringProblem[]>>(`${this.monitoringBaseUrl}/problems`);
  }

  getMonitoringMetricsResponse(): Observable<UnifiedMonitoringResponse<UnifiedMonitoringMetric[]>> {
    return this.http.get<UnifiedMonitoringResponse<UnifiedMonitoringMetric[]>>(`${this.monitoringBaseUrl}/metrics`);
  }

  getCameraDevices(): Observable<CameraDevice[]> {
    return this.http.get<CameraDevice[]>(this.cameraBaseUrl);
  }

  createCameraDevice(payload: CreateCameraDeviceRequest): Observable<CameraDevice> {
    return this.http.post<ApiResponse<CameraDevice>>(this.cameraBaseUrl, payload)
      .pipe(map((response) => response.data as CameraDevice));
  }

  updateCameraDevice(id: number, payload: CreateCameraDeviceRequest): Observable<CameraDevice> {
    return this.http.put<ApiResponse<CameraDevice>>(`${this.cameraBaseUrl}/${id}`, payload)
      .pipe(map((response) => response.data as CameraDevice));
  }

  updateCameraDeviceEnabled(id: number, enabled: boolean): Observable<CameraDevice> {
    const action = enabled ? 'enable' : 'disable';
    return this.http.patch<ApiResponse<CameraDevice>>(`${this.cameraBaseUrl}/${id}/${action}`, {})
      .pipe(map((response) => response.data as CameraDevice));
  }

  deleteCameraDevice(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.cameraBaseUrl}/${id}`)
      .pipe(map(() => void 0));
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

  getSnmpDevices(): Observable<SnmpDevice[]> {
    return this.http.get<SnmpDevice[]>(`${this.monitoringBaseUrl}/snmp/devices`);
  }

  createSnmpDevice(payload: CreateSnmpDeviceRequest): Observable<SnmpDevice> {
    return this.http.post<ApiResponse<SnmpDevice>>(`${this.monitoringBaseUrl}/snmp/devices`, payload)
      .pipe(map((response) => response.data as SnmpDevice));
  }

  updateSnmpDevice(id: number, payload: CreateSnmpDeviceRequest): Observable<SnmpDevice> {
    return this.http.put<ApiResponse<SnmpDevice>>(`${this.monitoringBaseUrl}/snmp/devices/${id}`, payload)
      .pipe(map((response) => response.data as SnmpDevice));
  }

  updateSnmpDeviceEnabled(id: number, enabled: boolean): Observable<SnmpDevice> {
    return this.http.patch<ApiResponse<SnmpDevice>>(
      `${this.monitoringBaseUrl}/snmp/devices/${id}/enabled?enabled=${enabled}`,
      {}
    ).pipe(map((response) => response.data as SnmpDevice));
  }

  deleteSnmpDevice(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.monitoringBaseUrl}/snmp/devices/${id}`)
      .pipe(map(() => void 0));
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

