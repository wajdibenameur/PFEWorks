package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    private String comment;
    private LocalDateTime timestamp;
    private String result;

    @ManyToOne
    private User performedBy;

    @ManyToOne
    private Ticket ticket;
}
