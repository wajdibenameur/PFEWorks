package tn.iteam.authservice.service;

import feign.FeignException;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tn.iteam.authservice.client.KeycloakAdminClient;
import tn.iteam.authservice.client.KeycloakTokenClient;
import tn.iteam.authservice.config.KeycloakProperties;
import tn.iteam.authservice.dto.*;
import tn.iteam.authservice.exception.AuthenticationException;
import tn.iteam.authservice.exception.KeycloakIntegrationException;
import tn.iteam.authservice.exception.UserAlreadyExistsException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;

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
        String tokenUrl = properties.getBaseUrl()
                + "/realms/" + properties.getRealm()
                + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("username", request.getUsername());
        params.add("password", request.getPassword());

        HttpEntity<MultiValueMap<String, String>> entity =
                new HttpEntity<>(params, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    tokenUrl,
                    entity,
                    TokenResponse.class
            );

            return response.getBody();

        } catch (HttpClientErrorException e) {
            throw new AuthenticationException("Invalid username or password");
        } catch (Exception e) {
            throw new KeycloakIntegrationException("Login failed: " + e.getMessage(), e);
        }
    }
    /**
     * Refreshes an access token using a refresh token.
     */
    public TokenResponse refresh(RefreshRequest request) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", properties.getClientId());
        params.add("client_secret", properties.getClientSecret());
        params.add("refresh_token", request.getRefreshToken());

        try {
            return tokenClient.obtainToken(
                    properties.getRealm(),
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    params
            );
        } catch (FeignException.Unauthorized | FeignException.BadRequest e) {
            throw new AuthenticationException("Refresh token is invalid or expired");
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Token refresh failed: " + e.getMessage(), e);
        }
    }

    /**
     * Registers a new user in Keycloak and sets their password.
     * Throws {@link UserAlreadyExistsException} if the username already exists.
     */
    public RegisterResponse register(RegisterRequest request) {
        String adminToken = "Bearer " + adminTokenService.getAdminToken();

        // Check for existing user
        List<KeycloakUserRepresentation> existing = adminClient.findUserByUsername(
                properties.getRealm(), adminToken, request.getUsername(), true);
        if (!existing.isEmpty()) {
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
            createResponse = adminClient.createUser(properties.getRealm(), adminToken, newUser);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.CONFLICT.value()) {
                throw new UserAlreadyExistsException("User already exists");
            }
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
            adminClient.resetPassword(properties.getRealm(), userId, adminToken, credential);
        } catch (FeignException e) {
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

}
