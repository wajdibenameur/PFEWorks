package tn.iteam.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ServiceStatusDTO {

    private String source;
    private String name;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
    private LocalDateTime lastCheck;
}

