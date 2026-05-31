import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface AppConfig {
  monitoringApiUrl: string;
  authApiUrl: string;
  metricsStaleThresholdMs: number;
  hostsStaleThresholdMs: number;
  wsNoDeltaThresholdMs: number;
  freshDataWindowMs: number;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export const appConfigValue: AppConfig = {
  monitoringApiUrl: environment.monitoringApiUrl,
  authApiUrl: environment.authApiUrl,
  metricsStaleThresholdMs: environment.metricsStaleThresholdMs,
  hostsStaleThresholdMs: environment.hostsStaleThresholdMs,
  wsNoDeltaThresholdMs: environment.wsNoDeltaThresholdMs,
  freshDataWindowMs: environment.freshDataWindowMs
};

