import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { PlatformAlertService } from '../../core/ui/platform-alert.service';
import { IdleLogoutService } from '../../core/auth/idle-logout.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, NavbarComponent, SidebarComponent],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShellComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly idleLogoutService = inject(IdleLogoutService);
  readonly platformAlert = inject(PlatformAlertService);
  readonly sidebarCollapsed = signal(false);
  readonly drawerOpen = signal(false);
  readonly overlayMode = signal(this.isOverlayMode());

  constructor() {
    this.idleLogoutService.start();

    const handleResize = () => {
      const isOverlay = this.isOverlayMode();
      this.overlayMode.set(isOverlay);
      if (isOverlay) {
        this.drawerOpen.set(false);
      }
    };

    window.addEventListener('resize', handleResize);
    this.destroyRef.onDestroy(() => window.removeEventListener('resize', handleResize));
  }

  toggleSidebar(): void {
    if (this.overlayMode()) {
      this.drawerOpen.update((value) => !value);
    }
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  private isOverlayMode(): boolean {
    return window.matchMedia('(max-width: 900px)').matches;
  }
}


