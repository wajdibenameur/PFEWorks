package tn.iteam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
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