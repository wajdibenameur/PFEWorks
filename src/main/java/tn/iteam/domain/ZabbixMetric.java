package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zabbix_metric",
        indexes = {
                @Index(columnList = "hostId"),
                @Index(columnList = "itemId"),
                @Index(columnList = "timestamp")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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