package tn.iteam.auth.service;

import feign.FeignException;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tn.iteam.auth.client.KeycloakAdminClient;
import tn.iteam.auth.client.KeycloakTokenClient;
import tn.iteam.auth.config.KeycloakProperties;
import tn.iteam.auth.dto.*;
import tn.iteam.auth.exception.AuthenticationException;
import tn.iteam.auth.exception.KeycloakIntegrationException;
import tn.iteam.auth.exception.UserAlreadyExistsException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakTokenClient tokenClient;
    private final KeycloakAdminClient adminClient;
    private final KeycloakProperties properties;
    private final AdminTokenService adminTokenService;
    private final RestTemplate restTemplate;

    public AuthService(
            KeycloakTokenClient tokenClient,
            KeycloakAdminClient adminClient,
            KeycloakProperties properties,
            AdminTokenService adminTokenService,
            RestTemplate restTemplate) {

        this.tokenClient = tokenClient;
        this.adminClient = adminClient;
        this.properties = properties;
        this.adminTokenService = adminTokenService;
        this.restTemplate = restTemplate;
    }

    /**
     * Authenticates a user via Keycloak password grant and returns tokens.
     */
    public TokenResponse login(LoginRequest request) {
        log.info("AuthService login started for user: {}", request.getUsername());
        String tokenUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";
        final String grantType = "password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", grantType);
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("username", request.getUsername());
        params.add("password", request.getPassword());

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(params, headers);

        try {
            log.info(
                    "KEYCLOAK LOGIN ATTEMPT endpoint={} realm={} clientId={} grantType={} username={}",
                    tokenUrl,
                    properties.getRealm(),
                    properties.getClientId(),
                    grantType,
                    request.getUsername()
            );
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl,
                    entity,
                    TokenResponse.class
            );
            log.info("AuthService login succeeded for user: {}", request.getUsername());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            String responseBody = safeSingleLine(e.getResponseBodyAsString());
            String keycloakError = classifyKeycloakLoginError(responseBody);
            log.warn(
                    "KEYCLOAK LOGIN FAILURE endpoint={} realm={} clientId={} grantType={} username={} status={} keycloakError={} body={}",
                    tokenUrl,
                    properties.getRealm(),
                    properties.getClientId(),
                    grantType,
                    request.getUsername(),
                    e.getStatusCode().value(),
                    keycloakError,
                    responseBody
            );
            throw new AuthenticationException("Invalid username or password");
        } catch (Exception e) {
            log.error("AuthService login integration failure for user: {}", request.getUsername(), e);
            throw new KeycloakIntegrationException("Login failed: " + e.getMessage(), e);
        }
    }
    /**
     * Refreshes an access token using a refresh token.
     */
    public TokenResponse refresh(String refreshToken) {
        log.info("AuthService refresh started");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationException("Refresh token is required");
        }

        String tokenUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

        try {
            log.debug("Calling Keycloak token endpoint for refresh grant");
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl,
                    entity,
                    TokenResponse.class
            );
            log.info("AuthService refresh completed successfully");
            return response.getBody();
        } catch (HttpClientErrorException e) {
            String keycloakBody = safeSingleLine(e.getResponseBodyAsString());
            String keycloakError = classifyKeycloakRefreshError(keycloakBody);
            log.warn(
                    "KEYCLOAK REFRESH FAILURE realm={} clientId={} status={} keycloakError={} body={}",
                    properties.getRealm(),
                    properties.getClientId(),
                    e.getStatusCode().value(),
                    keycloakError,
                    keycloakBody
            );
            throw new AuthenticationException("Refresh token is invalid or expired");
        } catch (Exception e) {
            log.error("Refresh token flow failed due to Keycloak error", e);
            throw new KeycloakIntegrationException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    public String buildAuthorizationUrl(String state) {
        String authorizeUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/auth";

        return authorizeUrl
                + "?response_type=code"
                + "&client_id=" + urlEncode(properties.getClientId())
                + "&redirect_uri=" + urlEncode(properties.getRedirectUri())
                + "&scope=" + urlEncode("openid profile email")
                + "&state=" + urlEncode(state);
    }

    public TokenResponse exchangeAuthorizationCode(String code) {
        String tokenUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("code", code);
        params.add("redirect_uri", properties.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        try {
            log.info("KEYCLOAK CODE EXCHANGE ATTEMPT realm={} clientId={} redirectUri={}", properties.getRealm(), properties.getClientId(), properties.getRedirectUri());
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(tokenUrl, entity, TokenResponse.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            String responseBody = safeSingleLine(e.getResponseBodyAsString());
            log.warn("KEYCLOAK CODE EXCHANGE FAILURE status={} body={}", e.getStatusCode().value(), responseBody);
            throw new AuthenticationException("Authorization code exchange failed");
        } catch (Exception e) {
            throw new KeycloakIntegrationException("Authorization code exchange failed: " + e.getMessage(), e);
        }
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String revokeUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/revoke";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("token", refreshToken);
        params.add("token_type_hint", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        try {
            restTemplate.postForEntity(revokeUrl, entity, String.class);
            log.info("AuthService logout revoke completed");
        } catch (HttpClientErrorException e) {
            log.warn("AuthService logout revoke returned status={}", e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("AuthService logout revoke failed: {}", e.getMessage());
        }
    }

    /**
     * Registers a new user in Keycloak and sets their password.
     * Throws {@link UserAlreadyExistsException} if the username already exists.
     */
    public RegisterResponse register(RegisterRequest request) {
        log.info("AuthService register started for user: {}", request.getUsername());
        String adminToken = "Bearer " + adminTokenService.getAdminToken();

        // Check for existing user
        log.debug("Checking existing Keycloak user for username: {}", request.getUsername());
        List<KeycloakUserRepresentation> existing = adminClient.findUserByUsername(
                properties.getRealm(), adminToken, request.getUsername(), true);
        if (!existing.isEmpty()) {
            log.warn("Registration rejected because user already exists: {}", request.getUsername());
            throw new UserAlreadyExistsException(
                    "User '" + request.getUsername() + "' already exists");
        }

        // Build user representation (no credentials embedded - set separately)
        KeycloakUserRepresentation newUser = KeycloakUserRepresentation.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(true)
                .emailVerified(true)
                .build();

        // Create user
        Response createResponse;
        try {
            log.debug("Creating Keycloak user for username: {}", request.getUsername());
            createResponse = adminClient.createUser(properties.getRealm(), adminToken, newUser);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.CONFLICT.value()) {
                log.warn("Keycloak reported conflict during registration for user: {}", request.getUsername());
                throw new UserAlreadyExistsException("User already exists");
            }
            log.error("Failed to create Keycloak user for username: {}", request.getUsername(), e);
            throw new KeycloakIntegrationException("Failed to create user: " + e.getMessage(), e);
        }

        // Extract user ID from Location header
        String userId = extractUserIdFromLocation(createResponse);

        // Set password
        KeycloakCredentialRepresentation credential = KeycloakCredentialRepresentation.builder()
                .type("password")
                .value(request.getPassword())
                .temporary(false)
                .build();

        try {
            log.debug("Resetting password in Keycloak for newly created userId: {}", userId);
            adminClient.resetPassword(properties.getRealm(), userId, adminToken, credential);
        } catch (FeignException e) {
            log.error("Failed to set password in Keycloak for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to set user password: " + e.getMessage(), e);
        }

        log.info("Registered new user '{}' with Keycloak ID '{}'", request.getUsername(), userId);

        return RegisterResponse.builder()
                .message("User registered successfully")
                .keycloakUserId(userId)
                .build();
    }

    private String extractUserIdFromLocation(Response response) {
        String location = response.headers().getOrDefault("Location",
                response.headers().get("location") != null
                        ? response.headers().get("location")
                        : Collections.emptyList()
        ).stream().findFirst()
                .orElseThrow(() -> new KeycloakIntegrationException(
                        "User created but Location header missing in Keycloak response"));
        // Location is like: http://keycloak/admin/realms/{realm}/users/{uuid}
        return location.substring(location.lastIndexOf('/') + 1);

    }

    private String safeSingleLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String classifyKeycloakLoginError(String keycloakBody) {
        String body = keycloakBody == null ? "" : keycloakBody.toLowerCase(Locale.ROOT);
        if (body.contains("unauthorized_client")) {
            return "unauthorized_client (likely Direct Access Grants OFF)";
        }
        if (body.contains("invalid_client")) {
            return "invalid_client (client credentials/config issue)";
        }
        if (body.contains("invalid_grant")) {
            return "invalid_grant (invalid username/password)";
        }
        if (body.contains("invalid_request")) {
            return "invalid_request (missing/invalid request parameters)";
        }
        return "unknown";
    }

    private String classifyKeycloakRefreshError(String keycloakBody) {
        String body = keycloakBody == null ? "" : keycloakBody.toLowerCase(Locale.ROOT);
        if (body.contains("invalid_grant")) {
            return "invalid_grant (refresh token invalid/expired/revoked/session invalid)";
        }
        if (body.contains("invalid_client")) {
            return "invalid_client (client credentials/config issue)";
        }
        if (body.contains("unauthorized_client")) {
            return "unauthorized_client (client not allowed for this flow)";
        }
        if (body.contains("invalid_request")) {
            return "invalid_request (malformed or missing parameters)";
        }
        return "unknown";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}

