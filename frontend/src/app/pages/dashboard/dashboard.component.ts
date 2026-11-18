import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { DashboardStats } from '../../core/models';
import { formatBytes, humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  private readonly api = inject(ApiService);

  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');

  readonly maxUploads = computed(() => {
    const days = this.stats()?.uploadsLast7Days ?? [];
    return Math.max(1, ...days.map(day => day.uploaded));
  });

  readonly maxCategory = computed(() => {
    const categories = this.stats()?.categoryBreakdown ?? [];
    return Math.max(1, ...categories.map(category => category.count));
  });

  readonly storageLabel = computed(() => formatBytes(this.stats()?.storageBytes ?? 0));

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.get<DashboardStats>('/dashboard/stats').subscribe({
      next: stats => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the dashboard.'));
      }
    });
  }

  barHeight(uploaded: number): number {
    return Math.max(2, Math.round((uploaded / this.maxUploads()) * 100));
  }

  categoryWidth(count: number): string {
    return `${Math.round((count / this.maxCategory()) * 100)}%`;
  }

  label(value: string): string {
    return humanize(value);
  }
}
