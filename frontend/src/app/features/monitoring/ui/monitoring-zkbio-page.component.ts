import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CollectionTarget } from '../../../core/models/collection-target.model';
import { ServiceStatus } from '../../../core/models/service-status.model';
import { SourceAvailability } from '../../../core/models/source-availability.model';
import { ZkBioAttendance } from '../../../core/models/zkbio-attendance.model';
import { ZkBioProblem } from '../../../core/models/zkbio-problem.model';
import {
  CollectionActionVm,
  CollectionControlBarComponent
} from '../../../shared/ui/collection-control-bar/collection-control-bar.component';
import { MonitoringApiService } from '../data/monitoring-api.service';
import { MonitoringRealtimeService } from '../data/monitoring-realtime.service';

@Component({
  selector: 'app-monitoring-zkbio-page',
  imports: [CommonModule, CollectionControlBarComponent],
  templateUrl: './monitoring-zkbio-page.component.html',
  styleUrl: './monitoring-zkbio-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitoringZkBioPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = inject(MonitoringApiService);
  private readonly realtime = inject(MonitoringRealtimeService);
  private realtimeBound = false;

  protected readonly isLoading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly lastRefresh = signal<Date | null>(null);

  protected readonly serverStatus = signal<ServiceStatus | null>(null);
  protected readonly devices = signal<ServiceStatus[]>([]);
  protected readonly problems = signal<ZkBioProblem[]>([]);
  protected readonly attendanceLogs = signal<ZkBioAttendance[]>([]);

  protected readonly activeTab = signal<'problems' | 'attendance' | 'devices'>('problems');
  protected readonly problemSearch = signal('');
  protected readonly attendanceSearch = signal('');

  protected readonly collectionActions: CollectionActionVm[] = [
    { label: 'Collect ZKBio', target: 'zkbio' }
  ];

  ngOnInit(): void {
    this.loadData();
    this.bindRealtime();
  }

  protected loadData(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      status: this.api.getZkBioStatus().pipe(
        catchError((error) => {
          this.handleError('status', error);
          return of<ServiceStatus | null>(null);
        })
      ),
      devices: this.api.getZkBioDevices().pipe(
        catchError((error) => {
          this.handleError('devices', error);
          return of<ServiceStatus[]>([]);
        })
      ),
      problems: this.api.getZkBioProblems().pipe(
        catchError((error) => {
          this.handleError('problems', error);
          return of<ZkBioProblem[]>([]);
        })
      ),
      attendance: this.api.getZkBioAttendance().pipe(
        catchError((error) => {
          this.handleError('attendance', error);
          return of<ZkBioAttendance[]>([]);
        })
      ),
      sourceHealth: this.api.getSourceHealth().pipe(
        catchError((error) => {
          this.handleError('source health', error);
          return of<SourceAvailability[]>([]);
        })
      )
    }).subscribe({
      next: ({ status, devices, problems, attendance, sourceHealth }) => {
        const availability =
          sourceHealth.find((entry) => entry.source.toUpperCase() === 'ZKBIO') ?? null;

        this.serverStatus.set(this.applyAvailability(status, availability));
        this.devices.set(devices);
        this.problems.set(problems);
        this.attendanceLogs.set(attendance);
        this.lastRefresh.set(new Date());
        this.isLoading.set(false);
      }
    });
  }

  protected triggerCollection(target: CollectionTarget): void {
    if (target !== 'zkbio') {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.api.triggerCollection(target).subscribe({
      next: () => {
        window.setTimeout(() => this.loadData(), 1500);
        window.setTimeout(() => this.loadData(), 5000);
      },
      error: (error) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          extractApiErrorMessage(error, 'Unable to trigger ZKBio collection.')
        );
      }
    });
  }

  protected setActiveTab(tab: 'problems' | 'attendance' | 'devices'): void {
    this.activeTab.set(tab);
  }

  protected updateProblemSearch(event: Event): void {
    this.problemSearch.set((event.target as HTMLInputElement).value);
  }

  protected updateAttendanceSearch(event: Event): void {
    this.attendanceSearch.set((event.target as HTMLInputElement).value);
  }

  protected get filteredProblems(): ZkBioProblem[] {
    const search = this.problemSearch().toLowerCase();
    if (!search) return this.problems();
    return this.problems().filter((problem) =>
      problem.host?.toLowerCase().includes(search) ||
      problem.description?.toLowerCase().includes(search) ||
      problem.severity?.toLowerCase().includes(search)
    );
  }

  protected get filteredAttendance(): ZkBioAttendance[] {
    const search = this.attendanceSearch().toLowerCase();
    if (!search) return this.attendanceLogs();
    return this.attendanceLogs().filter((attendance) =>
      attendance.userName?.toLowerCase().includes(search) ||
      attendance.deviceName?.toLowerCase().includes(search) ||
      attendance.verifyType?.toLowerCase().includes(search)
    );
  }

  protected get activeProblemsCount(): number {
    return this.problems().filter((problem) => problem.active).length;
  }

  protected formatTimestamp(timestamp: number): string {
    if (!timestamp) return '-';
    return new Date(timestamp * 1000).toLocaleString();
  }

  protected getStatusClass(status: string | null | undefined): string {
    switch (status?.toLowerCase()) {
      case 'up':
      case 'normal':
        return 'status-up';
      case 'down':
      case 'error':
        return 'status-down';
      default:
        return 'status-unknown';
    }
  }

  private bindRealtime(): void {
    if (this.realtimeBound) {
      return;
    }
    this.realtimeBound = true;

    this.realtime.zkbioProblems$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (problems) => {
        this.problems.set(this.mergeProblems(this.problems(), problems));
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'ZKBio realtime problem stream is unavailable.')
        );
      }
    });

    this.realtime.zkbioAttendance$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (logs) => {
        this.attendanceLogs.set(logs);
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'ZKBio realtime attendance stream is unavailable.')
        );
      }
    });

    this.realtime.zkbioDevices$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (devices) => {
        this.devices.set(devices);
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'ZKBio realtime device stream is unavailable.')
        );
      }
    });

    this.realtime.zkbioStatus$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (status) => {
        this.serverStatus.set(status);
        this.lastRefresh.set(new Date());
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'ZKBio realtime status stream is unavailable.')
        );
      }
    });

    this.realtime.sourceAvailability$().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (incoming) => {
        if (incoming.source.toUpperCase() !== 'ZKBIO') {
          return;
        }

        this.serverStatus.update((current) => this.applyAvailability(current, incoming));
      },
      error: (error) => {
        this.errorMessage.set(
          extractApiErrorMessage(error, 'ZKBio source health stream is unavailable.')
        );
      }
    });
  }

  private applyAvailability(
    status: ServiceStatus | null,
    availability: SourceAvailability | null
  ): ServiceStatus | null {
    if (!status && !availability) {
      return null;
    }

    const fallbackStatus =
      availability?.status === 'AVAILABLE' || availability?.available === true
        ? 'UP'
        : availability?.status === 'DEGRADED'
          ? 'DEGRADED'
          : availability?.status === 'UNAVAILABLE' || availability?.available === false
            ? 'DOWN'
            : 'UNKNOWN';

    return {
      source: status?.source ?? 'ZKBIO',
      name: status?.name ?? 'ZKBio Server',
      ip: status?.ip ?? null,
      port: status?.port ?? null,
      protocol: status?.protocol ?? null,
      status: status?.status ?? fallbackStatus,
      category: status?.category ?? 'ACCESS',
      lastCheck: status?.lastCheck ?? null
    };
  }

  private mergeProblems(existing: ZkBioProblem[], incoming: ZkBioProblem[]): ZkBioProblem[] {
    const map = new Map<string, ZkBioProblem>();

    for (const problem of existing) {
      map.set(problem.problemId, problem);
    }

    for (const problem of incoming) {
      map.set(problem.problemId, problem);
    }

    return Array.from(map.values());
  }

  private handleError(context: string, error: unknown): void {
    this.errorMessage.set(
      extractApiErrorMessage(error, `Unable to load ZKBio ${context}.`)
    );
  }
}
