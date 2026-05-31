package tn.iteam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.iteam.domain.Intervention;
import tn.iteam.enums.TicketStatus;
import tn.iteam.enums.Priority;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.dto.ZkBioProblemDTO;

import java.util.List;
import java.util.Optional;

public interface TicketService {

    Ticket createFromProblem(ZabbixProblemDTO problem);

    Ticket createFromProblem(ObserviumProblemDTO problem);

    Ticket createFromProblem(ZkBioProblemDTO problem);

    Ticket createManual(Ticket ticket);

    Ticket assign(Long ticketId, Long userId);

    Ticket updateStatus(Long ticketId, TicketStatus status, String resolution);

    Ticket validate(Long ticketId);

    Ticket reject(Long ticketId, String reason);

    Ticket addComment(Long ticketId, String comment);

    Intervention addIntervention(Long ticketId, String action, String comment, String result);

    Page<Ticket> search(TicketStatus status, Priority priority, String source, String archived, Pageable pageable);

    Page<Ticket> getAll(Pageable pageable);

    Page<Ticket> getByStatus(TicketStatus status, Pageable pageable);

    Optional<Ticket> getById(Long id);

    List<User> getAssignableUsers();

    Ticket archive(Long ticketId);

    Ticket unarchive(Long ticketId);

    void delete(Long id);
}
