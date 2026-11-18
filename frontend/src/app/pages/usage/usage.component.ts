import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/api-error';
import { DailyUsage, ModelUsage, UsageSummary } from '../../core/models';
import { UsageService } from '../../core/workspace.service';
import { humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-usage',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './usage.component.html',
  styleUrl: './usage.component.css'
})
export class UsageComponent {
  private readonly usageService = inject(UsageService);

  readonly ranges = [7, 30, 90];
  readonly days = signal(7);
  readonly summary = signal<UsageSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly refreshing = signal(false);

  readonly hasData = computed(() => (this.summary()?.eventCount ?? 0) > 0);
  readonly denseChart = computed(() => this.summary()?.daily.length ? this.summary()!.daily.length > 14 : false);

  readonly maxDailyTokens = computed(() => {
    const daily = this.summary()?.daily ?? [];
    return Math.max(1, ...daily.map(day => day.tokens));
  });

  readonly maxModelTokens = computed(() => {
    const models = this.summary()?.models ?? [];
    return Math.max(1, ...models.map(model => model.tokens));
  });

  readonly promptShare = computed(() => {
    const summary = this.summary();
    if (!summary || summary.totalTokens <= 0) {
      return 0;
    }
    return Math.round((summary.promptTokens / summary.totalTokens) * 100);
  });

  readonly completionShare = computed(() => 100 - this.promptShare());

  readonly averageCost = computed(() => {
    const summary = this.summary();
    if (!summary || summary.eventCount <= 0) {
      return 0;
    }
    return summary.totalCost / summary.eventCount;
  });

  readonly currency = computed(() => this.summary()?.currency || 'USD');

  constructor() {
    this.reload();
  }

  setRange(days: number): void {
    if (this.days() === days) {
      return;
    }
    this.days.set(days);
    this.reload();
  }

  reload(): void {
    if (this.summary()) {
      this.refreshing.set(true);
    } else {
      this.loading.set(true);
    }
    this.error.set('');
    this.usageService.summary(this.days()).subscribe({
      next: summary => {
        this.summary.set(summary);
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.refreshing.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the AI usage summary.'));
      }
    });
  }

  barHeight(tokens: number): number {
    return Math.max(2, Math.round((tokens / this.maxDailyTokens()) * 100));
  }

  modelWidth(tokens: number): number {
    return Math.max(2, Math.round((tokens / this.maxModelTokens()) * 100));
  }

  showDailyLabel(index: number, daily: DailyUsage[]): boolean {
    if (daily.length <= 7) {
      return true;
    }
    const step = daily.length <= 30 ? 5 : 15;
    return index % step === 0 || index === daily.length - 1;
  }

  number(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-US').format(value ?? 0);
  }

  cost(value: number | null | undefined, currency = this.currency()): string {
    const amount = value ?? 0;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency || 'USD',
        minimumFractionDigits: 2,
        maximumFractionDigits: 6
      }).format(amount);
    } catch {
      return `${amount.toFixed(6)} ${currency || 'USD'}`;
    }
  }

  compactCost(value: number | null | undefined, currency = this.currency()): string {
    const amount = value ?? 0;
    if (amount > 0 && amount < 0.01) {
      return this.cost(amount, currency);
    }
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: currency || 'USD',
        maximumFractionDigits: 2
      }).format(amount);
    } catch {
      return `${amount.toFixed(2)} ${currency || 'USD'}`;
    }
  }

  modelTitle(model: ModelUsage): string {
    return `${model.provider} · ${model.model}`;
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  shortDate(value: string): string {
    const date = new Date(`${value}T00:00:00Z`);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' });
  }
}
