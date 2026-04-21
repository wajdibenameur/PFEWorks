package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "service_status",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "name", "ip"}))
public class ServiceStatus extends BaseEntity {

    private String source;       // ZABBIX | OBSERVIUM | ZKBIO | CAMERA
    private String name;         // Hostname / Camera name
    private String ip;
    private Integer port;
    private String protocol;     // HTTP | RTSP | TCP
    private String status;       // UP | DOWN
    private String category;     // SERVER | CAMERA | ACCESS_CONTROL

    private LocalDateTime lastCheck;
}
