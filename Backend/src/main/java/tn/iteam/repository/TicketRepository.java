package tn.iteam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import tn.iteam.enums.TicketStatus;
import tn.iteam.domain.Ticket;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    @Override
    @EntityGraph(attributePaths = {
            "createdBy", "createdBy.role",
            "assignedTo", "assignedTo.role",
            "validatedBy", "validatedBy.role"
    })
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {
            "createdBy", "createdBy.role",
            "assignedTo", "assignedTo.role",
            "validatedBy", "validatedBy.role",
            "interventions", "interventions.performedBy", "interventions.performedBy.role"
    })
    Optional<Ticket> findByMonitoringSourceAndExternalProblemId(String monitoringSource, String externalProblemId);

    @EntityGraph(attributePaths = {
            "createdBy", "createdBy.role",
            "assignedTo", "assignedTo.role",
            "validatedBy", "validatedBy.role",
            "interventions", "interventions.performedBy", "interventions.performedBy.role"
    })
    Optional<Ticket> findFirstByMonitoringSourceAndResourceRefAndTitleAndArchivedFalseOrderByCreationDateDesc(
            String monitoringSource,
            String resourceRef,
            String title
    );

    @Override
    @EntityGraph(attributePaths = {
            "createdBy", "createdBy.role",
            "assignedTo", "assignedTo.role",
            "validatedBy", "validatedBy.role",
            "interventions", "interventions.performedBy", "interventions.performedBy.role"
    })
    Optional<Ticket> findById(Long id);
}
