package tn.iteam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private static final Logger log = LoggerFactory.getLogger(AudienceValidator.class);
    private final String expectedAudience;
    private static final OAuth2Error INVALID_AUDIENCE =
            new OAuth2Error("invalid_token", "The required audience claim is missing", null);

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        String subject = token.getSubject();
        if (audience != null && audience.contains(expectedAudience)) {
            log.debug("JWT audience validation passed: expectedAud={} tokenAud={} subject={}",
                    expectedAudience, audience, subject);
            return OAuth2TokenValidatorResult.success();
        }
        log.warn("JWT audience validation failed: expectedAud={} tokenAud={} subject={} issuer={}",
                expectedAudience, audience, subject, token.getIssuer());
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
