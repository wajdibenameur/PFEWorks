package tn.iteam.authservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminRoleDto {

    private String name;
    private String label;
    private String description;
}
