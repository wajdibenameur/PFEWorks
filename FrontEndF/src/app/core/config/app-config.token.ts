import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface AppConfig {
  monitoringApiUrl: string;
  authApiUrl: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export const appConfigValue: AppConfig = {
  monitoringApiUrl: environment.monitoringApiUrl,
  authApiUrl: environment.authApiUrl
};

