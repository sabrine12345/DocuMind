package com.documind.security;

import com.documind.common.UnauthorizedException;
import com.documind.user.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal created by the JWT filter.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return find().orElseThrow(() -> new UnauthorizedException("Authentication is required."));
    }

    public UUID id() {
        return require().id();
    }

    public boolean isSuperAdmin() {
        return find().map(user -> user.globalRole() == RoleName.SUPER_ADMIN).orElse(false);
    }
}
