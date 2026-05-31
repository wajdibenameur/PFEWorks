package tn.iteam.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    private final String refreshCookieName;
    private final String refreshCookiePath;
    private final String refreshCookieSameSite;
    private final boolean refreshCookieSecure;
    private final long refreshCookieMaxAgeSeconds;
    private final String stateCookieName;
    private final long stateCookieMaxAgeSeconds;

    public AuthCookieService(
            @Value("${app.auth.refresh-cookie.name:REFRESH_TOKEN}") String refreshCookieName,
            @Value("${app.auth.refresh-cookie.path:/api/auth}") String refreshCookiePath,
            @Value("${app.auth.refresh-cookie.same-site:Lax}") String refreshCookieSameSite,
            @Value("${app.auth.refresh-cookie.secure:false}") boolean refreshCookieSecure,
            @Value("${app.auth.refresh-cookie.max-age-seconds:2592000}") long refreshCookieMaxAgeSeconds,
            @Value("${app.auth.state-cookie.name:OIDC_STATE}") String stateCookieName,
            @Value("${app.auth.state-cookie.max-age-seconds:300}") long stateCookieMaxAgeSeconds
    ) {
        this.refreshCookieName = refreshCookieName;
        this.refreshCookiePath = refreshCookiePath;
        this.refreshCookieSameSite = refreshCookieSameSite;
        this.refreshCookieSecure = refreshCookieSecure;
        this.refreshCookieMaxAgeSeconds = refreshCookieMaxAgeSeconds;
        this.stateCookieName = stateCookieName;
        this.stateCookieMaxAgeSeconds = stateCookieMaxAgeSeconds;
    }

    public String refreshCookieName() {
        return refreshCookieName;
    }

    public ResponseCookie buildRefreshCookie(String refreshToken) {
        return ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(refreshCookieMaxAgeSeconds)
                .build();
    }

    public ResponseCookie buildExpiredRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(0)
                .build();
    }

    public String stateCookieName() {
        return stateCookieName;
    }

    public ResponseCookie buildStateCookie(String state) {
        return ResponseCookie.from(stateCookieName, state)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(stateCookieMaxAgeSeconds)
                .build();
    }

    public ResponseCookie buildExpiredStateCookie() {
        return ResponseCookie.from(stateCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(0)
                .build();
    }
}
