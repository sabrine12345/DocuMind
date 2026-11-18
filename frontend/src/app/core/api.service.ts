import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

type Query = Record<string, string | number | boolean | null | undefined>;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  get<T>(path: string, query?: Query): Observable<T> {
    return this.http.get<T>(this.baseUrl + path, { params: this.params(query) });
  }

  getBlob(path: string): Observable<Blob> {
    return this.http.get(this.baseUrl + path, { responseType: 'blob' });
  }

  post<T>(path: string, body?: unknown): Observable<T> {
    return this.http.post<T>(this.baseUrl + path, body ?? {});
  }

  postForm<T>(path: string, form: FormData): Observable<T> {
    return this.http.post<T>(this.baseUrl + path, form);
  }

  put<T>(path: string, body?: unknown): Observable<T> {
    return this.http.put<T>(this.baseUrl + path, body ?? {});
  }

  delete<T = void>(path: string): Observable<T> {
    return this.http.delete<T>(this.baseUrl + path);
  }

  private params(query?: Query): HttpParams {
    let params = new HttpParams();
    if (!query) {
      return params;
    }
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
