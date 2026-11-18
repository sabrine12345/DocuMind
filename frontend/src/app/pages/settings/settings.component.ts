import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { apiErrorMessage } from '../../core/api-error';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MemberResponse, RoleName, UserResponse, WorkspaceDetail } from '../../core/models';
import { WorkspaceService } from '../../core/workspace.service';
import { initials, humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

const MEMBER_ROLES: RoleName[] = ['ADMIN', 'MANAGER', 'VALIDATOR', 'USER'];

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, IconModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css'
})
export class SettingsComponent {
  private readonly api = inject(ApiService);
  private readonly workspaceService = inject(WorkspaceService);
  readonly auth = inject(AuthService);

  readonly memberRoles = MEMBER_ROLES;

  readonly workspace = signal<WorkspaceDetail | null>(null);
  readonly members = signal<MemberResponse[]>([]);
  readonly loadingWorkspace = signal(true);
  readonly error = signal('');
  readonly notice = signal('');

  readonly profileFullName = signal(this.auth.user()?.fullName ?? '');
  readonly profileJobTitle = signal(this.auth.user()?.jobTitle ?? '');
  readonly savingProfile = signal(false);

  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly savingPassword = signal(false);

  readonly workspaceName = signal('');
  readonly maxUploadSizeMb = signal(25);
  readonly notifyOnUpload = signal(true);
  readonly notifyOnValidation = signal(true);
  readonly savingWorkspace = signal(false);

  readonly addMemberEmail = signal('');
  readonly addMemberRole = signal<RoleName>('USER');
  readonly addingMember = signal(false);
  readonly memberBusy = signal<string | null>(null);
  readonly memberRolesDraft = signal<Record<string, RoleName>>({});

  readonly newWorkspaceName = signal('');
  readonly creatingWorkspace = signal(false);

  readonly userInitials = computed(() => initials(this.auth.user()?.fullName));
  readonly roleLabel = computed(() => humanize(this.auth.role()));

  constructor() {
    this.loadWorkspace();
  }

  loadWorkspace(): void {
    if (!this.auth.activeWorkspaceId()) {
      this.loadingWorkspace.set(false);
      return;
    }
    this.loadingWorkspace.set(true);
    this.workspaceService.current().subscribe({
      next: workspace => {
        this.workspace.set(workspace);
        this.workspaceName.set(workspace.name);
        this.maxUploadSizeMb.set(workspace.maxUploadSizeMb);
        this.notifyOnUpload.set(workspace.notifyOnUpload);
        this.notifyOnValidation.set(workspace.notifyOnValidation);
        this.loadingWorkspace.set(false);
        this.loadMembers();
      },
      error: error => {
        this.loadingWorkspace.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load the workspace settings.'));
      }
    });
  }

  loadMembers(): void {
    this.workspaceService.members().subscribe({
      next: members => {
        this.members.set(members);
        const drafts: Record<string, RoleName> = {};
        members.forEach(member => {
          drafts[member.id] = member.role;
        });
        this.memberRolesDraft.set(drafts);
      },
      error: error => this.error.set(apiErrorMessage(error, 'Unable to load the workspace members.'))
    });
  }

  saveProfile(): void {
    const fullName = this.profileFullName().trim();
    if (!fullName) {
      this.error.set('Full name is required.');
      return;
    }
    this.savingProfile.set(true);
    this.error.set('');
    this.api.put<UserResponse>('/users/me', {
      fullName,
      jobTitle: this.profileJobTitle().trim() || null
    }).subscribe({
      next: () => {
        this.auth.reloadProfile().subscribe({
          next: () => {
            this.savingProfile.set(false);
            this.notice.set('Profile updated.');
          },
          error: () => {
            this.savingProfile.set(false);
            this.notice.set('Profile updated. Reload the page to refresh your session data.');
          }
        });
      },
      error: error => {
        this.savingProfile.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to update the profile.'));
      }
    });
  }

