package com.documind.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.documind.user.RoleEntity;
import com.documind.user.RoleName;
import com.documind.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService service() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-that-is-longer-than-thirty-two-characters");
        return new JwtService(properties);
    }

    @Test
    void generatesAndParsesAccessTokens() {
        UUID userId = UUID.randomUUID();
        RoleEntity role = mock(RoleEntity.class);
        when(role.getName()).thenReturn(RoleName.USER);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("jane@documind.test");
        when(user.getFullName()).thenReturn("Jane Tester");
        when(user.getGlobalRole()).thenReturn(role);

        String token = service().generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(service().parseUserId(token)).isEqualTo(userId);
    }
}
