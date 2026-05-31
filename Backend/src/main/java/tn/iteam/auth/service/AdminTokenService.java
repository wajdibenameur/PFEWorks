package tn.iteam.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tn.iteam.auth.config.KeycloakProperties;
import tn.iteam.auth.dto.TokenResponse;
import tn.iteam.auth.exception.KeycloakIntegrationException;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AdminTokenService {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenService.class);
    private static final int EXPIRY_BUFFER_SECONDS = 30;

    private final KeycloakProperties properties;
    private final RestTemplate restTemplate;
    private final ReentrantLock lock = new ReentrantLock();

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public AdminTokenService(KeycloakProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public String getAdminToken() {
        log.debug("Admin token requested");
        if (isTokenValid()) {
            log.debug("Using cached admin token");
            return cachedToken;
        }

        lock.lock();
        try {
            if (isTokenValid()) {
                log.debug("Using cached admin token after lock acquisition");
                return cachedToken;
            }
            return fetchNewAdminToken();
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        return cachedToken != null && Instant.now().isBefore(tokenExpiry);
    }

    private String fetchNewAdminToken() {
        log.info("Fetching admin token from Keycloak");
        String tokenUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        params.add("client_id", properties.getAdminClientId());
        params.add("client_secret", properties.getAdminClientSecret());

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(params, headers);

        try {
            log.debug("Calling Keycloak token endpoint for admin client credentials");
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(tokenUrl, entity, TokenResponse.class);

            TokenResponse body = response.getBody();
            if (body == null || body.getAccessToken() == null) {
                log.error("Keycloak admin token response did not contain an access token");
                throw new KeycloakIntegrationException("Failed to obtain admin token: empty response body");
            }
            cachedToken = body.getAccessToken();

            int expiresIn = body.getExpiresIn() != null ? body.getExpiresIn() : 60;
            tokenExpiry = Instant.now().plusSeconds(expiresIn - EXPIRY_BUFFER_SECONDS);

            log.info("Admin token refreshed successfully");
            log.debug("Admin token expiry updated with buffer, expires in {} seconds", expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("Failed to obtain admin token from Keycloak", e);
            throw new KeycloakIntegrationException(
                    "Failed to obtain admin token: " + e.getMessage(), e
            );
        }
    }
}

