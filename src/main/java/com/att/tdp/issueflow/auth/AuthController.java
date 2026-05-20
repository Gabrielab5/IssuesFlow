package com.att.tdp.issueflow.auth;

import com.att.tdp.issueflow.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, Authentication authentication) {
        String jti = (String) request.getAttribute(JwtAuthenticationFilter.JWT_JTI_ATTRIBUTE);
        Instant expiresAt = (Instant) request.getAttribute(JwtAuthenticationFilter.JWT_EXPIRY_ATTRIBUTE);
        authService.logout(authentication, jti, expiresAt);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return authService.me(authentication);
    }
}
