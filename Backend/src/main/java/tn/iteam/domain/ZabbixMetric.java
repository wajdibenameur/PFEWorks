package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class ZabbixMetric extends BaseEntity {

    @Column(nullable = false)
    private String hostId;

    @Column(nullable = false)
    private String hostName;

    @Column(nullable = false)
    private String itemId;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private String metricKey;

    @Column(nullable = false)
    private String source = "Zabbix";

    @Column(nullable = false)
    private Integer valueType;

    @Column(nullable = false)
    private String status;

    private String units;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private Long timestamp;

    private String ip;

    private Integer port;
}
