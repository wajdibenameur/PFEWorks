package tn.iteam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 8, message = "Username must be at least 8 characters")
    @Pattern(regexp = "^\\S+$", message = "Username must not contain spaces")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String firstName;
    private String lastName;
    private String phone;
    private String position;
    private String role;
    private Boolean enabled;

    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[^A-Za-z0-9]).+$", message = "Password must include at least one special character")
    private String password;
}

