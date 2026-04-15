import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuthContextPort, AuthUser } from './auth-context.port';

@Injectable()
export class NoopAuthContextService implements AuthContextPort {
  readonly isAuthenticated$: Observable<boolean> = of(false);
  readonly user$: Observable<AuthUser | null> = of(null);

  getAccessToken(): string | null {
    return null;
  }
}
