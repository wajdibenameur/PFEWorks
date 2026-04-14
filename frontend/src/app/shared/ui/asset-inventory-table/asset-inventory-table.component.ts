import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export interface GlobalAssetVm {
  id: string;
  hostname: string;
  address: string;
  ip: string;
  source: string;
  category: string;
  status: string;
  problemCount: number;
}

@Component({
  selector: 'app-asset-inventory-table',
  imports: [CommonModule],
  templateUrl: './asset-inventory-table.component.html',
  styleUrl: './asset-inventory-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssetInventoryTableComponent {
  readonly assets = input.required<GlobalAssetVm[]>();
  readonly filterText = signal('');
  readonly statusFilter = signal<'ALL' | 'UP' | 'DOWN' | 'UNKNOWN'>('ALL');
  readonly categoryFilter = signal<'ALL' | 'SERVER' | 'PRINTER' | 'CAMERA' | 'ACCESS_CONTROL' | 'UNKNOWN'>('ALL');

  readonly filteredAssets = computed(() => {
    const filterText = this.filterText().trim().toLowerCase();
    const statusFilter = this.statusFilter();
    const categoryFilter = this.categoryFilter();

    return this.assets().filter((asset) => {
      if (statusFilter !== 'ALL' && asset.status !== statusFilter) {
        return false;
      }
      if (categoryFilter !== 'ALL' && asset.category !== categoryFilter) {
        return false;
      }
      if (!filterText) {
        return true;
      }

      const fingerprint = `${asset.hostname} ${asset.address} ${asset.ip} ${asset.source}`.toLowerCase();
      return fingerprint.includes(filterText);
    });
  });

  updateFilterText(event: Event): void {
    const value = (event.target as HTMLInputElement).value ?? '';
    this.filterText.set(value);
  }

  updateStatusFilter(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'UP' | 'DOWN' | 'UNKNOWN');
  }

  updateCategoryFilter(event: Event): void {
    this.categoryFilter.set(
      (event.target as HTMLSelectElement).value as
        | 'ALL'
        | 'SERVER'
        | 'PRINTER'
        | 'CAMERA'
        | 'ACCESS_CONTROL'
        | 'UNKNOWN'
    );
  }
}
