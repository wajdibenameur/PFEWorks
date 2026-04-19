package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "zabbix_metric",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_zabbix_metric_host_item_ts", columnNames = {"hostId", "itemId", "timestamp"})
        }
)
public class ZabbixMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String hostId;
    @Column(nullable = false)
    private String hostName;
    @Column(nullable = false)
    private String itemId;
    @Column(nullable = false)
    private String metricKey;
    @Column(nullable = false)
    private Double value;
    @Column(nullable = false)
    private Long timestamp;
    private String ip;
    private Integer port;
}
