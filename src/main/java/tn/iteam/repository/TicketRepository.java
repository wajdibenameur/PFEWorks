package tn.iteam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.Enums.TicketStatus;
import tn.iteam.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByAssignedTo_Id(Long userId, Pageable pageable);

    Page<Ticket> findByArchivedFalse(Pageable pageable);
}