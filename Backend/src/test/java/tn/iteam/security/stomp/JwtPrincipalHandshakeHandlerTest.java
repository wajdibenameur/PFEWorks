package tn.iteam.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtPrincipalHandshakeHandlerTest {

    @Test
    void determineUserReturnsAuthenticatedPrincipalFromConverter() {
        KeycloakJwtAuthenticationConverter authenticationConverter = mock(KeycloakJwtAuthenticationConverter.class);
        TestableJwtPrincipalHandshakeHandler handshakeHandler =
                new TestableJwtPrincipalHandshakeHandler(authenticationConverter);

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("alg", "none"),
                Map.of("sub", "user-1")
        );
        AbstractAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwt,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
        );
        when(authenticationConverter.convert(jwt)).thenReturn(authentication);

        Principal principal = handshakeHandler.determine(Map.of(
                JwtWebSocketHandshakeInterceptor.JWT_ATTRIBUTE,
                jwt
        ));

        assertThat(principal).isSameAs(authentication);
        assertThat(((AbstractAuthenticationToken) principal).isAuthenticated()).isTrue();
    }

    private static final class TestableJwtPrincipalHandshakeHandler extends JwtPrincipalHandshakeHandler {

        private TestableJwtPrincipalHandshakeHandler(KeycloakJwtAuthenticationConverter authenticationConverter) {
            super(authenticationConverter);
        }

        private Principal determine(Map<String, Object> attributes) {
            return determineUser(null, null, attributes);
        }
    }
}
