package tn.iteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraDeviceDTO {

    private String source;
    private String name;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
    private LocalDateTime lastScanAt;
    private boolean reachable;
    private boolean persisted;
}
