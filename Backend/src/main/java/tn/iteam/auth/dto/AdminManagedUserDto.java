package tn.iteam.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminManagedUserDto {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String position;
    private boolean enabled;
    private boolean connected;
    private List<String> roles;
    private String address;
    private String city;
    private String zipCode;
}

