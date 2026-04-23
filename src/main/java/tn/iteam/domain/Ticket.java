package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;
import tn.iteam.enums.TicketStatus;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

import tn.iteam.enums.Priority;
@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends BaseEntity {

    @Column(nullable = false)
    private String title;

    private Long hostId;

    @Column(length = 4000)
    private String description;

    private LocalDateTime creationDate;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Boolean externalProblem; // Zabbix ou manuel

    private String monitoringSource;

    private String externalProblemId;

    private String resourceRef;

    @Column(length = 4000)
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

    @Builder.Default
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp DESC")
    private List<Intervention> interventions = new ArrayList<>();


}
