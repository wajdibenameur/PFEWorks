import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { APP_CONFIG, appConfigValue } from './core/config/app-config.token';
import { AUTH_CONTEXT } from './core/auth/auth-context.port';
import { RealAuthContextService } from './core/auth/real-auth-context.service';
import { authHeaderInterceptor } from './core/http/auth-header.interceptor';
import { authErrorInterceptor } from './core/http/auth-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authHeaderInterceptor, authErrorInterceptor])),
    { provide: APP_CONFIG, useValue: appConfigValue },
    { provide: AUTH_CONTEXT, useClass: RealAuthContextService }
  ]
};

