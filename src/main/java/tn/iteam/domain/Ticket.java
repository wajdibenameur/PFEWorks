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
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @ManyToOne
    private User createdBy;

    @ManyToOne
    private User assignedTo;

    @ManyToOne
    private User validatedBy;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    private List<Intervention> interventions;


}