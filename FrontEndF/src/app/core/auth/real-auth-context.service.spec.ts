import { HttpBackend, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { APP_CONFIG } from '../config/app-config.token';
import { RealAuthContextService } from './real-auth-context.service';

class MockHttpBackend implements HttpBackend {
  constructor(private readonly effectivePermissions: string[]) {}

  handle(req: HttpRequest<unknown>): Observable<HttpResponse<unknown>> {
    if (req.url.includes('/api/auth/me/permissions')) {
      return of(
        new HttpResponse({
          status: 200,
          body: {
            username: 'tester',
            roles: ['VIEWER'],
            effectivePermissions: this.effectivePermissions
          }
        })
      );
    }

    return of(new HttpResponse({ status: 404, body: {} }));
  }
}

describe('RealAuthContextService permissions', () => {
  const createToken = (expOffsetSeconds: number) => {
    const payload = {
      sub: 'user-1',
      preferred_username: 'user1',
      realm_access: { roles: ['VIEWER'] },
      exp: Math.floor(Date.now() / 1000) + expOffsetSeconds
    };
    const payloadBase64 = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
    return `x.${payloadBase64}.y`;
  };

  beforeEach(() => {
    localStorage.clear();
  });

  it('returns true for granted permission and false for missing one', () => {
    TestBed.configureTestingModule({
      providers: [
        RealAuthContextService,
        { provide: HttpBackend, useValue: new MockHttpBackend(['VIEW_ZABBIX']) },
        {
          provide: APP_CONFIG,
          useValue: { authApiUrl: 'http://localhost:8081', monitoringApiUrl: 'http://localhost:8080' }
        }
      ]
    });

    const service = TestBed.inject(RealAuthContextService);
    service.setTokens(createToken(3600), 'refresh');

    expect(service.arePermissionsLoaded()).toBeTrue();
    expect(service.hasPermission('VIEW_ZABBIX')).toBeTrue();
    expect(service.hasPermission('VIEW_DASHBOARD')).toBeFalse();
  });
});
