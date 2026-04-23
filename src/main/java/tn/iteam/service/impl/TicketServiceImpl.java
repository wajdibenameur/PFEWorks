package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.Intervention;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.enums.Priority;
import tn.iteam.enums.TicketStatus;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.InterventionRepository;
import tn.iteam.repository.TicketRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.service.TicketService;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final InterventionRepository interventionRepository;
    private final SimpMessagingTemplate ws;

    // ================= CREATE FROM ZABBIX =================
    @Override
    public Ticket createFromProblem(ZabbixProblemDTO problem, Long creatorId) {
        User creator = getUserOrThrow(creatorId);

        Ticket ticket = Ticket.builder()
                .title(problem.getDescription())
                .description(problem.getDescription())
                .hostId(parseHostId(problem))
                .priority(mapSeverity(problem.getSeverity()))
                .status(TicketStatus.OPEN)
                .externalProblem(true)
                .monitoringSource(problem.getSource() != null ? problem.getSource() : "ZABBIX")
                .externalProblemId(problem.getProblemId())
                .resourceRef(resolveResourceRef(problem))
                .creationDate(LocalDateTime.now())
                .createdBy(creator)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created monitoring ticket {} from external problem {} for creator {}", saved.getId(), problem.getProblemId(), creator.getUsername());
        notify("NEW_TICKET", saved);

        return saved;
    }

    // ================= CREATE MANUAL =================
    @Override
    public Ticket createManual(Ticket ticket, Long creatorId) {
        User creator = getUserOrThrow(creatorId);
        ticket.setCreationDate(LocalDateTime.now());
        ticket.setCreatedBy(creator);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setInterventions(ticket.getInterventions() == null ? new ArrayList<>() : ticket.getInterventions());

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created manual ticket {} by {}", saved.getId(), creator.getUsername());
        notify("NEW_TICKET", saved);

        return saved;
    }

    // ================= ASSIGN =================
    @Override
    public Ticket assign(Long ticketId, Long userId) {

        Ticket ticket = getTicketOrThrow(ticketId);
        User user = getUserOrThrow(userId);

        ticket.setAssignedTo(user);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, user, "ASSIGNMENT", "Ticket assigned to " + user.getUsername(), "ASSIGNED");
        log.info("Assigned ticket {} to {}", saved.getId(), user.getUsername());
        notify("ASSIGNED", saved);

        return saved;
    }

    // ================= STATUS UPDATE =================
    @Override
    public Ticket updateStatus(Long ticketId, TicketStatus status, String resolution) {

        Ticket ticket = getTicketOrThrow(ticketId);
        ensureTransitionAllowed(ticket.getStatus(), status);
        ticket.setStatus(status);
        if (resolution != null && !resolution.isBlank()) {
            ticket.setResolution(resolution.trim());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Updated ticket {} status from {} to {}", saved.getId(), ticket.getStatus(), status);
        notify("STATUS_UPDATED", saved);

        return saved;
    }

    // ================= VALIDATE =================
    @Override
    public Ticket validate(Long ticketId, Long adminId) {
        User admin = getUserOrThrow(adminId);

        Ticket ticket = getTicketOrThrow(ticketId);

        ensureTransitionAllowed(ticket.getStatus(), TicketStatus.VALIDATED);
        ticket.setStatus(TicketStatus.VALIDATED);
        ticket.setValidatedBy(admin);

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, admin, "VALIDATION", "Ticket validated", "VALIDATED");
        log.info("Validated ticket {} by {}", saved.getId(), admin.getUsername());
        notify("VALIDATED", saved);

        return saved;
    }

    // ================= REJECT =================
    @Override
    public Ticket reject(Long ticketId, Long adminId, String reason) {
        User admin = getUserOrThrow(adminId);

        Ticket ticket = getTicketOrThrow(ticketId);

        ensureTransitionAllowed(ticket.getStatus(), TicketStatus.REJECTED);
        ticket.setStatus(TicketStatus.REJECTED);
        ticket.setResolution(reason);
        ticket.setValidatedBy(admin);

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, admin, "REJECTION", reason, "REJECTED");
        log.info("Rejected ticket {} by {} with reason {}", saved.getId(), admin.getUsername(), reason);
        notify("REJECTED", saved);

        return saved;
    }

    // ================= COMMENT =================
    @Override
    public Ticket addComment(Long ticketId, String comment, Long userId) {
        User user = getUserOrThrow(userId);

        Ticket ticket = getTicketOrThrow(ticketId);

        recordIntervention(ticket, user, "COMMENT", comment, null);
        Ticket saved = ticketRepository.save(ticket);
        log.info("Added comment to ticket {} by {}", saved.getId(), user.getUsername());
        notify("COMMENT_ADDED", saved);

        return saved;
    }

    @Override
    public Intervention addIntervention(Long ticketId, Long userId, String action, String comment, String result) {
        User user = getUserOrThrow(userId);
        Ticket ticket = getTicketOrThrow(ticketId);

        Intervention intervention = recordIntervention(ticket, user, action, comment, result);
        ticketRepository.save(ticket);
        notify("INTERVENTION_ADDED", ticket);
        log.info("Added intervention {} to ticket {} by {}", intervention.getId(), ticket.getId(), user.getUsername());
        return intervention;
    }

    // ================= GET =================
    @Override
    public Page<Ticket> getAll(Pageable pageable) {
        return search(null, null, null, pageable);
    }

    @Override
    public Page<Ticket> getByStatus(TicketStatus status, Pageable pageable) {
        return search(status, null, null, pageable);
    }

    @Override
    public Page<Ticket> search(TicketStatus status, Priority priority, String source, Pageable pageable) {
        Specification<Ticket> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("archived")));

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), priority));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("monitoringSource")), source.trim().toUpperCase()));
            }

            query.orderBy(criteriaBuilder.desc(root.get("creationDate")));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return ticketRepository.findAll(specification, pageable);
    }

    @Override
    public Optional<Ticket> getById(Long id) {
        return ticketRepository.findById(id);
    }

    @Override
    public List<User> getAssignableUsers() {
        return userRepository.findAll().stream()
                .sorted((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()))
                .toList();
    }

    @Override
    public void delete(Long id) {
        ticketRepository.deleteById(id);
    }

    // ================= HELPERS =================
    private Ticket getTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found"));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private void notify(String type, Ticket ticket) {
        ws.convertAndSend("/topic/tickets", Map.of(
                "type", type,
                "data", ticket
        ));
    }

    private Long parseHostId(ZabbixProblemDTO problem) {
        if (problem == null || problem.getHostId() == null || problem.getHostId().isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(problem.getHostId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Priority mapSeverity(String severity) {
        return switch (severity) {
            case "5" -> Priority.CRITICAL;
            case "4" -> Priority.HIGH;
            case "3" -> Priority.MEDIUM;
            default -> Priority.LOW;
        };
    }

    private String resolveResourceRef(ZabbixProblemDTO problem) {
        if (problem == null) {
            return null;
        }

        if (problem.getIp() != null && !problem.getIp().isBlank()) {
            return problem.getIp();
        }
        if (problem.getHost() != null && !problem.getHost().isBlank()) {
            return problem.getHost();
        }
        return problem.getHostId();
    }

    private void ensureTransitionAllowed(TicketStatus current, TicketStatus target) {
        if (current == null || target == null || current == target) {
            return;
        }

        boolean allowed = switch (current) {
            case OPEN -> target == TicketStatus.IN_PROGRESS || target == TicketStatus.REJECTED;
            case IN_PROGRESS -> target == TicketStatus.RESOLVED || target == TicketStatus.REJECTED;
            case RESOLVED -> target == TicketStatus.VALIDATED
                    || target == TicketStatus.REJECTED
                    || target == TicketStatus.IN_PROGRESS;
            case VALIDATED -> target == TicketStatus.CLOSED;
            case REJECTED -> target == TicketStatus.CLOSED;
            case CLOSED -> false;
        };

        if (!allowed) {
            throw new TicketingException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKET_TRANSITION",
                    "Transition from " + current + " to " + target + " is not allowed"
            );
        }
    }

    private Intervention recordIntervention(
            Ticket ticket,
            User user,
            String action,
            String comment,
            String result
    ) {
        Intervention intervention = Intervention.builder()
                .ticket(ticket)
                .performedBy(user)
                .action(action != null && !action.isBlank() ? action.trim() : "COMMENT")
                .comment(comment)
                .result(result)
                .timestamp(LocalDateTime.now())
                .build();

        ticket.getInterventions().add(intervention);
        return interventionRepository.save(intervention);
    }
}
