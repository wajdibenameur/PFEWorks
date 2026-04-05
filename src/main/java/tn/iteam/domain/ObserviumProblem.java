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
    private String problemId;   // identifiant unique Observium
    private String device;      // nom du device ou host
    private String description; // description du problème
    private String severity;    // si disponible
    private Boolean active;     // true si actif
    private String source = "Observium"; // référence
    private Long eventId;       // si l'API fournit un identifiant d'événement
}