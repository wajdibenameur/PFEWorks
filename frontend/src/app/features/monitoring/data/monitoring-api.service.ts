import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { ApiResponse } from '../../../core/models/api-response.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';

@Injectable({ providedIn: 'root' })
export class MonitoringApiService {
  private readonly monitoringBaseUrl: string;
  private readonly zabbixBaseUrl: string;

  constructor(
    private readonly http: HttpClient,
    @Inject(APP_CONFIG) config: AppConfig
  ) {
    this.monitoringBaseUrl = `${config.apiBaseUrl}/api/monitoring`;
    this.zabbixBaseUrl = `${config.apiBaseUrl}/api/zabbix`;
  }

  getActiveProblems(): Observable<ZabbixProblem[]> {
    return this.http.get<ZabbixProblem[]>(`${this.zabbixBaseUrl}/active`);
  }

  getMetrics(): Observable<ZabbixMetric[]> {
    return this.http.get<ZabbixMetric[]>(`${this.zabbixBaseUrl}/metrics`);
  }

  triggerCollection(target: CollectionTarget): Observable<ApiResponse<void>> {
    if (target === 'all') {
      return this.http.post<ApiResponse<void>>(`${this.monitoringBaseUrl}/collect`, {});
    }

    return this.http.post<ApiResponse<void>>(
      `${this.monitoringBaseUrl}/collect/${target}`,
      {}
    );
  }
}
