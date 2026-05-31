package tn.iteam.dto;

import lombok.Data;

@Data
public class SyncLocalUserRequest {
    private String username;
    private String email;
    private String role;
}
