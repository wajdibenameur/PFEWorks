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
                @UniqueConstraint(name = "uk_zabbix_metric_host_item", columnNames = {"hostId", "itemId"})
        }
)
public class ZabbixMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String hostId;
    private String hostName;
    private String itemId;
    private String metricKey;
    private Double value;
    private Long timestamp;
    private String ip;
    private Integer port;
}