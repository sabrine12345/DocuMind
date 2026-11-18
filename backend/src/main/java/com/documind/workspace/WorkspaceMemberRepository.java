package com.documind.workspace;

import com.documind.user.RoleName;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, UUID> {

    @Query("select m from WorkspaceMemberEntity m where m.user.id = :userId order by m.createdAt asc")
    List<WorkspaceMemberEntity> findAllForUser(@Param("userId") UUID userId);

    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUser_Id(UUID workspaceId, UUID userId);

    @Query("select m from WorkspaceMemberEntity m where m.workspaceId = :workspaceId order by m.createdAt asc")
    List<WorkspaceMemberEntity> findAllForWorkspace(@Param("workspaceId") UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndRole_Name(UUID workspaceId, RoleName role);

    boolean existsByWorkspaceIdAndUser_Id(UUID workspaceId, UUID userId);
}
