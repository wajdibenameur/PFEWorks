import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

export interface AuthUser {
  id: string;
  username: string;
  roles: string[];
}

export interface AuthContextPort {
  readonly isAuthenticated$: Observable<boolean>;
  readonly user$: Observable<AuthUser | null>;
  getAccessToken(): string | null;
}

export const AUTH_CONTEXT = new InjectionToken<AuthContextPort>('AUTH_CONTEXT');
