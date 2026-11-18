import { CommonModule } from '@angular/common';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import {
  PageResponse,
  ValidationDetailResponse,
  ValidationResponse,
  ValidationStatus
} from '../../core/models';
import { ValidationService } from '../../core/validation.service';
import { humanize, initials as formatInitials } from '../../shared/format';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-validation',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './validation.component.html',
  styleUrl: './validation.component.css'
})
export class ValidationComponent implements OnDestroy {
  private readonly validationService = inject(ValidationService);
  readonly auth = inject(AuthService);

  readonly page = signal<PageResponse<ValidationResponse> | null>(null);
  readonly status = signal<ValidationStatus | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');

  readonly detail = signal<ValidationDetailResponse | null>(null);
  readonly detailLoading = signal(false);
  readonly commentDraft = signal('');
  readonly postingComment = signal(false);

  readonly decision = signal<'approve' | 'reject' | null>(null);
  readonly decisionComment = signal('');
  readonly deciding = signal(false);

  private pollTimer?: ReturnType<typeof setInterval>;

  constructor() {
    this.reload();
    this.pollTimer = setInterval(() => this.poll(), 10000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.validationService.list(this.status(), this.pageIndex(), this.pageSize()).subscribe({
      next: page => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load validation requests.'));
      }
    });
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.status.set(value ? (value as ValidationStatus) : null);
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

  openReview(validation: ValidationResponse): void {
    this.detailLoading.set(true);
    this.commentDraft.set('');
    this.detail.set(null);
    this.validationService.get(validation.id).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.detailLoading.set(false);
      },
      error: error => {
        this.detailLoading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the validation request.'));
      }
    });
  }

  closeReview(): void {
    this.detail.set(null);
    this.decision.set(null);
    this.decisionComment.set('');
  }

  reloadDetail(): void {
    const current = this.detail();
    if (!current) {
      return;
    }
    this.validationService.get(current.validation.id).subscribe({
      next: detail => this.detail.set(detail),
      error: error => this.error.set(apiErrorMessage(error, 'Unable to refresh the validation request.'))
    });
  }

  addComment(): void {
    const current = this.detail();
    const message = this.commentDraft().trim();
    if (!current || !message) {
      return;
    }
    this.postingComment.set(true);
    this.validationService.addComment(current.validation.id, message).subscribe({
      next: () => {
        this.postingComment.set(false);
        this.commentDraft.set('');
        this.reloadDetail();
        this.reload();
      },
      error: error => {
        this.postingComment.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to post the comment.'));
      }
    });
  }

  openDecision(kind: 'approve' | 'reject'): void {
    this.decision.set(kind);
    this.decisionComment.set('');
  }

  confirmDecision(): void {
    const current = this.detail();
    const kind = this.decision();
    if (!current || !kind) {
      return;
    }
    if (kind === 'reject' && !this.decisionComment().trim()) {
      this.error.set('A comment is required to reject the document.');
      return;
    }
    this.deciding.set(true);
    const action = kind === 'approve'
      ? this.validationService.approve(current.validation.id, this.decisionComment() || null)
      : this.validationService.reject(current.validation.id, this.decisionComment());
    action.subscribe({
      next: detail => {
        this.deciding.set(false);
        this.decision.set(null);
        this.decisionComment.set('');
        this.detail.set(detail);
        this.notice.set(kind === 'approve' ? 'Validation approved.' : 'Validation rejected.');
        this.reload();
      },
      error: error => {
        this.deciding.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to record the decision.'));
      }
    });
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  initials(value: string | null | undefined): string {
    return formatInitials(value);
  }

  statusTone(status: ValidationStatus): string {
    switch (status) {
      case 'APPROVED':
        return 'tone-green';
      case 'REJECTED':
        return 'tone-red';
      default:
        return 'tone-amber';
    }
  }

  private poll(): void {
    const validations = this.page()?.content ?? [];
    const hasPending = validations.some(validation => validation.status === 'PENDING');
    if (!hasPending || this.loading()) {
      return;
    }
    this.validationService.list(this.status(), this.pageIndex(), this.pageSize()).subscribe({
      next: page => this.page.set(page)
    });
  }
}
