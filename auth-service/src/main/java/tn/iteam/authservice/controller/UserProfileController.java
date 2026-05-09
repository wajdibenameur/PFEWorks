package tn.iteam.authservice.controller;

import tn.iteam.authservice.dto.UserProfileDTO;
import tn.iteam.authservice.dto.UpdateProfileRequest;
import tn.iteam.authservice.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/profile")
public class UserProfileController {

  @Autowired private UserProfileService userProfileService;

  @GetMapping
  public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
    String username = authentication.getName();
    UserProfileDTO profile = userProfileService.getUserProfile(username);
    return ResponseEntity.ok(profile);
  }

  @PutMapping
  public ResponseEntity<UserProfileDTO> updateProfile(
      Authentication authentication, @RequestBody UpdateProfileRequest request) {
    String username = authentication.getName();
    UserProfileDTO updatedProfile = userProfileService.updateProfile(username, request);
    return ResponseEntity.ok(updatedProfile);
  }

  @PostMapping("/avatar")
  public ResponseEntity<?> uploadAvatar(
      Authentication authentication, @RequestParam("file") MultipartFile file) {
    String username = authentication.getName();
    String avatarUrl = userProfileService.uploadAvatar(username, file);
    return ResponseEntity.ok(new AvatarResponse(avatarUrl));
  }

  static class AvatarResponse {
    public String url;

    AvatarResponse(String url) {
      this.url = url;
    }
  }
}
