import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import * as pdfjs from 'pdfjs-dist';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { DocumentsService } from '../../core/documents.service';
import {
  DOCUMENT_CATEGORIES,
  DocumentCategory,
  DocumentResponse,
  DocumentVersionResponse,
  ExtractedFieldResponse,
  ProcessingJobResponse,
  TemplateResponse,
  ValidationResponse
} from '../../core/models';
import { ExtractionService, TemplatesService } from '../../core/templates.service';
import { ValidationService } from '../../core/validation.service';
import { formatBytes, humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdfjs/pdf.worker.min.mjs';

type Tab = 'preview' | 'fields' | 'versions' | 'validation' | 'details';

@Component({
  selector: 'app-document-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconModule],
  templateUrl: './document-detail.component.html',
  styleUrl: './document-detail.component.css'
})
export class DocumentDetailComponent implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly documentsService = inject(DocumentsService);
  private readonly extractionService = inject(ExtractionService);
  private readonly templatesService = inject(TemplatesService);
  private readonly validationService = inject(ValidationService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly auth = inject(AuthService);
  readonly categories = DOCUMENT_CATEGORIES;
  readonly documentId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly doc = signal<DocumentResponse | null>(null);
  readonly fields = signal<ExtractedFieldResponse[]>([]);
  readonly versions = signal<DocumentVersionResponse[]>([]);
  readonly jobs = signal<ProcessingJobResponse[]>([]);
  readonly validations = signal<ValidationResponse[]>([]);
  readonly templates = signal<TemplateResponse[]>([]);
  readonly fieldDrafts = signal<Record<string, string | undefined>>({});

  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly activeTab = signal<Tab>('preview');
  readonly savingField = signal<string | null>(null);
  readonly preparing = signal(false);

  readonly submitting = signal(false);
  readonly submitComment = signal('');
  readonly showSubmitModal = signal(false);

  readonly decision = signal<'approve' | 'reject' | null>(null);
  readonly decisionComment = signal('');
  readonly deciding = signal(false);

  readonly editTitle = signal('');
  readonly editDescription = signal('');
  readonly editCategory = signal<DocumentCategory>('OTHER');
  readonly editTemplateId = signal('');
  readonly savingDetails = signal(false);
  readonly uploadingVersion = signal(false);

  readonly pageNumber = signal(1);
  readonly zoom = signal(1);
  readonly totalPages = signal(0);
  readonly previewUrl = signal<SafeUrl | null>(null);
  readonly previewIsPdf = signal(false);
  readonly rendering = signal(false);

  private pdf: PDFDocumentProxy | null = null;
  private canvasRef?: ElementRef<HTMLCanvasElement>;
  private pollTimer?: ReturnType<typeof setInterval>;
  private previewLoaded = false;

  constructor() {
    this.load();
    this.pollTimer = setInterval(() => this.poll(), 5000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
    if (this.pdf) {
      void this.pdf.destroy();
    }
  }

  @ViewChild('pdfCanvas')
  set pdfCanvas(ref: ElementRef<HTMLCanvasElement> | undefined) {
    this.canvasRef = ref ?? undefined;
    if (ref) {
      void this.renderPage();
    }
  }

  load(): void {
    if (!this.documentId) {
      this.error.set('Missing document identifier.');
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.documentsService.get(this.documentId).subscribe({
      next: doc => {
        this.doc.set(doc);
        this.editTitle.set(doc.title ?? '');
        this.editDescription.set(doc.description ?? '');
        this.editCategory.set(doc.category);
        this.editTemplateId.set(doc.templateId ?? '');
        this.loading.set(false);
        this.loadFields();
        this.loadVersions();
        this.loadJobs();
        this.loadValidations();
        this.loadTemplates();
        if (this.activeTab() === 'preview') {
          this.ensurePreview();
        }
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the document.'));
      }
    });
  }

  setTab(tab: Tab): void {
    this.activeTab.set(tab);
    if (tab === 'preview') {
      this.ensurePreview();
    }
  }

  updateDraft(fieldId: string, value: string): void {
    this.fieldDrafts.update(drafts => ({ ...drafts, [fieldId]: value }));
  }

  saveField(field: ExtractedFieldResponse): void {
    const value = this.fieldDrafts()[field.id] ?? '';
    this.savingField.set(field.id);
    this.error.set('');
    this.extractionService.updateValue(field.id, value.trim() ? value : null).subscribe({
      next: updated => {
        this.replaceField(updated);
        this.savingField.set(null);
        this.notice.set(`Field "${field.fieldLabel}" saved.`);
      },
      error: error => {
        this.savingField.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to save the field.'));
      }
    });
  }

  toggleFieldValidation(field: ExtractedFieldResponse): void {
    const validated = field.status !== 'VALIDATED';
    this.savingField.set(field.id);
    this.extractionService.validate(field.id, validated).subscribe({
      next: updated => {
        this.replaceField(updated);
        this.savingField.set(null);
      },
      error: error => {
        this.savingField.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to update the field validation.'));
      }
    });
  }

  validateAllFields(): void {
    this.extractionService.validateAll(this.documentId).subscribe({
      next: fields => {
        this.fields.set(fields);
        this.notice.set('All filled fields were validated.');
      },
      error: error => this.error.set(apiErrorMessage(error, 'Unable to validate the fields.'))
    });
  }

  prepareFields(): void {
    this.preparing.set(true);
    this.extractionService.prepare(this.documentId, this.editTemplateId() || null).subscribe({
      next: fields => {
        this.fields.set(fields);
        this.syncDrafts(fields);
        this.preparing.set(false);
        this.notice.set('Fields were prepared from the template.');
      },
      error: error => {
        this.preparing.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to prepare the fields.'));
      }
    });
  }

  submitValidation(): void {
    this.submitting.set(true);
    this.validationService.submit(this.documentId, this.submitComment() || null).subscribe({
      next: () => {
        this.submitting.set(false);
        this.showSubmitModal.set(false);
        this.submitComment.set('');
        this.notice.set('The document was submitted for validation.');
        this.load();
      },
      error: error => {
        this.submitting.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to submit the document for validation.'));
      }
    });
  }

  openDecision(kind: 'approve' | 'reject'): void {
    this.decision.set(kind);
    this.decisionComment.set('');
  }

  confirmDecision(): void {
    const pending = this.pendingValidation();
    const kind = this.decision();
    if (!pending || !kind) {
      return;
    }
    if (kind === 'reject' && !this.decisionComment().trim()) {
      this.error.set('A comment is required to reject the document.');
      return;
    }
    this.deciding.set(true);
    const action = kind === 'approve'
      ? this.validationService.approve(pending.id, this.decisionComment() || null)
      : this.validationService.reject(pending.id, this.decisionComment());
    action.subscribe({
      next: () => {
        this.deciding.set(false);
        this.decision.set(null);
        this.notice.set(kind === 'approve' ? 'Validation approved.' : 'Validation rejected.');
        this.load();
      },
      error: error => {
        this.deciding.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to record the decision.'));
      }
    });
  }

  uploadVersion(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadingVersion.set(true);
    this.documentsService.addVersion(this.documentId, file).subscribe({
      next: () => {
        this.uploadingVersion.set(false);
        this.notice.set('A new version was uploaded and queued for processing.');
        input.value = '';
        this.resetPreview();
        this.load();
      },
      error: error => {
        this.uploadingVersion.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to upload the new version.'));
      }
    });
  }

  saveDetails(): void {
    this.savingDetails.set(true);
    this.documentsService.update(this.documentId, {
      title: this.editTitle().trim() || null,
      description: this.editDescription().trim() || null,
      category: this.editCategory(),
      templateId: this.editTemplateId() || null
    }).subscribe({
      next: doc => {
        this.doc.set(doc);
        this.savingDetails.set(false);
        this.notice.set('Document metadata saved.');
      },
      error: error => {
        this.savingDetails.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to update the document.'));
      }
    });
  }

  download(): void {
    const doc = this.doc();
    if (!doc) {
      return;
    }
    this.documentsService.download(doc.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = window.document.createElement('a');
        anchor.href = url;
        anchor.download = doc.fileName;
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: error => this.error.set(apiErrorMessage(error, 'Unable to download the document.'))
    });
  }

  remove(): void {
    if (!window.confirm('Delete this document and all its history?')) {
      return;
    }
    this.documentsService.remove(this.documentId).subscribe({
      next: () => void this.router.navigate(['/documents']),
      error: error => this.error.set(apiErrorMessage(error, 'Unable to delete the document.'))
    });
  }

  changePage(delta: number): void {
    const next = this.pageNumber() + delta;
    if (next < 1 || next > this.totalPages()) {
      return;
    }
    this.pageNumber.set(next);
    void this.renderPage();
  }

  changeZoom(delta: number): void {
    const next = Math.min(3, Math.max(0.5, this.zoom() + delta));
    this.zoom.set(next);
    void this.renderPage();
  }

  pendingValidation(): ValidationResponse | null {
    return this.validations().find(validation => validation.status === 'PENDING') ?? null;
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  size(bytes: number): string {
    return formatBytes(bytes);
  }

  statusTone(status: string): string {
    switch (status) {
      case 'COMPLETED':
      case 'VALIDATED':
      case 'APPROVED':
        return 'tone-green';
      case 'FAILED':
      case 'REJECTED':
        return 'tone-red';
      case 'PROCESSING':
      case 'QUEUED':
      case 'PENDING':
        return 'tone-amber';
      case 'EDITED':
        return 'tone-blue';
      default:
        return 'tone-gray';
    }
  }

  trackField(_index: number, field: ExtractedFieldResponse): string {
    return field.id;
  }

  private loadFields(): void {
    this.extractionService.fields(this.documentId).subscribe({
      next: fields => {
        this.fields.set(fields);
        this.syncDrafts(fields);
      },
      error: () => this.fields.set([])
    });
  }

  private syncDrafts(fields: ExtractedFieldResponse[]): void {
    const drafts: Record<string, string> = {};
    fields.forEach(field => {
      drafts[field.id] = field.value ?? '';
    });
    this.fieldDrafts.set(drafts);
  }

  private replaceField(updated: ExtractedFieldResponse): void {
    this.fields.update(fields => fields.map(field => (field.id === updated.id ? updated : field)));
    this.fieldDrafts.update(drafts => ({ ...drafts, [updated.id]: updated.value ?? '' }));
  }

  private loadVersions(): void {
    this.documentsService.versions(this.documentId).subscribe({ next: versions => this.versions.set(versions), error: () => undefined });
  }

  private loadJobs(): void {
    this.documentsService.processingHistory(this.documentId).subscribe({ next: jobs => this.jobs.set(jobs), error: () => undefined });
  }

  private loadValidations(): void {
    this.validationService.forDocument(this.documentId).subscribe({
      next: validations => this.validations.set(validations),
      error: () => undefined
    });
  }

  private loadTemplates(): void {
    this.templatesService.list().subscribe({ next: templates => this.templates.set(templates), error: () => undefined });
  }

  private poll(): void {
    const doc = this.doc();
    if (!doc || (doc.status !== 'QUEUED' && doc.status !== 'PROCESSING')) {
      return;
    }
    this.documentsService.get(this.documentId).subscribe({
      next: updated => {
        const wasProcessing = doc.status === 'QUEUED' || doc.status === 'PROCESSING';
        this.doc.set(updated);
        if (wasProcessing && updated.status === 'COMPLETED') {
          this.loadFields();
          this.loadJobs();
          this.notice.set('Processing completed. Fields are ready for review.');
        }
      }
    });
  }

  private ensurePreview(): void {
    if (this.previewLoaded) {
      void this.renderPage();
      return;
    }
    const doc = this.doc();
    if (!doc) {
      return;
    }
    this.previewLoaded = true;
    this.rendering.set(true);
    this.documentsService.preview(doc.id).subscribe({
      next: async blob => {
        if (doc.contentType === 'application/pdf') {
          this.previewIsPdf.set(true);
          const buffer = await blob.arrayBuffer();
          this.pdf = await pdfjs.getDocument({ data: new Uint8Array(buffer) }).promise;
          this.totalPages.set(this.pdf.numPages);
          this.pageNumber.set(1);
          this.rendering.set(false);
          void this.renderPage();
        } else {
          this.previewIsPdf.set(false);
          const url = URL.createObjectURL(blob);
          this.previewUrl.set(this.sanitizer.bypassSecurityTrustUrl(url));
          this.rendering.set(false);
        }
      },
      error: error => {
        this.previewLoaded = false;
        this.rendering.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the preview.'));
      }
    });
  }

  private async renderPage(): Promise<void> {
    if (!this.pdf || !this.canvasRef) {
      return;
    }
    const page = await this.pdf.getPage(this.pageNumber());
    const viewport = page.getViewport({ scale: this.zoom() * 1.1 });
    const canvas = this.canvasRef.nativeElement;
    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }
    canvas.width = viewport.width;
    canvas.height = viewport.height;
    await page.render({ canvasContext: context, viewport }).promise;
  }

  private resetPreview(): void {
    if (this.pdf) {
      void this.pdf.destroy();
      this.pdf = null;
    }
    this.previewLoaded = false;
    this.previewUrl.set(null);
    this.totalPages.set(0);
    this.pageNumber.set(1);
    this.rendering.set(false);
  }
}
