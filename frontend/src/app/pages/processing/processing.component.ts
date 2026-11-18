import { CommonModule } from '@angular/common';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { PageResponse, ProcessingJobResponse, ProcessingJobStatus } from '../../core/models';
import { ProcessingService } from '../../core/processing.service';
import { humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-processing',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './processing.component.html',
  styleUrl: './processing.component.css'
})
export class ProcessingComponent implements OnDestroy {
  private readonly processingService = inject(ProcessingService);
  readonly auth = inject(AuthService);

  readonly page = signal<PageResponse<ProcessingJobResponse> | null>(null);
  readonly status = signal<ProcessingJobStatus | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly retrying = signal<string | null>(null);

  private pollTimer?: ReturnType<typeof setInterval>;

  constructor() {
    this.reload();
    this.pollTimer = setInterval(() => this.poll(), 4000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.processingService.list(this.status(), this.pageIndex(), this.pageSize()).subscribe({
      next: page => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load processing jobs.'));
      }
    });
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.status.set(value ? (value as ProcessingJobStatus) : null);
    this.pageIndex.set(0);
    this.reload();
  }

  onPageSizeChange(event: Event): void {
    this.pageSize.set(Number((event.target as HTMLSelectElement).value));
    this.pageIndex.set(0);
    this.reload();
  }

  goToPage(index: number): void {
    const current = this.page();
    if (!current || index < 0 || index >= current.totalPages) {
      return;
    }
    this.pageIndex.set(index);
    this.reload();
  }

  retry(job: ProcessingJobResponse): void {
    this.retrying.set(job.id);
    this.error.set('');
    this.processingService.retry(job.id).subscribe({
      next: updated => {
        this.retrying.set(null);
        this.notice.set(`Job for "${updated.fileName}" was queued again.`);
        this.reload();
      },
      error: error => {
        this.retrying.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to retry this job.'));
      }
    });
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  statusTone(status: ProcessingJobStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'tone-green';
      case 'FAILED':
        return 'tone-red';
      case 'PROCESSING':
        return 'tone-blue';
      default:
        return 'tone-amber';
    }
  }

  private poll(): void {
    const jobs = this.page()?.content ?? [];
    const active = jobs.some(job => job.status === 'QUEUED' || job.status === 'PROCESSING');
    if (!active || this.loading()) {
      return;
    }
    this.processingService.list(this.status(), this.pageIndex(), this.pageSize()).subscribe({
      next: page => this.page.set(page)
    });
  }
}
