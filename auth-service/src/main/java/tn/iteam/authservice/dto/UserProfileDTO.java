package tn.iteam.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
  private String username;
  private String email;
  private String firstName;
  private String lastName;
  private String address;
  private String city;
  private String zipCode;
  private String phone;
  private String avatar;
  private String[] roles;
  private String createdAt;
  private String updatedAt;
}
