import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AUTH_CONTEXT, AuthContextPort, AuthUser } from '../../core/auth/auth-context.port';
import { SidebarComponent } from './sidebar.component';

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

describe('SidebarComponent RBAC filtering', () => {
  let fixture: any;
  let component: SidebarComponent;
  let auth: MockAuthContext;

  beforeEach(async () => {
    auth = new MockAuthContext();

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [{ provide: AUTH_CONTEXT, useValue: auth }]
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
  });

  it('shows only dashboard when only VIEW_DASHBOARD is granted', () => {
    auth.permissions = new Set(['VIEW_DASHBOARD']);
    fixture.detectChanges();

    const visible = component.visibleItems();
    const labels = visible.flatMap((item: any) => [item.label, ...(item.children ?? []).map((child: any) => child.label)]);

    expect(labels).toContain('Dashboard');
    expect(labels).not.toContain('Zabbix');
    expect(labels).not.toContain('SNMP');
    expect(labels).not.toContain('Camera');
    expect(labels).not.toContain('Access Point');
  });

  it('shows zabbix module only when all required zabbix permissions are granted', () => {
    auth.permissions = new Set(['VIEW_ZABBIX', 'VIEW_HOSTS', 'VIEW_ALERTS', 'VIEW_METRICS', 'VIEW_DASHBOARD']);
    fixture.detectChanges();

    const monitoring = component.visibleItems().find((item) => item.label === 'Monitoring');
    const childLabels = (monitoring?.children ?? []).map((item) => item.label);

    expect(childLabels).toEqual(['Zabbix']);
  });

  it('hides zabbix module when one required permission is missing', () => {
    auth.permissions = new Set(['VIEW_ZABBIX', 'VIEW_HOSTS', 'VIEW_ALERTS', 'VIEW_DASHBOARD']);
    fixture.detectChanges();

    const monitoring = component.visibleItems().find((item) => item.label === 'Monitoring');
    const childLabels = (monitoring?.children ?? []).map((item) => item.label) ?? [];

    expect(childLabels).not.toContain('Zabbix');
  });

  it('hides permission-based entries while permissions are not loaded', () => {
    auth.permissionsLoaded = false;
    auth.permissions = new Set(['VIEW_DASHBOARD', 'VIEW_ZABBIX', 'VIEW_USERS']);
    fixture.detectChanges();

    expect(component.visibleItems().length).toBe(0);
  });
});
