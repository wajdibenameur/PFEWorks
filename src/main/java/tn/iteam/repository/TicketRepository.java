package tn.iteam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.iteam.enums.TicketStatus;
import tn.iteam.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByAssignedTo_Id(Long userId, Pageable pageable);

    Page<Ticket> findByArchivedFalse(Pageable pageable);
}
