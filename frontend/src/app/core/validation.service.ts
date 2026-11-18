import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import {
  PageResponse,
  ValidationCommentResponse,
  ValidationDetailResponse,
  ValidationResponse,
  ValidationStatus
} from '../core/models';

@Injectable({ providedIn: 'root' })
export class ValidationService {
  private readonly api = inject(ApiService);

  list(status: ValidationStatus | null, page = 0, size = 20): Observable<PageResponse<ValidationResponse>> {
    return this.api.get<PageResponse<ValidationResponse>>('/validations', { status, page, size });
  }

  get(id: string): Observable<ValidationDetailResponse> {
    return this.api.get<ValidationDetailResponse>(`/validations/${id}`);
  }

  approve(id: string, comment?: string | null): Observable<ValidationDetailResponse> {
    return this.api.post<ValidationDetailResponse>(`/validations/${id}/approve`, { comment: comment ?? null });
  }

  reject(id: string, comment: string): Observable<ValidationDetailResponse> {
    return this.api.post<ValidationDetailResponse>(`/validations/${id}/reject`, { comment });
  }

  addComment(id: string, message: string): Observable<ValidationCommentResponse> {
    return this.api.post<ValidationCommentResponse>(`/validations/${id}/comments`, { message });
  }

  forDocument(documentId: string): Observable<ValidationResponse[]> {
    return this.api.get<ValidationResponse[]>(`/documents/${documentId}/validations`);
  }

  submit(documentId: string, comment?: string | null): Observable<ValidationDetailResponse> {
    return this.api.post<ValidationDetailResponse>(`/documents/${documentId}/validations`, { comment: comment ?? null });
  }
}
