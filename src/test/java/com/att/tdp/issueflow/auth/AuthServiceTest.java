package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.security.JwtProperties;
import com.att.tdp.issueflow.security.JwtService;
import com.att.tdp.issueflow.security.TokenDenyListService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenDenyListService tokenDenyListService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private final JwtProperties jwtProperties = new JwtProperties(
            "test-secret-change-in-production-256-bits",
            60,
            "issueflow-test"
    );

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtService,
                jwtProperties,
                tokenDenyListService,
                userRepository,
                auditService
        );
    }

    @Test
    void loginAuthenticatesIssuesTokenAndLogsAuditEvent() {
        LoginRequest request = new LoginRequest("admin", "admin123");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                java.util.List.of()
        );
        User user = user("admin", UserRole.ADMIN);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(user));
        when(jwtService.issue("admin", "ADMIN")).thenReturn("jwt-token");

        AuthTokenResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(auditService).log(
                AuditAction.LOGIN,
                "User",
                1L,
                1L,
                AuditActor.USER,
                Map.of("username", "admin")
        );
    }

    @Test
    void logoutRevokesCurrentJtiAndLogsAuditEvent() {
        Instant expiresAt = Instant.parse("2026-05-20T12:00:00Z");
        User user = user("admin", UserRole.ADMIN);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                org.springframework.security.core.userdetails.User
                        .withUsername("admin")
                        .password("hash")
                        .authorities("ROLE_ADMIN")
                        .build(),
                null,
                java.util.List.of()
        );
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(user));

        authService.logout(authentication, "jti-1", expiresAt);

        verify(tokenDenyListService).revoke("jti-1", expiresAt);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
                org.mockito.ArgumentMatchers.eq(AuditAction.LOGOUT),
                org.mockito.ArgumentMatchers.eq("User"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(AuditActor.USER),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isEqualTo(Map.of("username", "admin", "jti", "jti-1"));
    }

    @Test
    void meReturnsCurrentUserDto() {
        User user = user("admin", UserRole.ADMIN);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                java.util.List.of()
        );
        when(userRepository.findByUsernameAndDeletedAtIsNull("admin")).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.me(authentication);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.email()).isEqualTo("admin@example.com");
        assertThat(response.fullName()).isEqualTo("Admin User");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
    }

    private User user(String username, UserRole role) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("Admin User");
        user.setRole(role);
        user.setPasswordHash("hash");
        return user;
    }
}
