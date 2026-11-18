import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import {
  AddMemberRequest,
  CreateWorkspaceRequest,
  MemberResponse,
  RoleName,
  UpdateWorkspaceRequest,
  UsageSummary,
  WorkspaceDetail,
  WorkspaceSummary
} from '../core/models';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ApiService);

  list(): Observable<WorkspaceSummary[]> {
    return this.api.get<WorkspaceSummary[]>('/workspaces');
  }

  create(request: CreateWorkspaceRequest): Observable<WorkspaceSummary> {
    return this.api.post<WorkspaceSummary>('/workspaces', request);
  }

  current(): Observable<WorkspaceDetail> {
    return this.api.get<WorkspaceDetail>('/workspaces/current');
  }

  update(request: UpdateWorkspaceRequest): Observable<WorkspaceDetail> {
    return this.api.put<WorkspaceDetail>('/workspaces/current', request);
  }

  members(): Observable<MemberResponse[]> {
    return this.api.get<MemberResponse[]>('/workspaces/current/members');
  }

  addMember(request: AddMemberRequest): Observable<MemberResponse> {
    return this.api.post<MemberResponse>('/workspaces/current/members', request);
  }

  updateMemberRole(memberId: string, role: RoleName): Observable<MemberResponse> {
    return this.api.put<MemberResponse>(`/workspaces/current/members/${memberId}`, { role });
  }

  removeMember(memberId: string): Observable<void> {
    return this.api.delete<void>(`/workspaces/current/members/${memberId}`);
  }
}

@Injectable({ providedIn: 'root' })
export class UsageService {
  private readonly api = inject(ApiService);

  summary(days: number): Observable<UsageSummary> {
    return this.api.get<UsageSummary>('/usage/summary', { days });
  }
}
