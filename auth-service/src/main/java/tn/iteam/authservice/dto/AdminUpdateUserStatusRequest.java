package tn.iteam.authservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateUserStatusRequest {

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;
}
