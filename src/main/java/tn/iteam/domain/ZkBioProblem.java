package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "zkbio_problem")
@Setter@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZkBioProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String problemId;
    private String device;      // nom du device ou utilisateur
    private String description;
    private Boolean active;
    private String source = "ZKBIO";
    private Long eventId;       // optionnel
}