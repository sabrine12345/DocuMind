import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ExtractedFieldResponse, TemplateRequest, TemplateResponse } from '../core/models';

@Injectable({ providedIn: 'root' })
export class TemplatesService {
  private readonly api = inject(ApiService);

  list(): Observable<TemplateResponse[]> {
    return this.api.get<TemplateResponse[]>('/templates');
  }

  get(id: string): Observable<TemplateResponse> {
    return this.api.get<TemplateResponse>(`/templates/${id}`);
  }

  create(request: TemplateRequest): Observable<TemplateResponse> {
    return this.api.post<TemplateResponse>('/templates', request);
  }

  update(id: string, request: TemplateRequest): Observable<TemplateResponse> {
    return this.api.put<TemplateResponse>(`/templates/${id}`, request);
  }

  remove(id: string): Observable<void> {
    return this.api.delete<void>(`/templates/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class ExtractionService {
  private readonly api = inject(ApiService);

  fields(documentId: string): Observable<ExtractedFieldResponse[]> {
    return this.api.get<ExtractedFieldResponse[]>(`/documents/${documentId}/fields`);
  }

  prepare(documentId: string, templateId?: string | null): Observable<ExtractedFieldResponse[]> {
    return this.api.post<ExtractedFieldResponse[]>(
      `/documents/${documentId}/fields/from-template${templateId ? `?templateId=${templateId}` : ''}`
    );
  }

  updateValue(fieldId: string, value: string | null): Observable<ExtractedFieldResponse> {
    return this.api.put<ExtractedFieldResponse>(`/fields/${fieldId}`, { value });
  }

  validate(fieldId: string, validated = true): Observable<ExtractedFieldResponse> {
    return this.api.post<ExtractedFieldResponse>(`/fields/${fieldId}/validate`, { validated });
  }

  validateAll(documentId: string): Observable<ExtractedFieldResponse[]> {
    return this.api.post<ExtractedFieldResponse[]>(`/documents/${documentId}/fields/validate`);
  }
}
