package tn.iteam.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import tn.iteam.auth.dto.*;
import tn.iteam.auth.service.AuthCookieService;
import tn.iteam.auth.service.AuthService;
import tn.iteam.auth.service.OidcCallbackTicketService;

import java.util.Map;
import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final OidcCallbackTicketService oidcCallbackTicketService;
    private final String frontendCallbackUrl;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookieService,
            OidcCallbackTicketService oidcCallbackTicketService,
            @Value("${app.auth.frontend-callback-url:http://localhost:4200/auth/callback}") String frontendCallbackUrl
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.oidcCallbackTicketService = oidcCallbackTicketService;
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    /**
     * Authenticates a user and returns Keycloak access + refresh tokens.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> loginRedirect() {
        String state = generateState();
        String authorizationUrl = authService.buildAuthorizationUrl(state);
        ResponseCookie stateCookie = authCookieService.buildStateCookie(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Set-Cookie", stateCookie.toString())
                .header("Location", authorizationUrl)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for user: {}", request.getUsername());
        TokenResponse response = authService.login(request);
        String refreshToken = response.getRefreshToken();
        TokenResponse sanitized = sanitizeForFrontend(response);
        ResponseCookie refreshCookie = authCookieService.buildRefreshCookie(refreshToken);
        log.info("REFRESH COOKIE SET on login name={} path={} maxAge={}s", refreshCookie.getName(), refreshCookie.getPath(), refreshCookie.getMaxAge().getSeconds());
        log.info("Login completed successfully for user: {}", request.getUsername());
        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(sanitized);
    }

    /**
     * Refreshes an access token using the provided refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            HttpServletRequest httpServletRequest
    ) {
        log.info("Refresh token request received");
        String xsrfHeader = httpServletRequest != null ? httpServletRequest.getHeader("X-XSRF-TOKEN") : null;
        log.info(
                "REFRESH REQUEST CONTEXT xsrfHeaderPresent={} refreshCookiePresent={}",
                xsrfHeader != null && !xsrfHeader.isBlank(),
                resolveRefreshToken(httpServletRequest) != null
        );
        String refreshToken = resolveRefreshToken(httpServletRequest);
        TokenResponse response = authService.refresh(refreshToken);
        String nextRefreshToken = response.getRefreshToken();
        TokenResponse sanitized = sanitizeForFrontend(response);
        ResponseCookie refreshCookie = authCookieService.buildRefreshCookie(nextRefreshToken);
        log.info("REFRESH COOKIE ROTATED on refresh name={} path={} maxAge={}s", refreshCookie.getName(), refreshCookie.getPath(), refreshCookie.getMaxAge().getSeconds());
        log.info("Refresh token request completed successfully");
        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(sanitized);
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
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = resolveRefreshToken(request);
        authService.logout(refreshToken);
        log.info("Logout completed");
        return ResponseEntity.noContent()
                .header("Set-Cookie", authCookieService.buildExpiredRefreshCookie().toString())
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletRequest request
    ) {
        String expectedState = resolveState(request);
        if (expectedState == null || !expectedState.equals(state)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Set-Cookie", authCookieService.buildExpiredStateCookie().toString())
                    .header("Location", frontendCallbackUrl + "#error=invalid_state")
                    .build();
        }

        TokenResponse response = authService.exchangeAuthorizationCode(code);
        ResponseCookie refreshCookie = authCookieService.buildRefreshCookie(response.getRefreshToken());
        String ticket = oidcCallbackTicketService.issue(response.getAccessToken());
        String redirectUrl = frontendCallbackUrl + "#ticket=" + ticket;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Set-Cookie", refreshCookie.toString())
                .header("Set-Cookie", authCookieService.buildExpiredStateCookie().toString())
                .header("Location", redirectUrl)
                .build();
    }

    @GetMapping("/callback/exchange")
    public ResponseEntity<TokenResponse> exchangeCallbackTicket(@RequestParam("ticket") String ticket) {
        String accessToken = oidcCallbackTicketService.consume(ticket);
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(null);
        response.setTokenType("Bearer");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
    }

    private String resolveRefreshToken(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null
                        && authCookieService.refreshCookieName().equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue().trim();
                }
            }
        }

        return null;
    }

    private String resolveState(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null
                        && authCookieService.stateCookieName().equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isBlank()) {
                    return cookie.getValue().trim();
                }
            }
        }
        return null;
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private TokenResponse sanitizeForFrontend(TokenResponse source) {
        TokenResponse sanitized = new TokenResponse();
        sanitized.setAccessToken(source.getAccessToken());
        sanitized.setTokenType(source.getTokenType());
        sanitized.setExpiresIn(source.getExpiresIn());
        sanitized.setRefreshExpiresIn(source.getRefreshExpiresIn());
        sanitized.setRefreshToken(null);
        return sanitized;
    }
}

