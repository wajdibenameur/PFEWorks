import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CameraDevice } from '../../../core/models/camera-device.model';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';
import { findSourceAvailability } from '../data/monitoring-source.utils';

@Component({
  selector: 'app-monitoring-camera-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-camera-page.component.html',
  styleUrls: ['./monitoring-camera-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringCameraPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimeoutIds: number[] = [];
  private realtimeBound = false;
  private loadGeneration = 0;

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastRefresh = signal<Date | null>(null);
  readonly cameraDevices = signal<CameraDevice[]>([]);
  readonly sourceAvailability = signal<SourceAvailability | null>(null);

  readonly collectionActions: CollectionActionVm[] = [
    { label: 'Scanner Camera', target: 'camera' }
  ];

  readonly summary = computed(() => {
    const devices = this.cameraDevices();
    return {
      total: devices.length,
      reachable: devices.filter((device) => device.reachable).length,
      persisted: devices.filter((device) => device.persisted).length,
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
    private readonly realtime: MonitoringRealtimeService
  ) {
    this.destroyRef.onDestroy(() => this.clearScheduledRefreshes());
    this.loadData();
    this.bindRealtime();
  }

  refresh(): void {
    this.loadData();
  }

  triggerCollection(target: CollectionTarget): void {
    if (target !== 'camera') {
      return;
    }

    this.errorMessage.set(null);
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
    return `${device.ip ?? 'unknown'}:${device.port ?? 'unknown'}`;
  }

  statusLabel(device: CameraDevice): string {
    if (device.reachable) {
      return 'REACHABLE';
    }
    return device.status ?? 'UNKNOWN';
  }

  persistenceLabel(device: CameraDevice): string {
    return device.persisted ? 'Saved in DB' : 'Not saved';
  }

  protocolLabel(device: CameraDevice): string {
    return device.protocol ?? 'UNKNOWN';
  }

  scanTimestampLabel(device: CameraDevice): string {
    if (!device.lastScanAt) {
      return 'Never scanned';
    }
    return new Date(device.lastScanAt).toLocaleString();
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

        this.cameraDevices.set(
          [...cameras].sort((left, right) =>
            `${left.ip ?? ''}:${left.port ?? ''}`.localeCompare(`${right.ip ?? ''}:${right.port ?? ''}`)
          )
        );
        this.sourceAvailability.set(findSourceAvailability(sourceHealth, 'CAMERA'));
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
}


