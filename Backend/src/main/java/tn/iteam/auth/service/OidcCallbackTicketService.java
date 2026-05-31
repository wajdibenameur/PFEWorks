package tn.iteam.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.auth.exception.AuthenticationException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OidcCallbackTicketService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final long ttlSeconds;
    private final Map<String, TicketEntry> store = new ConcurrentHashMap<>();

    public OidcCallbackTicketService(
            @Value("${app.auth.callback-ticket.ttl-seconds:60}") long ttlSeconds
    ) {
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String accessToken) {
        String ticket = randomToken();
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        store.put(ticket, new TicketEntry(accessToken, expiresAt));
        return ticket;
    }

    public String consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new AuthenticationException("Invalid callback ticket");
        }

        TicketEntry entry = store.remove(ticket);
        if (entry == null) {
            throw new AuthenticationException("Callback ticket is invalid or already used");
        }
        if (entry.expiresAtEpochSecond < Instant.now().getEpochSecond()) {
            throw new AuthenticationException("Callback ticket is expired");
        }
        return entry.accessToken;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record TicketEntry(String accessToken, long expiresAtEpochSecond) {
    }
}
