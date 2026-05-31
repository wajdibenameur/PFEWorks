import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorResponse } from '../models/api-error.model';

export function extractApiError(error: unknown): ApiErrorResponse | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }

  if (error.error && typeof error.error === 'object') {
    return error.error as ApiErrorResponse;
  }

  return {
    status: error.status,
    message: error.message || 'Unexpected HTTP error',
    errorCode: null,
    source: null,
    path: null
  };
}

export function extractApiErrorMessage(
  error: unknown,
  fallback: string
): string {
  const apiError = extractApiError(error);
  if (!apiError) {
    return fallback;
  }

  const rawMessage = apiError.message ?? fallback;
  if (rawMessage.includes('Transition from') && rawMessage.includes('is not allowed')) {
    return 'Action impossible pour le statut actuel du ticket. Utilisez le bouton Valider/Rejeter ou choisissez un statut autorise.';
  }
  if (rawMessage.includes('TICKET_NOT_FOUND')) {
    return 'Le ticket est introuvable ou a ete supprime.';
  }

  const sourcePrefix = apiError.source ? `${apiError.source}: ` : '';
  return `${sourcePrefix}${rawMessage}`;
}

