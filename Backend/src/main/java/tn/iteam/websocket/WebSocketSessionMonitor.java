package tn.iteam.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

@Component
public class WebSocketSessionMonitor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionMonitor.class);

    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> activeSessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastSeenByUser = new ConcurrentHashMap<>();

    private static final long USER_STALE_TTL_SECONDS = 45;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        int count = activeSessions.incrementAndGet();
        String username = extractUsername(event.getMessage());
        if (username != null && !username.isBlank()) {
            activeSessionsByUser.computeIfAbsent(username, ignored -> new AtomicInteger(0)).incrementAndGet();
            lastSeenByUser.put(username, Instant.now());
        }
        log.debug("WebSocket session connected. activeSessions={}", count);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        int count = activeSessions.updateAndGet(value -> Math.max(0, value - 1));
        String username = extractUsername(event.getMessage());
        if (username != null && !username.isBlank()) {
            activeSessionsByUser.computeIfPresent(username, (key, counter) -> {
                int next = Math.max(0, counter.decrementAndGet());
                if (next == 0) {
                    lastSeenByUser.remove(key);
                    return null;
                }
                return counter;
            });
        }
        log.debug("WebSocket session disconnected. activeSessions={}", count);
    }

    public int getActiveSessions() {
        return activeSessions.get();
    }

    public boolean isUserConnected(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        AtomicInteger count = activeSessionsByUser.get(username);
        if (count == null || count.get() <= 0) {
            return false;
        }
        Instant lastSeen = lastSeenByUser.get(username);
        return lastSeen != null && lastSeen.isAfter(Instant.now().minusSeconds(USER_STALE_TTL_SECONDS));
    }

    public void touchUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (activeSessionsByUser.containsKey(username)) {
            lastSeenByUser.put(username, Instant.now());
        }
    }

    @Scheduled(fixedDelayString = "${app.websocket.presence-sweep-ms:15000}")
    public void evictStaleUsers() {
        Instant cutoff = Instant.now().minusSeconds(USER_STALE_TTL_SECONDS);
        lastSeenByUser.forEach((username, lastSeen) -> {
            if (lastSeen != null && lastSeen.isBefore(cutoff)) {
                activeSessionsByUser.remove(username);
                lastSeenByUser.remove(username);
            }
        });
    }

    private String extractUsername(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return null;
        }
        Principal principal = accessor.getUser();
        return principal != null ? principal.getName() : null;
    }
}
