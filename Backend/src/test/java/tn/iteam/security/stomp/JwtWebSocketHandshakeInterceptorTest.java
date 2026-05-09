package tn.iteam.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtWebSocketHandshakeInterceptorTest {

    @Test
    void handshakeRejectsMissingToken() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        JwtWebSocketHandshakeInterceptor interceptor = new JwtWebSocketHandshakeInterceptor(jwtDecoder);

        ServletServerHttpRequest request = new ServletServerHttpRequest(new MockHttpServletRequest("GET", "/ws"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void handshakeAcceptsAccessTokenQueryParameter() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("alg", "none"),
                Map.of("sub", "user-1")
        );
        when(jwtDecoder.decode("abc123")).thenReturn(jwt);

        JwtWebSocketHandshakeInterceptor interceptor = new JwtWebSocketHandshakeInterceptor(jwtDecoder);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.setQueryString("access_token=abc123");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(JwtWebSocketHandshakeInterceptor.JWT_ATTRIBUTE, jwt);
    }
}
