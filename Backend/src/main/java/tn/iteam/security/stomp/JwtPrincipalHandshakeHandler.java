package tn.iteam.security.stomp;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;

import java.security.Principal;
import java.util.Map;

@Component
public class JwtPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private final KeycloakJwtAuthenticationConverter authenticationConverter;

    public JwtPrincipalHandshakeHandler(KeycloakJwtAuthenticationConverter authenticationConverter) {
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Object jwtAttribute = attributes.get(JwtWebSocketHandshakeInterceptor.JWT_ATTRIBUTE);
        if (jwtAttribute instanceof Jwt jwt) {
            AbstractAuthenticationToken authentication = authenticationConverter.convert(jwt);
            return authentication;
        }

        return super.determineUser(request, wsHandler, attributes);
    }
}
