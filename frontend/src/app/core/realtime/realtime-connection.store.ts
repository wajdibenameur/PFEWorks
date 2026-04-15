import { Injectable, signal } from '@angular/core';

export type RealtimeStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR';

@Injectable({ providedIn: 'root' })
export class RealtimeConnectionStore {
  readonly status = signal<RealtimeStatus>('DISCONNECTED');
  readonly lastError = signal<string | null>(null);

  setConnecting(): void {
    this.status.set('CONNECTING');
    this.lastError.set(null);
  }

  setConnected(): void {
    this.status.set('CONNECTED');
    this.lastError.set(null);
  }

  setDisconnected(): void {
    this.status.set('DISCONNECTED');
  }

  setError(message: string): void {
    this.status.set('ERROR');
    this.lastError.set(message);
  }
}
