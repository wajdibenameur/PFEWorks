import { Injectable, computed, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { extractApiErrorMessage } from '../http/http-error.utils';

export interface PlatformAlert {
  kind: 'error';
  title: string;
  message: string;
  status: number | null;
  path: string | null;
}

@Injectable({ providedIn: 'root' })
export class PlatformAlertService {
  private readonly currentAlert = signal<PlatformAlert | null>(null);
  readonly alert = computed(() => this.currentAlert());

  showError(message: string, status?: number | null, path?: string | null): void {
    const next: PlatformAlert = {
      kind: 'error',
      title: 'Request Failed',
      message,
      status: status ?? null,
      path: path ?? null
    };

    const prev = this.currentAlert();
    if (
      prev &&
      prev.kind === next.kind &&
      prev.message === next.message &&
      prev.status === next.status &&
      prev.path === next.path
    ) {
      return;
    }

    this.currentAlert.set(next);
  }

  showHttpError(error: HttpErrorResponse, fallback: string): void {
    const message = extractApiErrorMessage(error, fallback);
    const title =
      error.status === 403
        ? 'Acces refuse'
        : error.status === 401
          ? 'Authentification requise'
          : error.status >= 500
            ? 'Erreur serveur'
            : 'Requete en echec';

    this.currentAlert.set({
      kind: 'error',
      title,
      message,
      status: error.status || null,
      path: error.url ?? null
    });
  }

  dismiss(): void {
    this.currentAlert.set(null);
  }
}