  changePassword(): void {
    const current = this.currentPassword();
    const next = this.newPassword();
    if (!current) {
      this.error.set('The current password is required.');
      return;
    }
    if (next.length < 8 || next.length > 100) {
      this.error.set('The new password must contain between 8 and 100 characters.');
      return;
    }
    if (!/[A-Za-z]/.test(next) || !/\d/.test(next)) {
      this.error.set('The new password must contain at least one letter and one digit.');
      return;
    }
    if (next !== this.confirmPassword()) {
      this.error.set('The password confirmation does not match.');
      return;
    }
    this.savingPassword.set(true);
    this.error.set('');
    this.api.post<void>('/users/me/password', { currentPassword: current, newPassword: next }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.notice.set('Password updated.');
      },
      error: error => {
        this.savingPassword.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to change the password.'));
      }
    });
  }

  saveWorkspace(): void {
    if (!this.auth.canManageWorkspace()) {
      return;
    }
    const name = this.workspaceName().trim();
    if (!name) {
      this.error.set('Workspace name is required.');
      return;
    }
    if (this.maxUploadSizeMb() < 1 || this.maxUploadSizeMb() > 100) {
      this.error.set('The maximum upload size must be between 1 and 100 MB.');
      return;
    }
    this.savingWorkspace.set(true);
    this.error.set('');
    this.workspaceService.update({
      name,
      maxUploadSizeMb: this.maxUploadSizeMb(),
      notifyOnUpload: this.notifyOnUpload(),
      notifyOnValidation: this.notifyOnValidation()
    }).subscribe({
      next: workspace => {
        this.workspace.set(workspace);
        this.savingWorkspace.set(false);
        this.notice.set('Workspace settings saved.');
        this.auth.reloadProfile().subscribe({ error: () => undefined });
      },
      error: error => {
        this.savingWorkspace.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to save the workspace settings.'));
      }
    });
  }

  addMember(): void {
    const email = this.addMemberEmail().trim();
    if (!email) {
      this.error.set('An email address is required.');
      return;
    }
    this.addingMember.set(true);
    this.error.set('');
    this.workspaceService.addMember({ email, role: this.addMemberRole() }).subscribe({
      next: member => {
        this.addingMember.set(false);
        this.addMemberEmail.set('');
        this.addMemberRole.set('USER');
        this.notice.set(`${member.fullName} joined the workspace as ${humanize(member.role)}.`);
        this.loadMembers();
      },
      error: error => {
        this.addingMember.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to add this member.'));
      }
    });
  }

  changeMemberRole(member: MemberResponse, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as RoleName;
    this.memberRolesDraft.update(drafts => ({ ...drafts, [member.id]: role }));
  }

  saveMemberRole(member: MemberResponse): void {
    const role = this.memberRolesDraft()[member.id] ?? member.role;
    if (role === member.role) {
      return;
    }
    this.memberBusy.set(member.id);
    this.error.set('');
    this.workspaceService.updateMemberRole(member.id, role).subscribe({
      next: updated => {
        this.memberBusy.set(null);
        this.notice.set(`${updated.fullName} is now ${humanize(updated.role)}.`);
        this.loadMembers();
      },
      error: error => {
        this.memberBusy.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to update this member role.'));
      }
    });
  }

  removeMember(member: MemberResponse): void {
    if (member.owner) {
      return;
    }
    this.memberBusy.set(member.id);
    this.error.set('');
    this.workspaceService.removeMember(member.id).subscribe({
      next: () => {
        this.memberBusy.set(null);
        this.notice.set(`${member.fullName} was removed from the workspace.`);
        this.loadMembers();
      },
      error: error => {
        this.memberBusy.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to remove this member.'));
      }
    });
  }

  createWorkspace(): void {
    const name = this.newWorkspaceName().trim();
    if (!name) {
      this.error.set('A workspace name is required.');
      return;
    }
    this.creatingWorkspace.set(true);
    this.error.set('');
    this.workspaceService.create({ name }).subscribe({
      next: workspace => {
        this.auth.reloadProfile().subscribe({
          next: () => {
            this.auth.switchWorkspace(workspace.id);
            this.creatingWorkspace.set(false);
            this.newWorkspaceName.set('');
            this.notice.set(`Workspace "${workspace.name}" created and selected.`);
            this.loadWorkspace();
          },
          error: () => {
            this.creatingWorkspace.set(false);
            this.notice.set(`Workspace "${workspace.name}" created. Reload the page to see it.`);
          }
        });
      },
      error: error => {
        this.creatingWorkspace.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to create the workspace.'));
      }
    });
  }

  memberDraftRole(member: MemberResponse): RoleName {
    return this.memberRolesDraft()[member.id] ?? member.role;
  }

  canEditMember(member: MemberResponse): boolean {
    return this.auth.canManageWorkspace() && !member.owner && member.role !== 'SUPER_ADMIN';
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  memberInitials(member: MemberResponse): string {
    return initials(member.fullName);
  }
}
