package tn.iteam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.iteam.domain.Intervention;
import tn.iteam.enums.TicketStatus;
import tn.iteam.enums.Priority;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.ZabbixProblemDTO;

import java.util.List;
import java.util.Optional;

public interface TicketService {

    Ticket createFromProblem(ZabbixProblemDTO problem, Long creatorId);

    Ticket createManual(Ticket ticket, Long creatorId);

    Ticket assign(Long ticketId, Long userId);

    Ticket updateStatus(Long ticketId, TicketStatus status, String resolution);

    Ticket validate(Long ticketId, Long adminId);

    Ticket reject(Long ticketId, Long adminId, String reason);

    Ticket addComment(Long ticketId, String comment, Long userId);

    Intervention addIntervention(Long ticketId, Long userId, String action, String comment, String result);

    Page<Ticket> search(TicketStatus status, Priority priority, String source, Pageable pageable);

    Page<Ticket> getAll(Pageable pageable);

    Page<Ticket> getByStatus(TicketStatus status, Pageable pageable);

    Optional<Ticket> getById(Long id);

    List<User> getAssignableUsers();

    void delete(Long id);
}
