package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "zabbix_old_problem",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_zabbix_old_problem_event_id", columnNames = {"event_id"})
        }
)
public class ZabbixOldProblem extends BaseEntity {

    @Column(name = "problem_id", nullable = false)
    private String problemId;

    @Column(name = "host_id", nullable = false)
    private Long hostId;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = Boolean.FALSE;

    @Column(name = "ip")
    private String ip;

    @Column(name = "port")
    private Integer port;

    @Builder.Default
    @Column(name = "source", nullable = false)
    private String source = "Zabbix";

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "started_at", nullable = false)
    private Long startedAt;

    @Column(name = "resolved_at")
    private Long resolvedAt;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "UNKNOWN";
}
