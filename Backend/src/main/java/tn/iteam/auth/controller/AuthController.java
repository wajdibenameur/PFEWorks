package tn.iteam.auth.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.auth.dto.*;
import tn.iteam.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for user: {}", request.getUsername());
        TokenResponse response = authService.login(request);
        log.info("Login completed successfully for user: {}", request.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Refreshes an access token using the provided refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Refresh token request received");
        TokenResponse response = authService.refresh(request.getRefreshToken());
        log.info("Refresh token request completed successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Registers a new user in Keycloak.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for user: {}", request.getUsername());
        RegisterResponse response = authService.register(request);
        log.info("User registration completed successfully for user: {}", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        log.info("Logout completed");
        return ResponseEntity.noContent().build();
    }
}

