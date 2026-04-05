package tn.iteam.domain;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZabbixProblem {

    private String problemId;
    private String host;
    private String description;
    private String severity;
    private Boolean active;
    private String ip;
    private Integer port;
    private String source = "Zabbix";
    private Long eventId;
}
