import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MonitoringProblem } from '../../../core/models/monitoring-problem.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { StompClientService } from '../../../core/realtime/stomp-client.service';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZabbixMetric } from '../../../core/models/zabbix-metric.model';
import { ZabbixProblem } from '../../../core/models/zabbix-problem.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';
import { ZkBioProblem } from '../../../core/models/zkbio-problem.model';
import { UnifiedMonitoringMetric } from '../../../core/models/unified-monitoring-metric.model';

@Injectable({ providedIn: 'root' })
export class MonitoringRealtimeService {
  constructor(private readonly stomp: StompClientService) {}

  problems$(): Observable<ZabbixProblem[]> {
    return this.stomp.subscribe<ZabbixProblem[]>('/topic/zabbix/problems');
  }

  metrics$(): Observable<ZabbixMetric[]> {
    return this.stomp.subscribe<ZabbixMetric[]>('/topic/zabbix/metrics');
  }

  monitoringProblems$(): Observable<MonitoringProblem[]> {
    return this.stomp.subscribe<MonitoringProblem[]>('/topic/monitoring/problems');
  }

  monitoringMetrics$(): Observable<UnifiedMonitoringMetric[]> {
    return this.stomp.subscribe<UnifiedMonitoringMetric[]>('/topic/monitoring/metrics');
  }

  sourceAvailability$(): Observable<SourceAvailability> {
    return this.stomp.subscribe<SourceAvailability>('/topic/monitoring/sources');
  }

  zkbioProblems$(): Observable<ZkBioProblem[]> {
    return this.stomp.subscribe<ZkBioProblem[]>('/topic/zkbio/problems');
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
}
