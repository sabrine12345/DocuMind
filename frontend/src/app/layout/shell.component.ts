import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { initials, humanize } from '../shared/format';
import { IconModule } from '../shared/icons';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  visible: boolean;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, IconModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);

  readonly user = this.auth.user;
  readonly workspaces = this.auth.workspaces;
  readonly activeWorkspace = this.auth.activeWorkspace;
  readonly roleLabel = computed(() => humanize(this.auth.role()));

  readonly pageTitle = signal('Dashboard');
  readonly displayName = computed(() => this.user()?.fullName ?? 'DocuMind user');
  readonly initialsValue = computed(() => initials(this.user()?.fullName));

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: 'layout-dashboard', visible: true },
    { label: 'Documents', path: '/documents', icon: 'files', visible: true },
    { label: 'Processing', path: '/processing', icon: 'list-checks', visible: true },
    { label: 'Templates', path: '/templates', icon: 'layout-template', visible: true },
    { label: 'Validation', path: '/validation', icon: 'shield-check', visible: true },
    { label: 'AI Usage', path: '/usage', icon: 'sparkles', visible: this.auth.canViewAudit() },
    { label: 'AI Chat', path: '/chat', icon: 'message-square', visible: true },
    { label: 'Settings', path: '/settings', icon: 'settings', visible: true }
  ];

  constructor() {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      takeUntilDestroyed()
    ).subscribe(() => this.updateTitle());
    this.updateTitle();
  }

  switchWorkspace(event: Event): void {
    const workspaceId = (event.target as HTMLSelectElement).value;
    if (!workspaceId || workspaceId === this.auth.activeWorkspaceId()) {
      return;
    }
    this.auth.switchWorkspace(workspaceId);
    void this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this.auth.logout();
  }

  private updateTitle(): void {
    let route = this.activatedRoute.snapshot;
    while (route.firstChild) {
      route = route.firstChild;
    }
    this.pageTitle.set((route.data['title'] as string | undefined) ?? 'DocuMind');
  }
}
