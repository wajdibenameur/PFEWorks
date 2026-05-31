package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "zkbio_problem")
@Setter@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZkBioProblem extends BaseEntity {

    @Column(nullable = false)
    private String problemId;
    private String device;      // nom du device ou utilisateur
    private String description;
    @Builder.Default
    private Boolean active = Boolean.FALSE;
    private String status;
    @Column(name = "started_at")
    private Long startedAt;
    @Column(name = "resolved_at")
    private Long resolvedAt;
    @Builder.Default
    private String source = "ZKBIO";
    private Long eventId;       // optionnel
}
