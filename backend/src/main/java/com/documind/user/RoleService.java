package com.documind.user;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small in-memory catalog of the seeded role rows.
 */
@Service
public class RoleService {

    private final RoleRepository roles;
    private final Map<RoleName, RoleEntity> cache = new EnumMap<>(RoleName.class);

    public RoleService(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public RoleEntity require(RoleName name) {
        return cache.computeIfAbsent(name, key -> roles.findByName(key)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + key)));
    }
}
