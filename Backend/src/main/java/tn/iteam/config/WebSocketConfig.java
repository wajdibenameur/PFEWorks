package tn.iteam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import tn.iteam.security.stomp.JwtPrincipalHandshakeHandler;
import tn.iteam.security.stomp.JwtWebSocketHandshakeInterceptor;
import tn.iteam.security.stomp.StompSecurityChannelInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final JwtWebSocketHandshakeInterceptor handshakeInterceptor;
    private final JwtPrincipalHandshakeHandler handshakeHandler;
    private final StompSecurityChannelInterceptor stompSecurityChannelInterceptor;
    private final TaskScheduler websocketHeartbeatTaskScheduler;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins,
            JwtWebSocketHandshakeInterceptor handshakeInterceptor,
            JwtPrincipalHandshakeHandler handshakeHandler,
            StompSecurityChannelInterceptor stompSecurityChannelInterceptor,
            @Qualifier("websocketHeartbeatTaskScheduler") TaskScheduler websocketHeartbeatTaskScheduler
    ) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.stompSecurityChannelInterceptor = stompSecurityChannelInterceptor;
        this.websocketHeartbeatTaskScheduler = websocketHeartbeatTaskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(websocketHeartbeatTaskScheduler)
                .setHeartbeatValue(new long[]{10000, 10000});
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompSecurityChannelInterceptor);
    }
}
