package tn.iteam.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceStatusDTO {

    private String source;
    private String hostId;
    private String name;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
    private LocalDateTime lastCheck;
}
