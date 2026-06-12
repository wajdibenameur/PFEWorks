package tn.iteam.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void bearerTokenResolverAcceptsAuthorizationHeaderOnly() {
        BearerTokenResolver resolver = new DefaultBearerTokenResolver();

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
