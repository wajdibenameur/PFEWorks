package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "monitored_host",
        uniqueConstraints = @UniqueConstraint(name = "uk_monitored_host_hostid_source", columnNames = {"hostId", "source"})
)
public class MonitoredHost extends BaseEntity {
    @Column(nullable = false)
    private String hostId;
    @Column(nullable = false)
    private String name;
    private String ip;
    private Integer port;
    @Column(nullable = false)
    private String source;
}
