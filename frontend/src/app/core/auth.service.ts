import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, tap, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, MeResponse, RoleName, UserResponse, WorkspaceSummary } from './models';

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: UserResponse;
  workspaces: WorkspaceSummary[];
  activeWorkspaceId: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  jobTitle?: string | null;
  workspaceName?: string | null;
}

const SESSION_KEY = 'documind.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly sessionSignal = signal<Session | null>(this.readSession());
  private refreshRequest$: Observable<AuthResponse> | null = null;

  readonly session = this.sessionSignal.asReadonly();
  readonly user = computed(() => this.sessionSignal()?.user ?? null);
  readonly workspaces = computed(() => this.sessionSignal()?.workspaces ?? []);
  readonly activeWorkspace = computed<WorkspaceSummary | null>(() => {
    const session = this.sessionSignal();
    return session?.workspaces.find(workspace => workspace.id === session.activeWorkspaceId) ?? null;
  });
  readonly activeWorkspaceId = computed(() => this.sessionSignal()?.activeWorkspaceId ?? null);
  readonly isAuthenticated = computed(() => Boolean(this.sessionSignal()?.accessToken));

  readonly role = computed<RoleName | null>(() => {
    const session = this.sessionSignal();
    if (!session) {
      return null;
    }
    if (session.user.globalRole === 'SUPER_ADMIN') {
      return 'SUPER_ADMIN';
    }
    return session.workspaces.find(workspace => workspace.id === session.activeWorkspaceId)?.role ?? null;
  });

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/login', request).pipe(tap(response => this.persist(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/register', request).pipe(tap(response => this.persist(response)));
  }

  reloadProfile(): Observable<MeResponse> {
    return this.api.get<MeResponse>('/auth/me').pipe(tap(profile => {
      const session = this.sessionSignal();
      if (!session) {
        return;
      }
      this.setSession({
        ...session,
        user: profile.user,
        workspaces: profile.workspaces,
        activeWorkspaceId: profile.activeWorkspaceId ?? profile.workspaces[0]?.id ?? null
      });
    }));
  }

  refreshTokens(): Observable<AuthResponse> {
    const refreshToken = this.refreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available.'));
    }
    if (!this.refreshRequest$) {
      this.refreshRequest$ = this.api.post<AuthResponse>('/auth/refresh', { refreshToken }).pipe(
        tap(response => this.persist(response)),
        catchError(error => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshRequest$ = null;
        }),
        shareReplay(1)
      );
    }
    return this.refreshRequest$;
  }

  logout(): void {
    const refreshToken = this.refreshToken();
    if (refreshToken) {
      this.api.post<void>('/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  forceLogout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  switchWorkspace(workspaceId: string): void {
    const session = this.sessionSignal();
    if (!session || !session.workspaces.some(workspace => workspace.id === workspaceId)) {
      return;
    }
    this.setSession({ ...session, activeWorkspaceId: workspaceId });
  }

  accessToken(): string | null {
    return this.sessionSignal()?.accessToken ?? null;
  }

  refreshToken(): string | null {
    return this.sessionSignal()?.refreshToken ?? null;
  }

  hasAnyRole(...roles: RoleName[]): boolean {
    const current = this.role();
    if (!current) {
      return false;
    }
    if (current === 'SUPER_ADMIN') {
      return true;
    }
    return roles.includes(current);
  }

  canUploadDocuments(): boolean {
    return this.hasAnyRole('ADMIN', 'MANAGER', 'VALIDATOR', 'USER');
  }

  canValidate(): boolean {
    return this.hasAnyRole('ADMIN', 'MANAGER', 'VALIDATOR');
  }

  canManageTemplates(): boolean {
    return this.hasAnyRole('ADMIN', 'MANAGER');
  }

  canManageWorkspace(): boolean {
    return this.hasAnyRole('ADMIN');
  }

  canViewAudit(): boolean {
    return this.hasAnyRole('ADMIN', 'MANAGER');
  }

  clearSession(): void {
    this.sessionSignal.set(null);
    localStorage.removeItem(SESSION_KEY);
  }

  private persist(response: AuthResponse): void {
    this.setSession({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      user: response.user,
      workspaces: response.workspaces,
      activeWorkspaceId: response.activeWorkspaceId ?? response.workspaces[0]?.id ?? null
    });
  }

  private setSession(session: Session): void {
    this.sessionSignal.set(session);
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  private readSession(): Session | null {
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      if (!session.accessToken || !session.refreshToken || !session.user) {
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }
}
