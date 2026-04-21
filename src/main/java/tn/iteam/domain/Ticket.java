package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.iteam.Enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

import tn.iteam.Enums.Priority;
@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends BaseEntity {

    private String title;
    private Long hostId;
    private String description;

    private LocalDateTime creationDate;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Boolean externalProblem; // Zabbix ou manuel

    private String resolution;
    @Builder.Default
    private Boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private User validatedBy;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    private List<Intervention> interventions;


}
