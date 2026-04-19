package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "zabbix_problem")
public class ZabbixProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;          // clé technique (DB)
    @Column(nullable = false)
    private String problemId;
    @Column(nullable = false)
    private Long hostId;
    @Column(nullable = false)
    private String host;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String severity;
    @Column(nullable = false)
    private Boolean active;
    private String ip;
    private Integer port;
    @Column(nullable = false)
    private String source = "Zabbix";
    @Column(nullable = false)
    private Long eventId;

    @Column(name = "started_at", nullable = false)
    private Long startedAt;

    @Column(name = "resolved_at")
    private Long resolvedAt;
    @Column(nullable = false)
    private String status;
}
