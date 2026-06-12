import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AUTH_CONTEXT, AuthContextPort, AuthUser } from './auth-context.port';
import { RoleGuard } from './role.guard';

class MockAuthContext implements AuthContextPort {
  readonly isAuthenticated$ = of(true);
  readonly user$ = of<AuthUser | null>(null);
  permissionsLoaded = true;
  permissions = new Set<string>();

  getAccessToken(): string | null {
    return 'token';
  }
  getRefreshToken(): string | null {
    return 'refresh';
  }
  getRoles(): string[] {
    return [];
  }
  arePermissionsLoaded(): boolean {
    return this.permissionsLoaded;
  }
  hasRole(): boolean {
    return false;
  }
  hasPermission(permission: string): boolean {
    return this.permissions.has(permission.trim().toUpperCase());
  }
  setTokens(): void {}
  logout(): void {}
}

describe('RoleGuard', () => {
  let guard: RoleGuard;
  let router: jasmine.SpyObj<Router>;
  let auth: MockAuthContext;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    auth = new MockAuthContext();

    TestBed.configureTestingModule({
      providers: [
        RoleGuard,
        { provide: Router, useValue: router },
        { provide: AUTH_CONTEXT, useValue: auth }
      ]
    });

    guard = TestBed.inject(RoleGuard);
  });

  it('allows route when required permission is present', () => {
    auth.permissions.add('VIEW_ZABBIX');
    const route = { data: { permissionsAny: ['VIEW_ZABBIX'] } } as any;

    expect(guard.canActivate(route)).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects when permission is missing', () => {
    const route = { data: { permissionsAny: ['VIEW_ZABBIX'] } } as any;

    expect(guard.canActivate(route)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('redirects when permissions are not loaded and does not fallback to role', () => {
    auth.permissionsLoaded = false;
    const route = { data: { permissionsAny: ['VIEW_DASHBOARD'] } } as any;

    expect(guard.canActivate(route)).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
