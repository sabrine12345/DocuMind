import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { IconModule } from '../../shared/icons';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, RouterLink, IconModule],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.css'
})
export class ChatComponent {
  readonly auth = inject(AuthService);
  readonly workspaceName = computed(() => this.auth.activeWorkspace()?.name ?? 'this workspace');
}
