package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "zabbix_problem")
public class ZabbixProblem extends BaseEntity {

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
    @Builder.Default
    private Boolean active = Boolean.FALSE;

    private String ip;

    private Integer port;

    @Builder.Default
    @Column(nullable = false)
    private String source = "Zabbix";

    @Column(nullable = false)
    private Long eventId;

    @Column(name = "started_at", nullable = false)
    private Long startedAt;

    @Column(name = "resolved_at")
    private Long resolvedAt;

    @Column(nullable = false)
    @Builder.Default
    private String status = "UNKNOWN";
}
