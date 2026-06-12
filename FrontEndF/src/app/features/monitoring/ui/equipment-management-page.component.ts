import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { extractApiErrorMessage } from '../../../core/http/http-error.utils';
import { CreateSnmpDeviceRequest, SnmpDevice } from '../../../core/models/snmp-device.model';
import { MonitoringApiService } from '../data/monitoring-api.service';

type SnmpCategory =
  | 'CAMERA'
  | 'SERVER'
  | 'ACCESS_CONTROL'
  | 'UPS'
  | 'PRINTER'
  | 'SWITCH'
  | 'ROUTER'
  | 'FIREWALL'
  | 'LOAD_BALANCER'
  | 'WIFI_ACCESS_POINT'
  | 'NETWORK_CONTROLLER';

type SnmpDeviceType =
  | 'ROUTER'
  | 'SWITCH'
  | 'SERVER'
  | 'PRINTER'
  | 'UPS'
  | 'FIREWALL'
  | 'WIRELESS_CONTROLLER'
  | 'ACCESS_POINT'
  | 'CAMERA'
  | 'OTHER';

@Component({
  selector: 'app-equipment-management-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './equipment-management-page.component.html',
  styleUrl: './equipment-management-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EquipmentManagementPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AUTH_CONTEXT);

  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly activeDeviceId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly devices = signal<SnmpDevice[]>([]);
  readonly canManageHosts = computed(() =>
    this.auth.arePermissionsLoaded() && this.auth.hasPermission('MANAGE_HOSTS')
  );

  readonly categoryOptions: SnmpCategory[] = [
    'CAMERA',
    'SERVER',
    'ACCESS_CONTROL',
    'UPS',
    'PRINTER',
    'SWITCH',
    'ROUTER',
    'FIREWALL',
    'LOAD_BALANCER',
    'WIFI_ACCESS_POINT',
    'NETWORK_CONTROLLER'
  ];

  readonly typeOptions: SnmpDeviceType[] = [
    'ROUTER',
    'SWITCH',
    'SERVER',
    'PRINTER',
    'UPS',
    'FIREWALL',
    'WIRELESS_CONTROLLER',
    'ACCESS_POINT',
    'CAMERA',
    'OTHER'
  ];

  readonly metricOptions: string[] = [
    'SYS_NAME',
    'SYS_UPTIME',
    'CPU',
    'MEMORY',
    'INTERFACES',
    'TRAFFIC'
  ];

  readonly form = signal<CreateSnmpDeviceRequest>({
    ipAddress: '',
    hostname: '',
    type: 'OTHER',
    category: 'SERVER',
    deviceGroup: '',
    snmpPort: 161,
    snmpCommunity: 'public',
    snmpVersion: '2c',
    pollingIntervalSeconds: 60,
    metricsToPoll: ['SYS_NAME', 'SYS_UPTIME', 'CPU', 'MEMORY', 'INTERFACES'],
    enabled: true
  });

  constructor(private readonly api: MonitoringApiService) {
    this.loadDevices();
  }

  loadDevices(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.api.getSnmpDevices()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe({
        next: (devices) => this.devices.set(devices),
        error: (error) => this.errorMessage.set(
          extractApiErrorMessage(error, 'Unable to load SNMP equipment list.')
        )
      });
  }

  submit(): void {
    const payload = this.normalizeForm(this.form());
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.isSubmitting.set(true);
    const request$ = this.activeDeviceId() == null
      ? this.api.createSnmpDevice(payload)
      : this.api.updateSnmpDevice(this.activeDeviceId()!, payload);

    request$
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (device) => {
          const existing = this.devices().filter((entry) => entry.id !== device.id);
          this.devices.set([...existing, device].sort((left, right) => left.ipAddress.localeCompare(right.ipAddress)));
          this.successMessage.set(
            this.activeDeviceId() == null
              ? `SNMP equipment ${device.ipAddress} added successfully.`
              : `SNMP equipment ${device.ipAddress} updated successfully.`
          );
          this.resetForm(payload.category);
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(
              error,
              this.activeDeviceId() == null ? 'Unable to add SNMP equipment.' : 'Unable to update SNMP equipment.'
            )
          );
        }
      });
  }

  updateField<K extends keyof CreateSnmpDeviceRequest>(key: K, value: CreateSnmpDeviceRequest[K]): void {
    this.form.update((current) => ({ ...current, [key]: value }));
  }

  toggleMetric(metric: string, enabled: boolean): void {
    const current = new Set(this.form().metricsToPoll ?? []);
    if (enabled) {
      current.add(metric);
    } else {
      current.delete(metric);
    }
    this.updateField('metricsToPoll', [...current]);
  }

  categoryLabel(category: string | null | undefined): string {
    return (category ?? 'UNKNOWN').replace(/_/g, ' ');
  }

  typeLabel(type: string | null | undefined): string {
    return (type ?? 'OTHER').replace(/_/g, ' ');
  }

  statusLabel(status: string | null | undefined): string {
    return status ?? 'UNKNOWN';
  }

  metricsLabel(device: SnmpDevice): string {
    const metrics = device.metricsToPoll ?? [];
    return metrics.length ? metrics.join(', ') : 'Default';
  }

  startEdit(device: SnmpDevice): void {
    this.activeDeviceId.set(device.id);
    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.form.set({
      ipAddress: device.ipAddress,
      hostname: device.hostname,
      type: device.type ?? 'OTHER',
      category: device.category ?? 'SERVER',
      deviceGroup: device.deviceGroup ?? '',
      snmpPort: device.snmpPort,
      snmpCommunity: device.snmpCommunity ?? 'public',
      snmpVersion: device.snmpVersion ?? '2c',
      pollingIntervalSeconds: device.pollingIntervalSeconds ?? 60,
      metricsToPoll: [...(device.metricsToPoll ?? [])],
      enabled: device.enabled
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  toggleEnabled(device: SnmpDevice): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.api.updateSnmpDeviceEnabled(device.id, !device.enabled)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.replaceDevice(updated);
          this.successMessage.set(
            `${updated.ipAddress} ${updated.enabled ? 'enabled' : 'disabled'} successfully.`
          );
          if (this.activeDeviceId() === updated.id) {
            this.startEdit(updated);
          }
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to change SNMP equipment state.')
          );
        }
      });
  }

  deleteDevice(device: SnmpDevice): void {
    const confirmed = window.confirm(
      `Delete SNMP equipment ${device.ipAddress}${device.manualEntry ? '' : ' ? It may reappear after restart if still present in bootstrap config.'}`
    );
    if (!confirmed) {
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.api.deleteSnmpDevice(device.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.devices.set(this.devices().filter((entry) => entry.id !== device.id));
          this.successMessage.set(`SNMP equipment ${device.ipAddress} deleted successfully.`);
          if (this.activeDeviceId() === device.id) {
            this.resetForm();
          }
        },
        error: (error) => {
          this.errorMessage.set(
            extractApiErrorMessage(error, 'Unable to delete SNMP equipment.')
          );
        }
      });
  }

  trackByDevice(_: number, device: SnmpDevice): number {
    return device.id;
  }

  private normalizeForm(form: CreateSnmpDeviceRequest): CreateSnmpDeviceRequest {
    return {
      ipAddress: form.ipAddress.trim(),
      hostname: form.hostname?.trim() || null,
      type: form.type?.trim() || 'OTHER',
      category: form.category.trim(),
      deviceGroup: form.deviceGroup?.trim() || null,
      snmpPort: form.snmpPort ?? 161,
      snmpCommunity: form.snmpCommunity?.trim() || null,
      snmpVersion: form.snmpVersion?.trim() || '2c',
      pollingIntervalSeconds: form.pollingIntervalSeconds ?? 60,
      metricsToPoll: (form.metricsToPoll ?? []).map((metric) => metric.trim()).filter(Boolean),
      enabled: form.enabled ?? true
    };
  }

  private resetForm(category: string = 'SERVER'): void {
    this.activeDeviceId.set(null);
    this.form.set({
      ipAddress: '',
      hostname: '',
      type: 'OTHER',
      category,
      deviceGroup: '',
      snmpPort: 161,
      snmpCommunity: 'public',
      snmpVersion: '2c',
      pollingIntervalSeconds: 60,
      metricsToPoll: ['SYS_NAME', 'SYS_UPTIME', 'CPU', 'MEMORY', 'INTERFACES'],
      enabled: true
    });
  }

  private replaceDevice(device: SnmpDevice): void {
    const remaining = this.devices().filter((entry) => entry.id !== device.id);
    this.devices.set([...remaining, device].sort((left, right) => left.ipAddress.localeCompare(right.ipAddress)));
  }
}
