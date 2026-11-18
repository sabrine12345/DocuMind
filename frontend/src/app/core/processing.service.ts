import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { PageResponse, ProcessingJobResponse, ProcessingJobStatus } from '../core/models';

@Injectable({ providedIn: 'root' })
export class ProcessingService {
  private readonly api = inject(ApiService);

  list(status: ProcessingJobStatus | null, page = 0, size = 20): Observable<PageResponse<ProcessingJobResponse>> {
    return this.api.get<PageResponse<ProcessingJobResponse>>('/processing/jobs', { status, page, size });
  }

  get(id: string): Observable<ProcessingJobResponse> {
    return this.api.get<ProcessingJobResponse>(`/processing/jobs/${id}`);
  }

  retry(id: string): Observable<ProcessingJobResponse> {
    return this.api.post<ProcessingJobResponse>(`/processing/jobs/${id}/retry`);
  }
}
