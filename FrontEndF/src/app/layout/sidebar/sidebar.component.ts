import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SIDEBAR_NAV_ITEMS, NavItem } from './sidebar.model';
import { AUTH_CONTEXT } from '../../core/auth/auth-context.port';

@Component({
  selector: 'app-sidebar',
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SidebarComponent {
  private readonly auth = inject(AUTH_CONTEXT);
  readonly items = signal<NavItem[]>(SIDEBAR_NAV_ITEMS);
  readonly collapsed = input<boolean>(false);
  readonly drawerOpen = input<boolean>(false);
  readonly overlayMode = input<boolean>(false);
  readonly closeRequested = output<void>();
  readonly visibleItems = computed(() =>
    this.items()
      .filter((item) => this.canDisplay(item))
      .map((item) => ({
        ...item,
        children: item.children?.filter((child) => this.canDisplay(child))
      }))
  );

  protected initials(label: string): string {
    return label
      .split(' ')
      .map((part) => part.charAt(0))
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  protected startsSection(index: number, section?: string): boolean {
    if (!section) {
      return false;
    }

    if (index === 0) {
      return true;
    }

    return this.visibleItems()[index - 1]?.section !== section;
  }

  protected iconPath(icon?: string): string {
    const icons: Record<string, string> = {
      dashboard:
        'M3 13.2h8.2V3H3v10.2Zm9.8 7.8H21V10.8h-8.2V21ZM3 21h8.2v-6.2H3V21Zm9.8-9.8H21V3h-8.2v8.2Z',
      monitoring:
        'M4 18h2.8l2.7-7.1 3.2 8 3.1-5.9 1.8 3H20V18h-3.7l-1.5-2.5-2.6 4.9-3.3-8.2L7.9 18H4Z',
      equipment:
        'M12 2 4 5v6c0 5 3.4 9.7 8 11 4.6-1.3 8-6 8-11V5l-8-3Zm0 5.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5Zm0 11.3a6.8 6.8 0 0 1-4.7-1.9c.2-1.6 3.1-2.5 4.7-2.5 1.6 0 4.5.9 4.7 2.5a6.8 6.8 0 0 1-4.7 1.9Z',
      tickets:
        'M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 0 0-4V7Zm7-1h2v2h-2V6Zm0 4h2v2h-2v-2Zm0 4h2v2h-2v-2Z',
      users:
        'M16 11a4 4 0 1 0-3.999-4A4 4 0 0 0 16 11ZM8 10a3 3 0 1 0-3-3 3 3 0 0 0 3 3Zm8 2c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4ZM8 12c-2.3 0-7 1.2-7 3.5V18h5v-2c0-1.3.8-2.4 2.2-3.3A2.6 2.6 0 0 0 8 12Z',
      admin:
        'M12 2 2 7l10 5 8.2-4.1V15H22V7L12 2Zm-6 9.6V15c0 2.2 3.1 4 6 4s6-1.8 6-4v-3.4L12 15l-6-3.4Z'
    };

    return icons[icon ?? ''] ?? icons['dashboard'];
  }

  private canDisplay(item: NavItem): boolean {
    if (item.children?.length) {
      return item.children.some((child) => this.canDisplay(child));
    }

    if (item.requiredAnyPermissions?.length) {
      if (this.auth.arePermissionsLoaded()) {
        return item.requiredAnyPermissions.some((permission) => this.auth.hasPermission(permission));
      }

      if (item.fallbackRoles?.length) {
        return item.fallbackRoles.some((role) => this.auth.hasRole(role));
      }

      return false;
    }

    if (item.visibility !== 'ADMIN_AREA') {
      return true;
    }

    // UI hint only. Backend remains source of truth.
    return this.auth.hasRole('SUPERADMIN') || this.auth.hasRole('ADMIN');
  }
}


