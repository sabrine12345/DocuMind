package com.documind.workspace;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.security.CurrentUserProvider;
import com.documind.storage.StorageProperties;
import com.documind.storage.StorageProvider;
import com.documind.storage.StorageService;
import com.documind.template.TemplateService;
import com.documind.user.RoleName;
import com.documind.user.RoleService;
import com.documind.user.UserEntity;
import com.documind.user.UserRepository;
import com.documind.workspace.dto.AddMemberRequest;
import com.documind.workspace.dto.CreateWorkspaceRequest;
import com.documind.workspace.dto.MemberResponse;
import com.documind.workspace.dto.UpdateMemberRoleRequest;
import com.documind.workspace.dto.UpdateWorkspaceRequest;
import com.documind.workspace.dto.WorkspaceDetail;
import com.documind.workspace.dto.WorkspaceSummary;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final RoleService roles;
    private final TemplateService templates;
    private final StorageService storage;
    private final StorageProperties storageProperties;
    private final WorkspaceResolver resolver;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMemberRepository members,
            UserRepository users, RoleService roles, TemplateService templates, StorageService storage,
            StorageProperties storageProperties, WorkspaceResolver resolver,
            CurrentUserProvider currentUser, AuditService audit) {
        this.workspaces = workspaces;
        this.members = members;
        this.users = users;
        this.roles = roles;
        this.templates = templates;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.resolver = resolver;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public WorkspaceEntity createForUser(UserEntity owner, String name) {
        String workspaceName = (name == null || name.isBlank())
                ? owner.getFullName() + "'s workspace" : name.trim();
        WorkspaceEntity workspace = workspaces.save(new WorkspaceEntity(
                workspaceName, uniqueSlug(workspaceName), owner.getId(), storage.provider(), owner.getId()));
        members.save(new WorkspaceMemberEntity(workspace.getId(), owner,
                roles.require(RoleName.ADMIN), owner.getId()));
        templates.createDefaultTemplates(workspace.getId(), owner.getId());
        return workspace;
    }

    @Transactional
    public WorkspaceSummary create(CreateWorkspaceRequest request) {
        UserEntity owner = users.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        WorkspaceEntity workspace = createForUser(owner, request.name());
        audit.record(workspace.getId(), AuditAction.WORKSPACE_UPDATED, "WORKSPACE", workspace.getId(),
                "Workspace created: " + workspace.getName());
        return summary(workspace, RoleName.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummary> listForUser(UserEntity user) {
        if (user.getGlobalRole().getName() == RoleName.SUPER_ADMIN) {
            return workspaces.findAllByOrderByCreatedAtAsc().stream()
                    .map(workspace -> summary(workspace, RoleName.SUPER_ADMIN))
                    .toList();
        }
        return members.findAllForUser(user.getId()).stream()
                .map(membership -> workspaces.findById(membership.getWorkspaceId())
                        .map(workspace -> summary(workspace, membership.getRole().getName()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummary> listMine() {
        UserEntity user = users.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        return listForUser(user);
    }

    @Transactional(readOnly = true)
    public WorkspaceDetail detail() {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        return new WorkspaceDetail(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                resolver.role(),
                workspace.getOwnerId(),
                members.countByWorkspaceId(workspace.getId()),
                workspace.getMaxUploadSizeMb(),
                workspace.getStorageProvider().name(),
                workspace.getStorageProvider() == StorageProvider.MINIO ? storageProperties.getBucket() : "local",
                workspace.isNotifyOnUpload(),
                workspace.isNotifyOnValidation(),
                workspace.getCreatedAt()
        );
    }

    @Transactional
    public WorkspaceDetail update(UpdateWorkspaceRequest request) {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        workspace.setName(request.name().trim());
        workspace.setMaxUploadSizeMb(request.maxUploadSizeMb());
        workspace.setNotifyOnUpload(request.notifyOnUpload());
        workspace.setNotifyOnValidation(request.notifyOnValidation());
        workspaces.save(workspace);
        audit.record(workspace.getId(), AuditAction.WORKSPACE_UPDATED, "WORKSPACE", workspace.getId(),
                "Workspace settings updated");
        return detail();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        return members.findAllForWorkspace(workspace.getId()).stream()
                .map(member -> new MemberResponse(
                        member.getId(),
                        member.getUser().getId(),
                        member.getUser().getEmail(),
                        member.getUser().getFullName(),
                        member.getRole().getName(),
                        member.getUser().getId().equals(workspace.getOwnerId()),
                        member.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MemberResponse addMember(AddMemberRequest request) {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        requireAssignableRole(request.role());
        UserEntity user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NotFoundException(
                        "No DocuMind account exists for this email. The user must register first."));
        if (members.existsByWorkspaceIdAndUser_Id(workspace.getId(), user.getId())) {
            throw new ConflictException("This user is already a member of the workspace.");
        }
        WorkspaceMemberEntity member = members.save(new WorkspaceMemberEntity(workspace.getId(), user,
                roles.require(request.role()), currentUser.id()));
        audit.record(workspace.getId(), AuditAction.MEMBER_ADDED, "WORKSPACE_MEMBER", member.getId(),
                user.getEmail() + " added as " + request.role());
        return new MemberResponse(member.getId(), user.getId(), user.getEmail(), user.getFullName(),
                request.role(), false, member.getCreatedAt());
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID memberId, UpdateMemberRoleRequest request) {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        requireAssignableRole(request.role());
        WorkspaceMemberEntity member = members.findById(memberId)
                .filter(candidate -> candidate.getWorkspaceId().equals(workspace.getId()))
                .orElseThrow(() -> new NotFoundException("Workspace member not found."));
        if (member.getUser().getId().equals(workspace.getOwnerId())) {
            throw new ConflictException("The workspace owner role cannot be changed.");
        }
        RoleName currentRole = member.getRole().getName();
        if (currentRole == RoleName.ADMIN && request.role() != RoleName.ADMIN
                && members.countByWorkspaceIdAndRole_Name(workspace.getId(), RoleName.ADMIN) <= 1) {
            throw new ConflictException("The workspace must keep at least one administrator.");
        }
        member.setRole(roles.require(request.role()));
        members.save(member);
        audit.record(workspace.getId(), AuditAction.MEMBER_ROLE_CHANGED, "WORKSPACE_MEMBER", memberId,
                member.getUser().getEmail() + ": " + currentRole + " -> " + request.role());
        return new MemberResponse(member.getId(), member.getUser().getId(), member.getUser().getEmail(),
                member.getUser().getFullName(), request.role(), false, member.getCreatedAt());
    }

    @Transactional
    public void removeMember(UUID memberId) {
        WorkspaceEntity workspace = resolver.requireWorkspace();
        WorkspaceMemberEntity member = members.findById(memberId)
                .filter(candidate -> candidate.getWorkspaceId().equals(workspace.getId()))
                .orElseThrow(() -> new NotFoundException("Workspace member not found."));
        if (member.getUser().getId().equals(workspace.getOwnerId())) {
            throw new ConflictException("The workspace owner cannot be removed.");
        }
        if (member.getRole().getName() == RoleName.ADMIN
                && members.countByWorkspaceIdAndRole_Name(workspace.getId(), RoleName.ADMIN) <= 1) {
            throw new ConflictException("The workspace must keep at least one administrator.");
        }
        members.delete(member);
        audit.record(workspace.getId(), AuditAction.MEMBER_REMOVED, "WORKSPACE_MEMBER", memberId,
                member.getUser().getEmail() + " removed from workspace");
    }

    private void requireAssignableRole(RoleName role) {
        if (role == RoleName.SUPER_ADMIN) {
            throw new ConflictException("The SUPER_ADMIN role cannot be assigned to a workspace member.");
        }
    }

    private WorkspaceSummary summary(WorkspaceEntity workspace, RoleName role) {
        return new WorkspaceSummary(workspace.getId(), workspace.getName(), workspace.getSlug(), role,
                members.countByWorkspaceId(workspace.getId()), workspace.getCreatedAt());
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "workspace";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        String candidate = base;
        int suffix = 2;
        while (workspaces.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
