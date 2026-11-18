package com.documind.security;

import com.documind.user.RoleName;
import java.util.UUID;

/**
 * Immutable authenticated principal stored in the security context.
 */
public record AuthenticatedUser(UUID id, String email, String fullName, RoleName globalRole) {
}
