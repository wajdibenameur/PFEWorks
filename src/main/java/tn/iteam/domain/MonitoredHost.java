package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "monitored_host")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredHost {

    @Id
    private String hostId;

    private String name;

    private String ip;

    private Integer port;

    private String source;
}