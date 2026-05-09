package tn.iteam.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
  private String firstName;
  private String lastName;
  private String email;
  private String address;
  private String city;
  private String zipCode;
  private String phone;
  private String avatar;
}
