package tn.iteam.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.iteam.auth.dto.UpdateProfileRequest;
import tn.iteam.auth.dto.UserProfileDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tn.iteam.auth.client.KeycloakAdminClient;
import tn.iteam.auth.config.KeycloakProperties;
import tn.iteam.auth.dto.KeycloakRoleRepresentation;
import tn.iteam.auth.dto.KeycloakUserRepresentation;
import tn.iteam.auth.exception.KeycloakIntegrationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserProfileService {

  private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

  private static final String ADDRESS = "address";
  private static final String CITY = "city";
  private static final String ZIP_CODE = "zipCode";
  private static final String PHONE = "phone";
  private static final String AVATAR = "avatar";
  private static final String POSITION = "position";

  private final KeycloakAdminClient adminClient;
  private final KeycloakProperties properties;
  private final AdminTokenService adminTokenService;

  @Value("${file.upload-dir:uploads/avatars}")
  private String uploadDir;

  public UserProfileService(
      KeycloakAdminClient adminClient,
      KeycloakProperties properties,
      AdminTokenService adminTokenService) {
    this.adminClient = adminClient;
    this.properties = properties;
    this.adminTokenService = adminTokenService;
  }

  public UserProfileDTO getUserProfile(String username) {
    log.info("Fetching profile for user: {}", username);
    String adminToken = bearerToken();
    KeycloakUserRepresentation user = findUserByUsername(username, adminToken);
    log.info("Profile fetched successfully for user: {}", username);
    return mapToProfile(user, readRoles(user.getId(), adminToken));
  }

  public UserProfileDTO updateProfile(String username, UpdateProfileRequest request) {
    log.info("Updating profile for user: {}", username);
    String adminToken = bearerToken();
    KeycloakUserRepresentation existing = findUserByUsername(username, adminToken);

    Map<String, List<String>> mergedAttributes = new HashMap<>();
    if (existing.getAttributes() != null) {
      mergedAttributes.putAll(existing.getAttributes());
    }

    putAttribute(mergedAttributes, ADDRESS, request.getAddress());
    putAttribute(mergedAttributes, CITY, request.getCity());
    putAttribute(mergedAttributes, ZIP_CODE, request.getZipCode());
    putAttribute(mergedAttributes, PHONE, request.getPhone());
    putAttribute(mergedAttributes, AVATAR, request.getAvatar());
    putAttribute(mergedAttributes, POSITION, request.getPosition());

    KeycloakUserRepresentation updated = KeycloakUserRepresentation.builder()
        .id(existing.getId())
        .username(existing.getUsername())
        .email(request.getEmail())
        .firstName(request.getFirstName())
        .lastName(request.getLastName())
        .enabled(existing.getEnabled())
        .emailVerified(existing.getEmailVerified())
        .attributes(mergedAttributes)
        .build();

    try {
      log.debug("Calling Keycloak update profile for user: {}", username);
      log.info("SAVING ATTRIBUTES TO KEYCLOAK = {}", mergedAttributes);
      adminClient.updateUser(properties.getRealm(), existing.getId(), adminToken, updated);
    } catch (Exception ex) {
      log.error("Failed to update profile in Keycloak for user: {}", username, ex);
      throw new KeycloakIntegrationException("Failed to update user profile: " + ex.getMessage(), ex);
    }

    KeycloakUserRepresentation refreshed = fetchUserById(existing.getId(), adminToken);
    log.info("Profile updated successfully for user: {}", username);

    return mapToProfile(refreshed, readRoles(refreshed.getId(), adminToken));
  }

  public String uploadAvatar(String username, MultipartFile file) {
    log.info("Uploading avatar for user: {}", username);
    if (file.isEmpty()) {
      log.warn("Avatar upload rejected because file is empty for user: {}", username);
      throw new RuntimeException("File is empty");
    }

    try {
      String filename = username + "_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
      Path uploadPath = Paths.get(uploadDir);
      Files.createDirectories(uploadPath);

      Path filePath = uploadPath.resolve(filename);
      Files.copy(file.getInputStream(), filePath);

      log.info("Avatar uploaded successfully for user: {}", username);
      log.debug("Avatar stored at path: {}", filePath.toAbsolutePath());
      return "/uploads/avatars/" + filename;
    } catch (IOException e) {
      log.error("Failed to upload avatar for user: {}", username, e);
      throw new RuntimeException("Failed to upload file", e);
    }
  }

  private KeycloakUserRepresentation findUserByUsername(String username, String adminToken) {
    log.debug("Finding Keycloak user by username: {}", username);
    List<KeycloakUserRepresentation> users =
        adminClient.findUserByUsername(properties.getRealm(), adminToken, username, true);

    return users.stream()
        .findFirst()
        .orElseThrow(() -> new KeycloakIntegrationException("User '" + username + "' not found in Keycloak"));
  }

  private KeycloakUserRepresentation fetchUserById(String userId, String adminToken) {
    try {
      log.debug("Reloading Keycloak profile by userId: {}", userId);
      return adminClient.getUserById(properties.getRealm(), userId, adminToken);
    } catch (Exception ex) {
      log.error("Failed to reload Keycloak profile for userId: {}", userId, ex);
      throw new KeycloakIntegrationException("Failed to reload user profile: " + ex.getMessage(), ex);
    }
  }

  private String[] readRoles(String userId, String adminToken) {
    try {
      String[] roles = adminClient.getUserRealmRoles(properties.getRealm(), userId, adminToken).stream()
          .map(KeycloakRoleRepresentation::getName)
          .filter(StringUtils::hasText)
          .map(String::trim)
          .map(String::toUpperCase)
          .toArray(String[]::new);
      log.debug("Loaded {} roles for userId: {}", roles.length, userId);
      return roles;
    } catch (Exception ex) {
      log.error("Failed to load user roles for userId: {}", userId, ex);
      throw new KeycloakIntegrationException("Failed to load user roles: " + ex.getMessage(), ex);
    }
  }

  private UserProfileDTO mapToProfile(KeycloakUserRepresentation user, String[] roles) {
    Map<String, List<String>> attributes = user.getAttributes() == null ? Map.of() : user.getAttributes();
    log.info("ATTRIBUTES RECEIVED FROM KEYCLOAK for user {} = {}",
            user.getUsername(),
            attributes);

    return UserProfileDTO.builder()
        .username(defaultString(user.getUsername()))
        .email(defaultString(user.getEmail()))
        .firstName(defaultString(user.getFirstName()))
        .lastName(defaultString(user.getLastName()))
            .position(firstAttribute(attributes, POSITION))
        .address(firstAttribute(attributes, ADDRESS))
        .city(firstAttribute(attributes, CITY))
        .zipCode(firstAttribute(attributes, ZIP_CODE))
        .phone(firstAttribute(attributes, PHONE))
        .avatar(firstAttribute(attributes, AVATAR))
        .roles(roles)
        .build();
  }

  private void putAttribute(Map<String, List<String>> attributes, String key, String value) {
    if (StringUtils.hasText(value)) {
      attributes.put(key, List.of(value.trim()));
    } else {
      attributes.remove(key);
    }
  }

  private String firstAttribute(Map<String, List<String>> attributes, String key) {
    List<String> values = attributes.get(key);
    if (values == null || values.isEmpty()) {
      return "";
    }
    return values.get(0);
  }

  private String defaultString(String value) {
    return value == null ? "" : value;
  }

  private String bearerToken() {
    return "Bearer " + adminTokenService.getAdminToken();
  }
}

