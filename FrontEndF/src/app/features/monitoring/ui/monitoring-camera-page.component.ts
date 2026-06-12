import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CameraDevice, CreateCameraDeviceRequest } from '../../../core/models/camera-device.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { RealtimeConnectionStore } from '../../../core/realtime/realtime-connection.store';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import { CameraRealtimeService, CameraRealtimeUpdate } from '../data/camera-realtime.service';
import { findSourceAvailability } from '../data/monitoring-source.utils';
import { CameraRealtimeViewModel, RealtimeEntityStore } from '../state/camera-realtime.models';
import { RealtimeDataState } from '../state/global-monitoring.models';

@Component({
  selector: 'app-monitoring-camera-page',
  imports: [CommonModule, FormsModule, CollectionControlBarComponent],
  templateUrl: './monitoring-camera-page.component.html',
  styleUrls: ['./monitoring-camera-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringCameraPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AUTH_CONTEXT);
  private readonly config = inject<AppConfig>(APP_CONFIG);
  private readonly realtimeConnection = inject(RealtimeConnectionStore);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;
  private loadGeneration = 0;
  private heartbeatIntervalId: number | null = null;
  private lastNoDeltaLogBucket = -1;

  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);
  readonly nowMs = signal(Date.now());
  readonly activeCameraId = signal<number | null>(null);
  readonly cameraStore = signal<RealtimeEntityStore<CameraRealtimeViewModel>>({
    entities: new Map<string, CameraRealtimeViewModel>(),
    realtimeState: 'unchanged',
    lastUpdate: undefined
  });
  readonly cameraRows = computed(() =>
    [...this.cameraStore().entities.values()].sort((left, right) =>
      `${left.camera.ip ?? ''}:${left.camera.port ?? ''}`.localeCompare(`${right.camera.ip ?? ''}:${right.camera.port ?? ''}`)
    )
  );
  readonly cameraDevices = computed(() => this.cameraRows().map((vm) => vm.camera));
  readonly sourceAvailability = signal<SourceAvailability | null>(null);
  readonly canManageHosts = computed(() =>
    this.auth.arePermissionsLoaded() && this.auth.hasPermission('MANAGE_HOSTS')
  );
  readonly cameraForm = signal<CreateCameraDeviceRequest>({
    ipAddress: '',
    name: '',
    site: '',
    type: 'IP_CAMERA',
    port: 554,
    enabled: true
  });

  readonly collectionActions: CollectionActionVm[] = [
    { label: 'Scanner Camera', target: 'camera' }
  ];

  readonly summary = computed(() => {
    const devices = this.cameraDevices();
    return {
      total: devices.length,
      reachable: devices.filter((device) => device.reachable).length,
      persisted: devices.filter((device) => device.persisted).length,
      snmpEnabled: devices.filter((device) => device.snmpEnabled).length,
      protocols: new Set(
        devices
          .map((device) => device.protocol?.trim())
          .filter((protocol): protocol is string => Boolean(protocol))
      ).size
    };
  });

  readonly hasDevices = computed(() => this.cameraDevices().length > 0);

  constructor(
    private readonly api: MonitoringApiService,
    private readonly realtime: MonitoringRealtimeService,
    private readonly cameraRealtime: CameraRealtimeService
  ) {
    this.destroyRef.onDestroy(() => {
      this.clearScheduledRefreshes();
      this.stopHeartbeat();
    });
    this.startHeartbeat();
    this.loadData();
    this.bindRealtime();
  }

  refresh(): void {
    this.loadData();
  }

  updateFormField<K extends keyof CreateCameraDeviceRequest>(key: K, value: CreateCameraDeviceRequest[K]): void {
    this.cameraForm.update((current) => ({ ...current, [key]: value }));
  }

  submitCamera(): void {
    const payload = this.normalizeForm(this.cameraForm());
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isSubmitting.set(true);

    const request$ = this.activeCameraId() == null
      ? this.api.createCameraDevice(payload)
      : this.api.updateCameraDevice(this.activeCameraId()!, payload);

    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (device) => {
          this.replaceCamera(device);
          this.successMessage.set(
            this.activeCameraId() == null
              ? `Camera ${device.ip ?? payload.ipAddress} added successfully.`
              : `Camera ${device.ip ?? payload.ipAddress} updated successfully.`
          );
          this.resetCameraForm();
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              error,
              this.activeCameraId() == null ? 'Unable to add camera.' : 'Unable to update camera.'
            )
          );
        }
      });
  }

  startEdit(device: CameraDevice): void {
    this.activeCameraId.set(device.id ?? null);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.cameraForm.set({
      ipAddress: device.ip ?? '',
      name: device.name ?? '',
      site: device.site ?? '',
      type: device.type ?? 'IP_CAMERA',
      port: device.port ?? 554,
      enabled: device.enabled ?? true
    });
  }

  cancelEdit(): void {
    this.resetCameraForm();
  }

  toggleEnabled(device: CameraDevice): void {
    if (device.id == null) {
      this.errorMessage.set('This camera cannot be toggled because its identifier is missing.');
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.api.updateCameraDeviceEnabled(device.id, !(device.enabled ?? true))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.replaceCamera(updated);
          this.successMessage.set(
            `${updated.ip ?? 'Camera'} ${updated.enabled ? 'enabled' : 'disabled'} successfully.`
          );
          if (this.activeCameraId() === updated.id) {
            this.startEdit(updated);
          }
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to change camera state.')
          );
        }
      });
  }

  deleteCamera(device: CameraDevice): void {
    if (device.id == null) {
      this.errorMessage.set('This camera cannot be deleted because its identifier is missing.');
      return;
    }
    const confirmed = window.confirm(`Delete camera ${device.ip ?? 'UNKNOWN'} ?`);
    if (!confirmed) {
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.api.deleteCameraDevice(device.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.cameraStore.update((store) => {
            const next = new Map(store.entities);
            next.delete((device.ip ?? '').trim());
            return {
              entities: next,
              realtimeState: this.resolveGlobalRealtimeState(next),
              lastUpdate: Date.now()
            };
          });
          this.successMessage.set(`Camera ${device.ip ?? 'UNKNOWN'} deleted successfully.`);
          if (this.activeCameraId() === device.id) {
            this.resetCameraForm();
          }
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to delete camera.')
          );
        }
      });
  }

  triggerCollection(target: CollectionTarget): void {
    if (target !== 'camera') {
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isLoading.set(true);

    this.api.triggerCollection(target).subscribe({
      next: () => {
        this.scheduleSnapshotRefresh();
      },
      error: (error) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Unable to trigger camera network scan.')
        );
      }
    });
  }

  trackByDevice(_: number, device: CameraDevice): string {
    return `${device.id ?? 'no-id'}:${device.ip ?? 'unknown'}:${device.port ?? 'unknown'}`;
  }

  statusLabel(vm: CameraRealtimeViewModel): string {
    if ((vm.camera.status ?? '').toUpperCase() === 'DEGRADED') {
      return 'DEGRADED';
    }
    if (vm.camera.reachable) {
      return 'REACHABLE';
    }
    return vm.camera.status ?? 'UNKNOWN';
  }

  realtimeLabel(vm: CameraRealtimeViewModel): string {
    switch (vm.realtimeState) {
      case 'fresh':
        return 'Fresh';
      case 'unchanged':
        return 'Unchanged';
      case 'stale':
        return 'Stale';
      case 'offline':
        return 'Offline';
      case 'disconnected':
        return 'Disconnected';
      default:
        return 'Unknown';
    }
  }

  realtimeAgeLabel(vm: CameraRealtimeViewModel): string {
    const ts = vm.lastMetricUpdate ?? null;
    if (!ts) {
      if (vm.camera.lastScanAt && this.parseTimestamp(vm.camera.lastScanAt) == null) {
        return 'Invalid timestamp';
      }
      return 'No update yet';
    }
    const diff = Math.max(0, this.nowMs() - ts);
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) {
      return `Updated ${seconds}s ago`;
    }
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) {
      return `Updated ${minutes} min ago`;
    }
    const date = new Date(ts);
    const hh = `${date.getHours()}`.padStart(2, '0');
    const mm = `${date.getMinutes()}`.padStart(2, '0');
    return `No update since ${hh}:${mm}`;
  }

  persistenceLabel(device: CameraDevice): string {
    return device.persisted ? 'Saved in DB' : 'Not saved';
  }

  enabledLabel(device: CameraDevice): string {
    return device.enabled === false ? 'Disabled' : 'Enabled';
  }

  snmpLabel(device: CameraDevice): string {
    if (!device.snmpEnabled) {
      return 'SNMP disabled';
    }
    return device.snmpStatus ?? 'SNMP enabled';
  }

  protocolLabel(device: CameraDevice): string {
    return device.protocol ?? 'UNKNOWN';
  }

  snmpLastSeenLabel(device: CameraDevice): string {
    if (!device.snmpLastSeenAt) {
      return 'No SNMP poll yet';
    }
    const parsed = Date.parse(device.snmpLastSeenAt);
    if (Number.isNaN(parsed)) {
      return 'Invalid SNMP timestamp';
    }
    return new Date(parsed).toLocaleString();
  }

  snmpMetricSummary(device: CameraDevice): string {
    if (!device.snmpEnabled) {
      return 'Optional SNMP enrichment not enabled';
    }
    const parts: string[] = [];
    if (device.snmpCpuPercent != null) {
      parts.push(`CPU ${device.snmpCpuPercent.toFixed(1)}%`);
    }
    if (device.snmpMemoryPercent != null) {
      parts.push(`Memory ${device.snmpMemoryPercent.toFixed(1)}%`);
    }
    if (device.snmpUptimeSeconds != null) {
      parts.push(`Uptime ${this.formatDuration(device.snmpUptimeSeconds)}`);
    }
    if (device.snmpInterfaceCount != null) {
      parts.push(`${device.snmpInterfaceCount} interface(s)`);
    }
    return parts.length > 0 ? parts.join(' • ') : 'SNMP enabled, waiting for metrics';
  }

  scanTimestampLabel(device: CameraDevice): string {
    if (!device.lastScanAt) {
      return 'Never scanned';
    }
    const parsed = Date.parse(device.lastScanAt);
    if (Number.isNaN(parsed)) {
      return 'Invalid timestamp';
    }
    return new Date(parsed).toLocaleString();
  }

  private formatDuration(totalSeconds: number): string {
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }

  private loadData(): void {
    const currentLoadGeneration = ++this.loadGeneration;
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      cameras: this.api.getCameraDevices().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load persisted camera inventory.')
          );
          return of<CameraDevice[]>([]);
        })
      ),
      sourceHealth: this.api.getSourceHealth().pipe(
        catchError((error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to load camera source health.')
          );
          return of<SourceAvailability[]>([]);
        })
      )
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ cameras, sourceHealth }) => {
        if (currentLoadGeneration !== this.loadGeneration) {
          return;
        }

        this.sourceAvailability.set(findSourceAvailability(sourceHealth, 'CAMERA'));
        const entities = this.toDeviceMap(cameras);
        this.cameraStore.set({
          entities,
          realtimeState: this.resolveGlobalRealtimeState(entities),
          lastUpdate: Date.now()
        });
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

    this.realtime.monitoringSourceHealth$('CAMERA').pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        this.sourceAvailability.set(incoming);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Camera source health stream is unavailable.')
        );
      }
    });

    this.cameraRealtime.cameraUpdates$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updates) => {
        console.debug('WS EVENT RECEIVED', 'camera', updates.length);
        this.mergeRealtimeUpdates(updates);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Camera realtime stream is unavailable.')
        );
      }
    });
  }

  private scheduleSnapshotRefresh(): void {
    this.clearScheduledRefreshes();

    for (const delay of [1500, 5000]) {
      const timeoutId = window.setTimeout(() => this.loadData(), delay);
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

  private toDeviceMap(cameras: CameraDevice[]): Map<string, CameraRealtimeViewModel> {
    const map = new Map<string, CameraRealtimeViewModel>();
    for (const device of cameras) {
      const key = this.deviceKey(device);
      if (!key) {
        continue;
      }
      const ts = this.parseTimestamp(device.lastScanAt);
      map.set(key, {
        camera: device,
        lastMetricUpdate: ts ?? undefined,
        realtimeState: this.resolveRealtimeState(ts)
      });
    }
    return map;
  }

  private replaceCamera(device: CameraDevice): void {
    const key = this.deviceKey(device);
    if (!key) {
      this.loadData();
      return;
    }

    this.cameraStore.update((store) => {
      const next = new Map(store.entities);
      const existing = next.get(key);
      const lastMetricUpdate = this.parseTimestamp(device.lastScanAt) ?? existing?.lastMetricUpdate ?? null;
      next.set(key, {
        camera: {
          ...existing?.camera,
          ...device
        },
        lastMetricUpdate: lastMetricUpdate ?? undefined,
        realtimeState: this.resolveRealtimeState(lastMetricUpdate)
      });
      return {
        entities: next,
        realtimeState: this.resolveGlobalRealtimeState(next),
        lastUpdate: Date.now()
      };
    });
    this.lastRefresh.set(new Date());
  }

  private mergeRealtimeUpdates(updates: CameraRealtimeUpdate[]): void {
    if (!updates.length) {
      return;
    }

    this.cameraStore.update((store) => {
      let changed = false;
      const next = new Map(store.entities);
      const now = Date.now();

      for (const update of updates) {
        const ip = update.ipAddress ?? update.ip ?? null;
        if (!ip) {
          continue;
        }
        const existing = next.get(ip);
        if (!existing) {
          continue;
        }

        const existingTs = existing.lastMetricUpdate ?? null;
        const incomingLastScanAt = update.lastSeen ?? update.lastScanAt ?? null;
        const incomingTs = this.parseTimestamp(incomingLastScanAt);
        if (incomingTs != null && existingTs != null && incomingTs < existingTs) {
          console.debug('CAMERA DELTA IGNORED', ip, 'older_timestamp');
          continue;
        }

        const nextStatus = update.status ?? existing.camera.status;
        const nextLastScanAt = incomingLastScanAt ?? existing.camera.lastScanAt;
        const nextReachable = update.reachable ?? this.isReachableStatus(nextStatus);

        if (
          nextStatus === existing.camera.status &&
          nextLastScanAt === existing.camera.lastScanAt &&
          nextReachable === existing.camera.reachable
        ) {
          console.debug('KPI UNCHANGED', ip);
          continue;
        }

        next.set(ip, {
          camera: {
            ...existing.camera,
            status: nextStatus,
            lastScanAt: nextLastScanAt,
            reachable: nextReachable
          },
          lastMetricUpdate: incomingTs ?? existing.lastMetricUpdate,
          realtimeState: this.resolveRealtimeState(incomingTs ?? existing.lastMetricUpdate ?? null)
        });
        console.debug('KPI UPDATED', ip);
        changed = true;
      }

      if (!changed) {
        return store;
      }

      this.lastRefresh.set(new Date());
      return {
        entities: next,
        realtimeState: this.resolveGlobalRealtimeState(next),
        lastUpdate: now
      };
    });
  }

  private deviceKey(device: CameraDevice): string | null {
    return device.ip?.trim() ?? null;
  }

  private isReachableStatus(status: string | null): boolean {
    const normalized = (status ?? '').toUpperCase();
    return normalized === 'UP' || normalized === 'DEGRADED';
  }

  private normalizeForm(form: CreateCameraDeviceRequest): CreateCameraDeviceRequest {
    return {
      ipAddress: form.ipAddress.trim(),
      name: form.name?.trim() || null,
      site: form.site?.trim() || null,
      type: form.type?.trim() || 'IP_CAMERA',
      port: form.port ?? null,
      enabled: form.enabled ?? true
    };
  }

  private resetCameraForm(): void {
    this.activeCameraId.set(null);
    this.cameraForm.set({
      ipAddress: '',
      name: '',
      site: '',
      type: 'IP_CAMERA',
      port: 554,
      enabled: true
    });
  }

  private parseTimestamp(value: string | null | undefined): number | null {
    if (!value) {
      return null;
    }
    const parsed = Date.parse(value);
    if (Number.isNaN(parsed)) {
      console.warn('CAMERA WS PAYLOAD INVALID', 'invalid_timestamp', value);
      return null;
    }
    return parsed;
  }

  private resolveRealtimeState(lastUpdateMs: number | null): RealtimeDataState {
    const availabilityStatus = this.mapAvailability(this.sourceAvailability());
    if (availabilityStatus === 'UNAVAILABLE') {
      return 'offline';
    }
    const wsStatus = this.realtimeConnection.status();
    if (wsStatus === 'DISCONNECTED' || wsStatus === 'ERROR') {
      return 'disconnected';
    }
    if (lastUpdateMs == null) {
      return 'unchanged';
    }

    const ageMs = Math.max(0, this.nowMs() - lastUpdateMs);
    if (ageMs <= this.config.freshDataWindowMs) {
      return 'fresh';
    }
    if (ageMs > this.config.hostsStaleThresholdMs) {
      return 'stale';
    }
    return 'unchanged';
  }

  private resolveGlobalRealtimeState(entities: Map<string, CameraRealtimeViewModel>): RealtimeDataState {
    const rows = [...entities.values()];
    if (!rows.length) {
      return this.resolveRealtimeState(null);
    }
    if (rows.some((row) => row.realtimeState === 'offline')) {
      return 'offline';
    }
    if (rows.some((row) => row.realtimeState === 'disconnected')) {
      return 'disconnected';
    }
    if (rows.some((row) => row.realtimeState === 'stale')) {
      return 'stale';
    }
    if (rows.some((row) => row.realtimeState === 'fresh')) {
      return 'fresh';
    }
    return 'unchanged';
  }

  private mapAvailability(entry: SourceAvailability | null): 'AVAILABLE' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN' {
    if (!entry) {
      return 'UNKNOWN';
    }
    if (
      entry.status === 'AVAILABLE' ||
      entry.status === 'DEGRADED' ||
      entry.status === 'UNAVAILABLE'
    ) {
      return entry.status;
    }
    return entry.available ? 'AVAILABLE' : 'UNAVAILABLE';
  }

  private startHeartbeat(): void {
    this.heartbeatIntervalId = window.setInterval(() => {
      this.nowMs.set(Date.now());

      const wsStatus = this.realtimeConnection.status();
      if (wsStatus === 'CONNECTED') {
        console.debug('WS HEARTBEAT OK');
        const lastWs = this.cameraStore().lastUpdate ?? null;
        if (lastWs != null) {
          const gap = this.nowMs() - lastWs;
          if (gap > this.config.wsNoDeltaThresholdMs) {
            const bucket = Math.floor(gap / this.config.wsNoDeltaThresholdMs);
            if (bucket !== this.lastNoDeltaLogBucket) {
              console.debug('WS NO DELTA RECEIVED', `gapMs=${gap}`);
              this.lastNoDeltaLogBucket = bucket;
            }
          } else {
            this.lastNoDeltaLogBucket = -1;
          }
        }
      }

      this.cameraStore.update((store) => {
        let changed = false;
        const next = new Map<string, CameraRealtimeViewModel>();
        for (const [key, vm] of store.entities.entries()) {
          const nextState = this.resolveRealtimeState(vm.lastMetricUpdate ?? null);
          if (nextState !== vm.realtimeState && nextState === 'stale') {
            console.debug('KPI MARKED STALE', key);
          }
          if (nextState !== vm.realtimeState) {
            changed = true;
            next.set(key, { ...vm, realtimeState: nextState });
          } else {
            next.set(key, vm);
          }
        }
        if (!changed) {
          return store;
        }
        return {
          entities: next,
          realtimeState: this.resolveGlobalRealtimeState(next),
          lastUpdate: store.lastUpdate
        };
      });
    }, 5000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatIntervalId == null) {
      return;
    }
    window.clearInterval(this.heartbeatIntervalId);
    this.heartbeatIntervalId = null;
  }
}
