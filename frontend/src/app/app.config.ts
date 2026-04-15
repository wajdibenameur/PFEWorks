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
import { NoopAuthContextService } from './core/auth/noop-auth-context.service';
import { authHeaderInterceptor } from './core/http/auth-header.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authHeaderInterceptor])),
    { provide: APP_CONFIG, useValue: appConfigValue },
    { provide: AUTH_CONTEXT, useClass: NoopAuthContextService }
  ]
};
