package tn.iteam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class ResilientIssuerJwtDecoder implements JwtDecoder {

    private static final Logger log = LoggerFactory.getLogger(ResilientIssuerJwtDecoder.class);

    private final String issuerUri;
    private final String audience;
    private final Duration retryBackoff;
    private final AtomicReference<JwtDecoder> delegateRef = new AtomicReference<>();

    private volatile Instant nextRetryAt = Instant.EPOCH;
    private volatile boolean issuerAvailable = false;
    private volatile String lastFailureMessage = null;
    private volatile boolean degradedAuthModeLogged = false;

    public ResilientIssuerJwtDecoder(String issuerUri, String audience, Duration retryBackoff) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.retryBackoff = retryBackoff != null ? retryBackoff : Duration.ofSeconds(30);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        JwtDecoder delegate = resolveDelegate();
        return delegate.decode(token);
    }

    private JwtDecoder resolveDelegate() {
        JwtDecoder existing = delegateRef.get();
        if (existing != null) {
            return existing;
        }

        Instant now = Instant.now();
        if (now.isBefore(nextRetryAt)) {
            throw unavailableException(lastFailureMessage);
        }

        synchronized (this) {
            JwtDecoder cached = delegateRef.get();
            if (cached != null) {
                return cached;
            }
            Instant retryNow = Instant.now();
            if (retryNow.isBefore(nextRetryAt)) {
                throw unavailableException(lastFailureMessage);
            }

            try {
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
                OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
                OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audience);
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
                delegateRef.set(decoder);
                nextRetryAt = Instant.EPOCH;
                if (!issuerAvailable) {
                    log.info("JWT issuer is BACK online issuerUri={}", issuerUri);
                }
                issuerAvailable = true;
                degradedAuthModeLogged = false;
                lastFailureMessage = null;
                return decoder;
            } catch (RuntimeException exception) {
                issuerAvailable = false;
                lastFailureMessage = safeMessage(exception);
                nextRetryAt = retryNow.plus(retryBackoff);
                log.warn("JWT issuer unavailable issuerUri={} retryAfter={} cause={}",
                        issuerUri,
                        nextRetryAt,
                        lastFailureMessage);
                if (!degradedAuthModeLogged) {
                    log.warn("JWT issuer unavailable, authentication remains in degraded mode until issuer recovery");
                    degradedAuthModeLogged = true;
                }
                throw unavailableException(lastFailureMessage, exception);
            }
        }
    }

    private JwtException unavailableException(String message) {
        return unavailableException(message, null);
    }

    private JwtException unavailableException(String message, Throwable cause) {
        String detail = (message != null && !message.isBlank()) ? message : "JWT issuer temporarily unavailable";
        return new JwtException(detail, cause);
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable != null ? throwable.getClass().getSimpleName() : "unknown";
        }
        return throwable.getMessage();
    }
}
