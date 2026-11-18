package com.documind.security;

import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.common.UnauthorizedException;
import com.documind.security.dto.AuthResponse;
import com.documind.security.dto.LoginRequest;
import com.documind.security.dto.MeResponse;
import com.documind.security.dto.RegisterRequest;
import com.documind.user.RoleName;
import com.documind.user.RoleService;
import com.documind.user.UserEntity;
import com.documind.user.UserRepository;
import com.documind.user.UserResponse;
import com.documind.workspace.WorkspaceEntity;
import com.documind.workspace.WorkspaceService;
import com.documind.workspace.dto.WorkspaceSummary;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleService roles;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final WorkspaceService workspaces;
    private final CurrentUserProvider currentUser;

    public AuthService(UserRepository users, RoleService roles, PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokens, JwtService jwtService, WorkspaceService workspaces,
            CurrentUserProvider currentUser) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.workspaces = workspaces;
        this.currentUser = currentUser;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists with this email address.");
        }
        UserEntity user = new UserEntity(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), roles.require(RoleName.USER));
        if (request.jobTitle() != null && !request.jobTitle().isBlank()) {
            user.setJobTitle(request.jobTitle().trim());
        }
        users.save(user);
        WorkspaceEntity workspace = workspaces.createForUser(user, request.workspaceName());
        return issueTokens(user, workspace.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
        UUID activeWorkspace = workspaces.listForUser(user).stream()
                .findFirst().map(WorkspaceSummary::id).orElse(null);
        return issueTokens(user, activeWorkspace);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenEntity token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("The refresh token is invalid."));
        if (!token.isUsable()) {
            throw new UnauthorizedException("The refresh token is expired or revoked.");
        }
        UserEntity user = users.findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException("The account no longer exists."));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled.");
        }
        token.revoke();
        refreshTokens.save(token);
        return issueTokens(user, null);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.revoke();
            refreshTokens.save(token);
        });
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        UserEntity user = users.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        List<WorkspaceSummary> workspaceList = workspaces.listForUser(user);
        UUID active = workspaceList.stream().findFirst().map(WorkspaceSummary::id).orElse(null);
        return new MeResponse(UserResponse.from(user), workspaceList, active);
    }

    private AuthResponse issueTokens(UserEntity user, UUID preferredWorkspaceId) {
        String rawRefreshToken = generateRefreshToken();
        refreshTokens.save(new RefreshTokenEntity(user.getId(), hash(rawRefreshToken),
                Instant.now().plusSeconds(jwtService.refreshTokenTtlSeconds())));
        List<WorkspaceSummary> workspaceList = workspaces.listForUser(user);
        UUID activeWorkspaceId = preferredWorkspaceId != null ? preferredWorkspaceId
                : workspaceList.stream().findFirst().map(WorkspaceSummary::id).orElse(null);
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                rawRefreshToken,
                jwtService.accessTokenTtlSeconds(),
                UserResponse.from(user),
                workspaceList,
                activeWorkspaceId
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
