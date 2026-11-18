import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/login.component').then(module => module.LoginComponent),
    data: { title: 'Sign in' }
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/register.component').then(module => module.RegisterComponent),
    data: { title: 'Create an account' }
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(module => module.DashboardComponent),
        data: { title: 'Dashboard' }
      },
      {
        path: 'documents',
        loadComponent: () => import('./pages/documents/documents.component').then(module => module.DocumentsComponent),
        data: { title: 'Documents' }
      },
      {
        path: 'documents/:id',
        loadComponent: () => import('./pages/documents/document-detail.component').then(module => module.DocumentDetailComponent),
        data: { title: 'Document detail' }
      },
      {
        path: 'processing',
        loadComponent: () => import('./pages/processing/processing.component').then(module => module.ProcessingComponent),
        data: { title: 'Processing' }
      },
      {
        path: 'templates',
        loadComponent: () => import('./pages/templates/templates.component').then(module => module.TemplatesComponent),
        data: { title: 'Templates' }
      },
      {
        path: 'validation',
        loadComponent: () => import('./pages/validation/validation.component').then(module => module.ValidationComponent),
        data: { title: 'Validation' }
      },
      {
        path: 'usage',
        loadComponent: () => import('./pages/usage/usage.component').then(module => module.UsageComponent),
        data: { title: 'AI usage & cost' }
      },
      {
        path: 'chat',
        loadComponent: () => import('./pages/chat/chat.component').then(module => module.ChatComponent),
        data: { title: 'AI chat' }
      },
      {
        path: 'settings',
        loadComponent: () => import('./pages/settings/settings.component').then(module => module.SettingsComponent),
        data: { title: 'Settings' }
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
