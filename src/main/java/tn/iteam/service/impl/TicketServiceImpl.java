package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.Enums.Priority;
import tn.iteam.Enums.TicketStatus;
import tn.iteam.Enums.NotificationType;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.repository.TicketRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.service.TicketService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate ws;

    // ================= CREATE FROM ZABBIX =================
    @Override
    public Ticket createFromProblem(ZabbixProblemDTO problem, User creator) {

        Ticket ticket = Ticket.builder()
                .title(problem.getDescription())
                .description(problem.getDescription())
                .hostId(problem.getEventId()) // ⚠️ à corriger plus tard
                .priority(mapSeverity(problem.getSeverity()))
                .status(TicketStatus.OPEN)
                .externalProblem(true)
                .creationDate(LocalDateTime.now())
                .createdBy(creator)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        notify("NEW_TICKET", saved);

        return saved;
    }

    // ================= CREATE MANUAL =================
    @Override
    public Ticket createManual(Ticket ticket, User creator) {
        ticket.setCreationDate(LocalDateTime.now());
        ticket.setCreatedBy(creator);
        ticket.setStatus(TicketStatus.OPEN);

        Ticket saved = ticketRepository.save(ticket);
        notify("NEW_TICKET", saved);

        return saved;
    }

    // ================= ASSIGN =================
    @Override
    public Ticket assign(Long ticketId, Long userId) {

        Ticket ticket = getTicketOrThrow(ticketId);
        User user = getUserOrThrow(userId);

        ticket.setAssignedTo(user);
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        Ticket saved = ticketRepository.save(ticket);
        notify("ASSIGNED", saved);

        return saved;
    }

    // ================= STATUS UPDATE =================
    @Override
    public Ticket updateStatus(Long ticketId, TicketStatus status) {

        Ticket ticket = getTicketOrThrow(ticketId);
        ticket.setStatus(status);

        Ticket saved = ticketRepository.save(ticket);
        notify("STATUS_UPDATED", saved);

        return saved;
    }

    // ================= VALIDATE =================
    @Override
    public Ticket validate(Long ticketId, User admin) {

        Ticket ticket = getTicketOrThrow(ticketId);

        ticket.setStatus(TicketStatus.VALIDATED);
        ticket.setValidatedBy(admin);

        Ticket saved = ticketRepository.save(ticket);
        notify("VALIDATED", saved);

        return saved;
    }

    // ================= REJECT =================
    @Override
    public Ticket reject(Long ticketId, User admin, String reason) {

        Ticket ticket = getTicketOrThrow(ticketId);

        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setResolution(reason);

        Ticket saved = ticketRepository.save(ticket);
        notify("REJECTED", saved);

        return saved;
    }

    // ================= COMMENT =================
    @Override
    public Ticket addComment(Long ticketId, String comment, User user) {

        Ticket ticket = getTicketOrThrow(ticketId);

        ticket.setResolution(
                (ticket.getResolution() == null ? "" : ticket.getResolution() + "\n")
                        + user.getUsername() + ": " + comment
        );

        Ticket saved = ticketRepository.save(ticket);
        notify("COMMENT_ADDED", saved);

        return saved;
    }

    // ================= GET =================
    @Override
    public Page<Ticket> getAll(Pageable pageable) {
        return ticketRepository.findAll(pageable);
    }

    @Override
    public Page<Ticket> getByStatus(TicketStatus status, Pageable pageable) {
        return ticketRepository.findByStatus(status, pageable);
    }

    @Override
    public Optional<Ticket> getById(Long id) {
        return ticketRepository.findById(id);
    }

    @Override
    public void delete(Long id) {
        ticketRepository.deleteById(id);
    }

    // ================= HELPERS =================
    private Ticket getTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void notify(String type, Ticket ticket) {
        ws.convertAndSend("/topic/tickets", Map.of(
                "type", type,
                "data", ticket
        ));
    }

    private Priority mapSeverity(String severity) {
        return switch (severity) {
            case "5" -> Priority.CRITICAL;
            case "4" -> Priority.HIGH;
            case "3" -> Priority.MEDIUM;
            default -> Priority.LOW;
        };
    }
}