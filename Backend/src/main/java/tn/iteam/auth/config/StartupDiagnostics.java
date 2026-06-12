package tn.iteam.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Component
public class StartupDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    private final Environment environment;
    private final KeycloakProperties keycloakProperties;
    private final RestTemplate restTemplate;
    private final List<String> allowedOrigins;
    private final boolean degradedDbMode;
    private final boolean keycloakClientConfidential;

    public StartupDiagnostics(
            Environment environment,
            KeycloakProperties keycloakProperties,
            RestTemplate restTemplate,
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins,
            @Value("${app.degraded-db-mode:false}") boolean degradedDbMode,
            @Value("${keycloak.client-confidential:true}") boolean keycloakClientConfidential
    ) {
        this.environment = environment;
        this.keycloakProperties = keycloakProperties;
        this.restTemplate = restTemplate;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split("\\s*,\\s*")).toList();
        this.degradedDbMode = degradedDbMode;
        this.keycloakClientConfidential = keycloakClientConfidential;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Auth-service startup complete");
        log.info("Active profiles: {}", resolveActiveProfiles());
        log.info("Running port: {}", environment.getProperty("local.server.port", environment.getProperty("server.port", "unknown")));
        log.info("CORS origins loaded: {}", allowedOrigins);
        if (degradedDbMode) {
            log.warn("Degraded DB mode is enabled");
        }
        logKeycloakClientSecretStatus();
        logKeycloakConnectivity();
    }

    private void logKeycloakClientSecretStatus() {
        if (keycloakClientConfidential && !StringUtils.hasText(keycloakProperties.getClientSecret())) {
            log.warn("KEYCLOAK CLIENT SECRET IS EMPTY while keycloak.client-confidential=true. Login may fail with invalid_client.");
        }
    }

    private void logKeycloakConnectivity() {
        if (!StringUtils.hasText(keycloakProperties.getBaseUrl()) || !StringUtils.hasText(keycloakProperties.getRealm())) {
            log.warn("Keycloak connectivity status: unavailable because base URL or realm is not configured");
            return;
        }

        String endpoint = keycloakProperties.getBaseUrl()
                + "/realms/" + keycloakProperties.getRealm()
                + "/.well-known/openid-configuration";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(endpoint, String.class);
            log.info("Keycloak connectivity status: reachable with HTTP {}", response.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("Keycloak connectivity status: unreachable ({})", ex.getClass().getSimpleName());
            log.warn("JWT issuer unavailable, application started in degraded auth mode");
            log.debug("Keycloak connectivity check failed", ex);
        }
    }

    private List<String> resolveActiveProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return List.of("default");
        }
        return Arrays.asList(profiles);
    }
}

