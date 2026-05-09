package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "observium_problem")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObserviumProblem extends BaseEntity {

    @Column(nullable = false)
    private String problemId;

    private String hostId;
    private String device;
    private String description;
    private String severity;
    private Boolean active;
    private String source = "Observium";
    private Long eventId;
    @Column(name = "started_at")
    private Long startedAt;
    @Column(name = "resolved_at")
    private Long resolvedAt;
}
