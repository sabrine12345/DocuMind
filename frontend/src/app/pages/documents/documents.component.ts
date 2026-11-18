import { CommonModule } from '@angular/common';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { DocumentsService } from '../../core/documents.service';
import {
  DOCUMENT_CATEGORIES,
  DocumentCategory,
  DocumentResponse,
  DocumentStatus,
  PageResponse,
  TemplateResponse
} from '../../core/models';
import { TemplatesService } from '../../core/templates.service';
import { formatBytes, humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './documents.component.html',
  styleUrl: './documents.component.css'
})
export class DocumentsComponent implements OnDestroy {
  private readonly documentsService = inject(DocumentsService);
  private readonly templatesService = inject(TemplatesService);
  private readonly searchInput = new Subject<string>();

  readonly auth = inject(AuthService);

  readonly categories = DOCUMENT_CATEGORIES;
  readonly page = signal<PageResponse<DocumentResponse> | null>(null);
  readonly templates = signal<TemplateResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly uploadError = signal('');
  readonly uploading = signal(false);
  readonly dragActive = signal(false);
  readonly documentToDelete = signal<DocumentResponse | null>(null);

  readonly search = signal('');
  readonly status = signal<DocumentStatus | null>(null);
  readonly category = signal<DocumentCategory | null>(null);
  readonly sort = signal('uploadedAt');
  readonly direction = signal<'asc' | 'desc'>('desc');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);

  readonly uploadTemplateId = signal('');
  readonly uploadCategory = signal<DocumentCategory | ''>('');

  private pollTimer?: ReturnType<typeof setInterval>;

  constructor() {
    this.searchInput.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe(value => {
      this.search.set(value);
      this.pageIndex.set(0);
      this.reload();
    });
    this.templatesService.list().subscribe({
      next: templates => this.templates.set(templates),
      error: () => this.templates.set([])
    });
    this.reload();
    this.pollTimer = setInterval(() => this.poll(), 5000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.documentsService.search({
      search: this.search(),
      status: this.status(),
      category: this.category(),
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sort(),
      direction: this.direction()
    }).subscribe({
      next: page => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load documents.'));
      }
    });
  }

  onSearchInput(event: Event): void {
    this.searchInput.next((event.target as HTMLInputElement).value);
  }

  onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.status.set(value ? (value as DocumentStatus) : null);
    this.pageIndex.set(0);
    this.reload();
  }

  onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.category.set(value ? (value as DocumentCategory) : null);
    this.pageIndex.set(0);
    this.reload();
  }

  onSortChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    const [sort, direction] = value.split(':');
    this.sort.set(sort);
    this.direction.set(direction === 'asc' ? 'asc' : 'desc');
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

  onUploadTemplateChange(event: Event): void {
    this.uploadTemplateId.set((event.target as HTMLSelectElement).value);
  }

  onUploadCategoryChange(event: Event): void {
    this.uploadCategory.set((event.target as HTMLSelectElement).value as DocumentCategory | '');
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    this.uploadFiles(files);
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(false);
    this.uploadFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  confirmDelete(): void {
    const document = this.documentToDelete();
    if (!document) {
      return;
    }
    this.documentsService.remove(document.id).subscribe({
      next: () => {
        this.documentToDelete.set(null);
        this.reload();
      },
      error: error => {
        this.documentToDelete.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to delete the document.'));
      }
    });
  }

  download(item: DocumentResponse): void {
    this.documentsService.download(item.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = window.document.createElement('a');
        anchor.href = url;
        anchor.download = item.fileName;
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: error => this.error.set(apiErrorMessage(error, 'Unable to download the document.'))
    });
  }

  label(value: string): string {
    return humanize(value);
  }

  size(bytes: number): string {
    return formatBytes(bytes);
  }

  statusTone(status: DocumentStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'tone-green';
      case 'FAILED':
        return 'tone-red';
      default:
        return 'tone-amber';
    }
  }

  validationTone(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'tone-green';
      case 'REJECTED':
        return 'tone-red';
      case 'PENDING':
        return 'tone-amber';
      default:
        return 'tone-gray';
    }
  }

  private uploadFiles(files: File[]): void {
    if (!files.length || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.uploadError.set('');
    this.documentsService.upload(
      files,
      this.uploadTemplateId() || null,
      (this.uploadCategory() || null) as DocumentCategory | null
    ).subscribe({
      next: () => {
        this.uploading.set(false);
        this.pageIndex.set(0);
        this.reload();
      },
      error: error => {
        this.uploading.set(false);
        this.uploadError.set(apiErrorMessage(error, 'Upload failed.'));
      }
    });
  }

  private poll(): void {
    const documents = this.page()?.content ?? [];
    const active = documents.some(document => document.status === 'QUEUED' || document.status === 'PROCESSING');
    if (!active || this.loading()) {
      return;
    }
    this.documentsService.search({
      search: this.search(),
      status: this.status(),
      category: this.category(),
      page: this.pageIndex(),
      size: this.pageSize(),
      sort: this.sort(),
      direction: this.direction()
    }).subscribe({ next: page => this.page.set(page) });
  }
}
