import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import {
  DocumentCategory,
  DocumentResponse,
  DocumentStatus,
  DocumentVersionResponse,
  PageResponse,
  ProcessingJobResponse
} from '../core/models';

export interface DocumentQuery {
  search?: string | null;
  status?: DocumentStatus | null;
  category?: DocumentCategory | null;
  templateId?: string | null;
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly api = inject(ApiService);

  search(query: DocumentQuery): Observable<PageResponse<DocumentResponse>> {
    return this.api.get<PageResponse<DocumentResponse>>('/documents', {
      search: query.search,
      status: query.status,
      category: query.category,
      templateId: query.templateId,
      page: query.page ?? 0,
      size: query.size ?? 20,
      sort: query.sort ?? 'uploadedAt',
      direction: query.direction ?? 'desc'
    });
  }

  get(id: string): Observable<DocumentResponse> {
    return this.api.get<DocumentResponse>(`/documents/${id}`);
  }

  upload(files: File[], templateId?: string | null, category?: DocumentCategory | null): Observable<DocumentResponse[]> {
    const form = new FormData();
    files.forEach(file => form.append('files', file));
    if (templateId) {
      form.append('templateId', templateId);
    }
    if (category) {
      form.append('category', category);
    }
    return this.api.postForm<DocumentResponse[]>('/documents', form);
  }

  update(id: string, body: Partial<{
    title: string | null;
    description: string | null;
    category: DocumentCategory;
    templateId: string | null;
  }>): Observable<DocumentResponse> {
    return this.api.put<DocumentResponse>(`/documents/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.api.delete<void>(`/documents/${id}`);
  }

  download(id: string): Observable<Blob> {
    return this.api.getBlob(`/documents/${id}/download`);
  }

  preview(id: string): Observable<Blob> {
    return this.api.getBlob(`/documents/${id}/preview`);
  }

  versions(id: string): Observable<DocumentVersionResponse[]> {
    return this.api.get<DocumentVersionResponse[]>(`/documents/${id}/versions`);
  }

  addVersion(id: string, file: File): Observable<DocumentVersionResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.api.postForm<DocumentVersionResponse>(`/documents/${id}/versions`, form);
  }

  processingHistory(id: string): Observable<ProcessingJobResponse[]> {
    return this.api.get<ProcessingJobResponse[]>(`/documents/${id}/processing`);
  }
}
