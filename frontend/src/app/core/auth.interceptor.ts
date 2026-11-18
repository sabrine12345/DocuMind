import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

const AUTH_ENDPOINT = /\/auth\/(login|register|refresh|logout)$/;

/**
 * Attaches the bearer token and active workspace, and transparently refreshes
 * the access token once on a 401 response.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);

  if (!request.url.startsWith(environment.apiBaseUrl)) {
    return next(request);
  }

  const isAuthCall = AUTH_ENDPOINT.test(request.url);
  const workspaceId = auth.activeWorkspaceId();
  const headers: Record<string, string> = {};
  const token = auth.accessToken();

  if (token && !isAuthCall) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (workspaceId) {
    headers['X-Workspace-Id'] = workspaceId;
  }

  const authorized = Object.keys(headers).length > 0 ? request.clone({ setHeaders: headers }) : request;

  return next(authorized).pipe(
    catchError(error => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthCall) {
        return throwError(() => error);
      }
      if (!auth.refreshToken()) {
        auth.forceLogout();
        return throwError(() => error);
      }
      return auth.refreshTokens().pipe(
        switchMap(() => {
          const retryHeaders: Record<string, string> = {
            Authorization: `Bearer ${auth.accessToken()}`
          };
          const currentWorkspace = auth.activeWorkspaceId();
          if (currentWorkspace) {
            retryHeaders['X-Workspace-Id'] = currentWorkspace;
          }
          return next(request.clone({ setHeaders: retryHeaders }));
        }),
        catchError(refreshError => {
          auth.forceLogout();
          return throwError(() => refreshError);
        })
      );
    })
  );
};
