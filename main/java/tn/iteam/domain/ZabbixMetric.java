package tn.iteam.domain;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZabbixMetric {

    private String hostId;
    private String hostName;
    private String itemId;
    private String metricKey;
    private Double value;
    private Long timestamp;
    private String ip;
    private Integer port;
}