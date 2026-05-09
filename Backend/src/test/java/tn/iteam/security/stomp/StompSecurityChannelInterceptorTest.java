package tn.iteam.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.security.KeycloakRolePermissionService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompSecurityChannelInterceptorTest {

    @Test
    void connectWithoutBearerTokenIsRejected() {
        StompSecurityChannelInterceptor interceptor = interceptorWithDecoder(mock(JwtDecoder.class));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void subscribeWithoutAuthIsRejected() {
        StompSecurityChannelInterceptor interceptor = interceptorWithDecoder(mock(JwtDecoder.class));

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/tickets");
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void subscribeWithoutRequiredPermissionIsRejected() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        when(jwtDecoder.decode("token")).thenReturn(jwtWithRoles("UNKNOWN"));
        StompSecurityChannelInterceptor interceptor = interceptorWithDecoder(jwtDecoder);

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        interceptor.preSend(connectMessage, null);

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/tickets");
        subscribeAccessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithRequiredPermissionIsAllowed() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        when(jwtDecoder.decode("token")).thenReturn(jwtWithRoles("ADMIN"));
        StompSecurityChannelInterceptor interceptor = interceptorWithDecoder(jwtDecoder);

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        interceptor.preSend(connectMessage, null);

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setDestination("/topic/tickets");
        subscribeAccessor.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        interceptor.preSend(subscribeMessage, null);
    }

    private StompSecurityChannelInterceptor interceptorWithDecoder(JwtDecoder decoder) {
        return new StompSecurityChannelInterceptor(
                decoder,
                new KeycloakJwtAuthenticationConverter(new KeycloakRolePermissionService()),
                new StompDestinationAuthorizationService()
        );
    }

    private Jwt jwtWithRoles(String... roles) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "subject-1",
                        "preferred_username", "user1",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
    }
}
