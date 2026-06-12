import { Injectable } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { CameraDevice } from '../../../core/models/camera-device.model';
import { StompClientService } from '../../../core/realtime/stomp-client.service';

export interface CameraRealtimeUpdate {
  ipAddress?: string | null;
  ip?: string | null;
  status?: string | null;
  lastSeen?: string | null;
  lastScanAt?: string | null;
  reachable?: boolean | null;
}

const CAMERA_STATUSES = new Set(['UP', 'DOWN', 'DEGRADED', 'UNKNOWN', 'DISABLED']);

@Injectable({ providedIn: 'root' })
export class CameraRealtimeService {
  constructor(private readonly stomp: StompClientService) {}

  cameraUpdates$(): Observable<CameraRealtimeUpdate[]> {
    return this.stomp.subscribe<unknown>('/topic/camera').pipe(
      map((payload) => this.normalizePayload(payload)),
      tap((updates) => {
        if (updates.length === 0) {
          console.warn('CAMERA WS PAYLOAD INVALID');
        }
      }),
      catchError((error) => {
        console.warn('CAMERA WS STREAM RECOVERED', error);
        return of([]);
      })
    );
  }

  private normalizePayload(payload: unknown): CameraRealtimeUpdate[] {
    if (Array.isArray(payload)) {
      return payload.map((item) => this.toUpdate(item)).filter((item) => !!item.ipAddress || !!item.ip);
    }
    return [this.toUpdate(payload)].filter((item) => !!item.ipAddress || !!item.ip);
  }

  private toUpdate(value: unknown): CameraRealtimeUpdate {
    if (!value || typeof value !== 'object') {
      return {};
    }
    const raw = value as Record<string, unknown>;
    return {
      ipAddress: this.asString(raw['ipAddress']),
      ip: this.asString(raw['ip']),
      status: this.asStatus(raw['status']),
      lastSeen: this.asString(raw['lastSeen']),
      lastScanAt: this.asString(raw['lastScanAt']),
      reachable: this.asBoolean(raw['reachable'])
    };
  }

  private asString(value: unknown): string | null {
    return typeof value === 'string' && value.trim().length > 0 ? value : null;
  }

  private asBoolean(value: unknown): boolean | null {
    return typeof value === 'boolean' ? value : null;
  }

  private asStatus(value: unknown): string | null {
    const status = this.asString(value);
    if (!status) {
      return null;
    }
    const normalized = status.toUpperCase();
    return CAMERA_STATUSES.has(normalized) ? normalized : null;
  }
}
