package com.documind.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.security.dto.AuthResponse;
import com.documind.security.dto.MeResponse;
import com.documind.security.dto.RefreshTokenRequest;
import com.documind.security.dto.RegisterRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerLoginRefreshAndProfile() {
        String email = "tester-" + UUID.randomUUID() + "@documind.test";
        RegisterRequest registration = new RegisterRequest(email, "Password123", "Jane Tester",
                "Quality manager", "Acme Corp");

        ResponseEntity<AuthResponse> registered = rest.postForEntity("/api/auth/register", registration,
                AuthResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse tokens = registered.getBody();
        assertThat(tokens).isNotNull();
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.activeWorkspaceId()).isNotNull();
        assertThat(tokens.workspaces()).hasSize(1);
        assertThat(tokens.workspaces().get(0).name()).isEqualTo("Acme Corp");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        ResponseEntity<MeResponse> me = rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), MeResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().user().email()).isEqualTo(email);
        assertThat(me.getBody().activeWorkspaceId()).isEqualTo(tokens.activeWorkspaceId());

        // Refresh tokens are single use: the previous token cannot be replayed.
        ResponseEntity<AuthResponse> refreshed = rest.postForEntity("/api/auth/refresh",
                new RefreshTokenRequest(tokens.refreshToken()), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody()).isNotNull();
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(tokens.refreshToken());

        ResponseEntity<String> replayed = rest.postForEntity("/api/auth/refresh",
                new RefreshTokenRequest(tokens.refreshToken()), String.class);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unauthenticatedAccessIsRejected() {
        ResponseEntity<String> response = rest.getForEntity("/api/dashboard/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void duplicateRegistrationIsRejected() {
        String email = "duplicate-" + UUID.randomUUID() + "@documind.test";
        RegisterRequest registration = new RegisterRequest(email, "Password123", "Duplicate User", null, null);
        ResponseEntity<AuthResponse> first = rest.postForEntity("/api/auth/register", registration,
                AuthResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> second = rest.postForEntity("/api/auth/register", registration, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
