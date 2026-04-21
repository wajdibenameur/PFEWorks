package tn.iteam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.iteam.Enums.TicketStatus;
import tn.iteam.domain.Ticket;
import tn.iteam.dto.ZabbixProblemDTO;

import java.util.Optional;

public interface TicketService {

    Ticket createFromProblem(ZabbixProblemDTO problem, Long creatorId);

    Ticket createManual(Ticket ticket, Long creatorId);

    Ticket assign(Long ticketId, Long userId);

    Ticket updateStatus(Long ticketId, TicketStatus status);

    Ticket validate(Long ticketId, Long adminId);

    Ticket reject(Long ticketId, Long adminId, String reason);

    Ticket addComment(Long ticketId, String comment, Long userId);

    Page<Ticket> getAll(Pageable pageable);

    Page<Ticket> getByStatus(TicketStatus status, Pageable pageable);

    Optional<Ticket> getById(Long id);

    void delete(Long id);
}
