package tn.iteam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraDeviceUpsertRequest {

    @NotBlank(message = "ipAddress is required")
    @Pattern(
            regexp = "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$",
            message = "ipAddress must be a valid IPv4 address"
    )
    private String ipAddress;

    private String name;

    private String site;

    private String type;

    @Min(value = 1, message = "port must be between 1 and 65535")
    @Max(value = 65535, message = "port must be between 1 and 65535")
    private Integer port;

    private Boolean enabled;
}
