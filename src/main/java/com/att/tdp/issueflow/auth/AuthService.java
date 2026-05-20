package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.security.JwtProperties;
import com.att.tdp.issueflow.security.JwtService;
import com.att.tdp.issueflow.security.TokenDenyListService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenDenyListService tokenDenyListService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            TokenDenyListService tokenDenyListService,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.tokenDenyListService = tokenDenyListService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        String username = authentication.getName();
        User user = findActiveUser(username);
        String token = jwtService.issue(user.getUsername(), user.getRole().name());
        auditService.log(
                AuditAction.LOGIN,
                "User",
                user.getId(),
                user.getId(),
                AuditActor.USER,
                Map.of("username", user.getUsername())
        );
        return AuthTokenResponse.bearer(token, jwtProperties.expirationMinutes() * 60);
    }

    @Transactional
    public void logout(Authentication authentication, String jti, Instant expiresAt) {
        User user = findActiveUser(extractUsername(authentication));
        if (jti == null || expiresAt == null) {
            throw new BadCredentialsException("Invalid or expired token");
        }
        tokenDenyListService.revoke(jti, expiresAt);
        auditService.log(
                AuditAction.LOGOUT,
                "User",
                user.getId(),
                user.getId(),
                AuditActor.USER,
                Map.of("username", user.getUsername(), "jti", jti)
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(Authentication authentication) {
        User user = findActiveUser(extractUsername(authentication));
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }

    private User findActiveUser(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String extractUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("Authentication is required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}
