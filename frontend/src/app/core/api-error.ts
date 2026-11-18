import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

const NETWORK_MESSAGE = 'Cannot reach the server. Check that the backend is running.';

/**
 * Extracts the backend ApiError message, preferring the first field error.
 */
export function apiErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return NETWORK_MESSAGE;
    }
    const body = error.error as ApiError | string | null;
    if (body && typeof body === 'object') {
      const fieldErrors = body.fieldErrors ?? {};
      const firstFieldError = Object.values(fieldErrors)[0];
      if (firstFieldError) {
        return firstFieldError;
      }
      if (body.message) {
        return body.message;
      }
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    return fallback;
  }
  return fallback;
}
