import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { StompClientService } from '../../../core/realtime/stomp-client.service';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';

@Injectable({ providedIn: 'root' })
export class MonitoringRealtimeService {
  constructor(private readonly stomp: StompClientService) {}

  problems$(): Observable<ZabbixProblem[]> {
    return this.stomp.subscribe<ZabbixProblem[]>('/topic/zabbix/problems');
  }

  metrics$(): Observable<ZabbixMetric[]> {
    return this.stomp.subscribe<ZabbixMetric[]>('/topic/zabbix/metrics');
  }
}
