package com.documind.user;

import com.documind.common.BadRequestException;
import com.documind.common.NotFoundException;
import com.documind.security.CurrentUserProvider;
import com.documind.security.RefreshTokenRepository;
import com.documind.security.dto.ChangePasswordRequest;
import com.documind.security.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final CurrentUserProvider currentUser;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokens, CurrentUserProvider currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.currentUser = currentUser;
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        UserEntity user = users.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        user.setFullName(request.fullName().trim());
        if (request.jobTitle() != null) {
            String jobTitle = request.jobTitle().trim();
            user.setJobTitle(jobTitle.isEmpty() ? null : jobTitle);
        }
        users.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserEntity user = users.findById(currentUser.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must be different from the current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        // Invalidate every existing refresh token after a credential change.
        refreshTokens.findAllByUserIdAndRevokedAtIsNull(user.getId()).forEach(token -> {
            token.revoke();
            refreshTokens.save(token);
        });
    }
}
