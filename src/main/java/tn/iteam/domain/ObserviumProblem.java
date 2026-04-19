package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "observium_problem")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObserviumProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String problemId;

    private String hostId;
    private String device;
    private String description;
    private String severity;
    private Boolean active;
    private String source = "Observium";
    private Long eventId;
}
