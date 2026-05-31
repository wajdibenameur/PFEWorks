package tn.iteam.security.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import tn.iteam.enums.Permission;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.websocket.WebSocketSessionMonitor;

import java.util.List;
import java.util.Map;
import java.time.Instant;

@Component
@Slf4j
public class StompSecurityChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHENTICATION_ATTRIBUTE = "AUTHENTICATION";

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthenticationConverter authenticationConverter;
    private final StompDestinationAuthorizationService destinationAuthorizationService;
    private final WebSocketSessionMonitor webSocketSessionMonitor;

    public StompSecurityChannelInterceptor(
            JwtDecoder jwtDecoder,
            KeycloakJwtAuthenticationConverter authenticationConverter,
            StompDestinationAuthorizationService destinationAuthorizationService,
            WebSocketSessionMonitor webSocketSessionMonitor
    ) {
        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
        this.destinationAuthorizationService = destinationAuthorizationService;
        this.webSocketSessionMonitor = webSocketSessionMonitor;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            Authentication authentication = resolveAuthentication(accessor);
            accessor.setUser(authentication);
            saveAuthenticationToSession(accessor, authentication);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            webSocketSessionMonitor.touchUser(authentication.getName());
            log.info("[STOMP AUTH] CONNECT ok sessionId={} user={} destination={}",
                    accessor.getSessionId(),
                    authentication.getName(),
                    accessor.getDestination());
            return rebuildMessage(message, accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command)) {
            Authentication authentication = extractAuthentication(accessor);
            accessor.setUser(authentication);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            ensureAuthenticationNotExpired(authentication);
            authorizeDestination(authentication, accessor.getDestination());
            webSocketSessionMonitor.touchUser(authentication.getName());
            if (StompCommand.SEND.equals(command)) {
                log.info("[STOMP AUTH] SEND ok sessionId={} user={} destination={}",
                        accessor.getSessionId(),
                        authentication.getName(),
                        accessor.getDestination());
            }
            return rebuildMessage(message, accessor);
        }

        return message;
    }

    private Message<?> rebuildMessage(Message<?> original, StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(original.getPayload(), accessor.getMessageHeaders());
    }

    private Authentication resolveAuthentication(StompHeaderAccessor accessor) {
        String authorizationHeader = firstNativeHeader(accessor, "Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthenticationCredentialsNotFoundException("Missing Bearer token for STOMP CONNECT");
        }

        String token = authorizationHeader.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return authenticationConverter.convert(jwt);
        } catch (JwtException exception) {
            throw new AuthenticationCredentialsNotFoundException("Invalid JWT for STOMP CONNECT", exception);
        }
    }

    private Authentication extractAuthentication(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication) {
            return authentication;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object storedAuthentication = sessionAttributes.get(AUTHENTICATION_ATTRIBUTE);
            if (storedAuthentication instanceof Authentication authentication) {
                accessor.setUser(authentication);
                return authentication;
            }
        }

        String authorizationHeader = firstNativeHeader(accessor, "Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            Authentication authentication = resolveAuthentication(accessor);
            accessor.setUser(authentication);
            saveAuthenticationToSession(accessor, authentication);
            return authentication;
        }

        log.warn("[STOMP AUTH] missing authentication sessionId={} destination={} command={}",
                accessor.getSessionId(),
                accessor.getDestination(),
                accessor.getCommand());
        throw new AuthenticationCredentialsNotFoundException("Unauthenticated STOMP session");
    }

    private void saveAuthenticationToSession(StompHeaderAccessor accessor, Authentication authentication) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(AUTHENTICATION_ATTRIBUTE, authentication);
        }
    }

    private void authorizeDestination(Authentication authentication, String destination) {
        Permission requiredPermission = destinationAuthorizationService.requiredPermission(destination);
        if (requiredPermission == null) {
            throw new AccessDeniedException("Unknown STOMP destination: " + destination);
        }

        boolean allowed = authentication.getAuthorities().contains(new SimpleGrantedAuthority(requiredPermission.name()));
        if (!allowed) {
            throw new AccessDeniedException("Missing permission " + requiredPermission.name() + " for destination " + destination);
        }
    }

    private void ensureAuthenticationNotExpired(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Invalid STOMP authentication principal");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new AuthenticationCredentialsNotFoundException("STOMP token is expired");
        }
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
