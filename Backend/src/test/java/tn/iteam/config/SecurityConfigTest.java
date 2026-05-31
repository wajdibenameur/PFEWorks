package tn.iteam.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void bearerTokenResolverAcceptsAuthorizationHeaderOnly() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(KeycloakJwtAuthenticationConverter.class),
                mock(CsrfCookieFilter.class),
                "http://localhost:4200"
        );

        BearerTokenResolver resolver = securityConfig.bearerTokenResolver();

        MockHttpServletRequest headerRequest = new MockHttpServletRequest();
        headerRequest.addHeader("Authorization", "Bearer header-token");
        assertThat(resolve(resolver, headerRequest)).isEqualTo("header-token");

        MockHttpServletRequest queryRequest = new MockHttpServletRequest();
        queryRequest.setParameter("access_token", "query-token");
        assertThat(resolve(resolver, queryRequest)).isNull();
    }

    private String resolve(BearerTokenResolver resolver, HttpServletRequest request) {
        return resolver.resolve(request);
    }
}
