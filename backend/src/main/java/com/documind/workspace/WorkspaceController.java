package com.documind.workspace;

import com.documind.workspace.dto.AddMemberRequest;
import com.documind.workspace.dto.CreateWorkspaceRequest;
import com.documind.workspace.dto.MemberResponse;
import com.documind.workspace.dto.UpdateMemberRoleRequest;
import com.documind.workspace.dto.UpdateWorkspaceRequest;
import com.documind.workspace.dto.WorkspaceDetail;
import com.documind.workspace.dto.WorkspaceSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkspaceSummary> list() {
        return service.listMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceSummary create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return service.create(request);
    }

    @GetMapping("/current")
    public WorkspaceDetail current() {
        return service.detail();
    }

    @PutMapping("/current")
    @PreAuthorize("@perm.canManageWorkspace()")
    public WorkspaceDetail update(@Valid @RequestBody UpdateWorkspaceRequest request) {
        return service.update(request);
    }

    @GetMapping("/current/members")
    public List<MemberResponse> members() {
        return service.listMembers();
    }

    @PostMapping("/current/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canManageWorkspace()")
    public MemberResponse addMember(@Valid @RequestBody AddMemberRequest request) {
        return service.addMember(request);
    }

    @PutMapping("/current/members/{memberId}")
    @PreAuthorize("@perm.canManageWorkspace()")
    public MemberResponse updateMemberRole(@PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return service.updateMemberRole(memberId, request);
    }

    @DeleteMapping("/current/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canManageWorkspace()")
    public void removeMember(@PathVariable UUID memberId) {
        service.removeMember(memberId);
    }
}
