package tn.iteam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPermissionMutationRequest {

    @NotBlank
    private String permission;
}
