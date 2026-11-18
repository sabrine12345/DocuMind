package com.documind.workspace;

import com.documind.common.BaseEntity;
import com.documind.user.RoleEntity;
import com.documind.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMemberEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "created_by")
    private UUID createdBy;

    protected WorkspaceMemberEntity() {
    }

    public WorkspaceMemberEntity(UUID workspaceId, UserEntity user, RoleEntity role, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.user = user;
        this.role = role;
        this.createdBy = createdBy;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UserEntity getUser() {
        return user;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
