package tn.iteam.authservice.service;

import tn.iteam.authservice.dto.UpdateProfileRequest;
import tn.iteam.authservice.dto.UserProfileDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tn.iteam.authservice.client.KeycloakAdminClient;
import tn.iteam.authservice.config.KeycloakProperties;
import tn.iteam.authservice.dto.KeycloakRoleRepresentation;
import tn.iteam.authservice.dto.KeycloakUserRepresentation;
import tn.iteam.authservice.exception.KeycloakIntegrationException;

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

  private static final String ADDRESS = "address";
  private static final String CITY = "city";
  private static final String ZIP_CODE = "zipCode";
  private static final String PHONE = "phone";
  private static final String AVATAR = "avatar";

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
    String adminToken = bearerToken();
    KeycloakUserRepresentation user = findUserByUsername(username, adminToken);
    return mapToProfile(user, readRoles(user.getId(), adminToken));
  }

  public UserProfileDTO updateProfile(String username, UpdateProfileRequest request) {
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
      adminClient.updateUser(properties.getRealm(), existing.getId(), adminToken, updated);
    } catch (Exception ex) {
      throw new KeycloakIntegrationException("Failed to update user profile: " + ex.getMessage(), ex);
    }

    KeycloakUserRepresentation refreshed = fetchUserById(existing.getId(), adminToken);
    return mapToProfile(refreshed, readRoles(refreshed.getId(), adminToken));
  }

  public String uploadAvatar(String username, MultipartFile file) {
    if (file.isEmpty()) {
      throw new RuntimeException("File is empty");
    }

    try {
      String filename = username + "_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
      Path uploadPath = Paths.get(uploadDir);
      Files.createDirectories(uploadPath);

      Path filePath = uploadPath.resolve(filename);
      Files.copy(file.getInputStream(), filePath);

      return "/uploads/avatars/" + filename;
    } catch (IOException e) {
      throw new RuntimeException("Failed to upload file", e);
    }
  }

  private KeycloakUserRepresentation findUserByUsername(String username, String adminToken) {
    List<KeycloakUserRepresentation> users =
        adminClient.findUserByUsername(properties.getRealm(), adminToken, username, true);

    return users.stream()
        .findFirst()
        .orElseThrow(() -> new KeycloakIntegrationException("User '" + username + "' not found in Keycloak"));
  }

  private KeycloakUserRepresentation fetchUserById(String userId, String adminToken) {
    try {
      return adminClient.getUserById(properties.getRealm(), userId, adminToken);
    } catch (Exception ex) {
      throw new KeycloakIntegrationException("Failed to reload user profile: " + ex.getMessage(), ex);
    }
  }

  private String[] readRoles(String userId, String adminToken) {
    try {
      return adminClient.getUserRealmRoles(properties.getRealm(), userId, adminToken).stream()
          .map(KeycloakRoleRepresentation::getName)
          .filter(StringUtils::hasText)
          .map(String::trim)
          .map(String::toUpperCase)
          .toArray(String[]::new);
    } catch (Exception ex) {
      throw new KeycloakIntegrationException("Failed to load user roles: " + ex.getMessage(), ex);
    }
  }

  private UserProfileDTO mapToProfile(KeycloakUserRepresentation user, String[] roles) {
    Map<String, List<String>> attributes = user.getAttributes() == null ? Map.of() : user.getAttributes();

    return UserProfileDTO.builder()
        .username(defaultString(user.getUsername()))
        .email(defaultString(user.getEmail()))
        .firstName(defaultString(user.getFirstName()))
        .lastName(defaultString(user.getLastName()))
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
