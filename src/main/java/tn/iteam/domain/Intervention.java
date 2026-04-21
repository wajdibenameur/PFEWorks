package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Intervention extends BaseEntity {

    private String action;
    private String comment;
    private LocalDateTime timestamp;
    private String result;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    private User performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
}
