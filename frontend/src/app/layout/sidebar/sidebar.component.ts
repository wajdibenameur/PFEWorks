import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SIDEBAR_NAV_ITEMS, NavItem } from './sidebar.model';

@Component({
  selector: 'app-sidebar',
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SidebarComponent {
  readonly items = signal<NavItem[]>(SIDEBAR_NAV_ITEMS);
  readonly collapsed = input<boolean>(false);
  readonly drawerOpen = input<boolean>(false);
  readonly overlayMode = input<boolean>(false);
  readonly closeRequested = output<void>();

  displayLabel(label: string): string {
    if (!this.collapsed()) {
      return label;
    }
    return label
      .split(' ')
      .map((part) => part.charAt(0))
      .join('')
      .slice(0, 3)
      .toUpperCase();
  }
}
