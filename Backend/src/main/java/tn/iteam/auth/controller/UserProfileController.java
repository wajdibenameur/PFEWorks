package tn.iteam.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.iteam.auth.dto.UserProfileDTO;
import tn.iteam.auth.dto.UpdateProfileRequest;
import tn.iteam.auth.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/profile")
public class UserProfileController {

  private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

  private final UserProfileService userProfileService;

  public UserProfileController(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @GetMapping
  public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
    String username = extractUsername(authentication);
    log.info("User profile requested for username: {}", username);

    UserProfileDTO profile = userProfileService.getUserProfile(username);

    log.info("User profile returned successfully for username: {}", username);
    return ResponseEntity.ok(profile);
  }

  @PutMapping
  public ResponseEntity<UserProfileDTO> updateProfile(
          Authentication authentication, @RequestBody UpdateProfileRequest request) {
    String username = extractUsername(authentication);
    log.info("Profile update request received for username: {}", username);

    UserProfileDTO updatedProfile = userProfileService.updateProfile(username, request);

    log.info("User profile updated successfully for username: {}", username);
    return ResponseEntity.ok(updatedProfile);
  }

  @PostMapping("/avatar")
  public ResponseEntity<?> uploadAvatar(
          Authentication authentication, @RequestParam("file") MultipartFile file) {
    String username = extractUsername(authentication);
    log.info("Avatar upload request received for username: {}", username);

    String avatarUrl = userProfileService.uploadAvatar(username, file);

    log.info("Avatar uploaded successfully for username: {}", username);
    return ResponseEntity.ok(new AvatarResponse(avatarUrl));
  }

  private String extractUsername(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      String username = jwtAuth.getToken().getClaimAsString("preferred_username");

      if (username != null && !username.isBlank()) {
        return username;
      }
    }

    return authentication.getName();
  }

  static class AvatarResponse {
    public String url;

    AvatarResponse(String url) {
      this.url = url;
    }
  }
}
