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

@Component({
  selector: 'app-monitoring-camera-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-camera-page.component.html',
  styleUrl: './monitoring-camera-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringCameraPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private realtimeBound = false;

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
        window.setTimeout(() => this.loadData(), 1500);
        window.setTimeout(() => this.loadData(), 5000);
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
    }).subscribe({
      next: ({ cameras, sourceHealth }) => {
        this.cameraDevices.set(
          [...cameras].sort((left, right) =>
            `${left.ip ?? ''}:${left.port ?? ''}`.localeCompare(`${right.ip ?? ''}:${right.port ?? ''}`)
          )
        );
        this.sourceAvailability.set(
          sourceHealth.find((entry) => entry.source.toUpperCase() === 'CAMERA') ?? null
        );
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

    this.realtime.monitoringSources$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        if (incoming.source.toUpperCase() !== 'CAMERA') {
          return;
        }
        this.sourceAvailability.set(incoming);
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Camera source health stream is unavailable.')
        );
      }
    });
  }
}
