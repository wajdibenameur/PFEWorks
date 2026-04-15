package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ZabbixProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;          // clé technique (DB)
    private String problemId;
    private Long hostId;
    private String host;
    private String description;
    private String severity;
    private Boolean active;
    private String ip;
    private Integer port;
    private String source = "Zabbix";
    private Long eventId;

    @Column(name = "started_at")
    private Long startedAt;

    @Column(name = "resolved_at")
    private Long resolvedAt;
    private String status;
}
