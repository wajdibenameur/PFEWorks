package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.Intervention;
import tn.iteam.domain.Role;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.enums.Priority;
import tn.iteam.enums.RoleName;
import tn.iteam.enums.TicketStatus;
import tn.iteam.exception.TicketingException;
import tn.iteam.mapper.TicketMapper;
import tn.iteam.repository.InterventionRepository;
import tn.iteam.repository.RoleRepository;
import tn.iteam.repository.TicketRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.RolePermissionMatrix;
import tn.iteam.service.TicketService;
import tn.iteam.enums.Permission;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.service.support.TicketNotificationService;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InterventionRepository interventionRepository;
    private final SimpMessagingTemplate ws;
    private final AuthenticatedUserService authenticatedUserService;
    private final TicketMapper ticketMapper;
    private final TicketNotificationService ticketNotificationService;

    private static final String SYSTEM_USERNAME = "SYSTEM";
    private static final String SYSTEM_EMAIL = "system@monitoring.local";

    // ================= CREATE FROM ZABBIX =================
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ticket createFromProblem(ZabbixProblemDTO problem) {
        return createFromProblem(problem, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ticket createFromProblem(SnmpProblemDTO problem) {
        return createFromProblem(problem, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ticket createFromProblem(ZkBioProblemDTO problem) {
        return createFromProblem(problem, null);
    }

    private Ticket createFromProblem(ZabbixProblemDTO problem, User creator) {
        return createMonitoringTicket(
                normalizeSource(problem != null ? problem.getSource() : null, MonitoringConstants.SOURCE_ZABBIX),
                problem != null ? problem.getProblemId() : null,
                problem != null ? problem.getDescription() : null,
                problem != null ? problem.getSeverity() : null,
                problem != null ? problem.getHostId() : null,
                resolveResourceRef(problem != null ? problem.getHost() : null, problem != null ? problem.getHostId() : null, problem != null ? problem.getIp() : null),
                creator
        );
    }

    private Ticket createFromProblem(SnmpProblemDTO problem, User creator) {
        return createMonitoringTicket(
                normalizeSource(problem != null ? problem.getSource() : null, MonitoringConstants.SOURCE_SNMP),
                problem != null ? problem.getProblemId() : null,
                problem != null ? problem.getDescription() : null,
                problem != null ? problem.getSeverity() : null,
                problem != null ? problem.getHostId() : null,
                resolveResourceRef(problem != null ? problem.getHost() : null, problem != null ? problem.getHostId() : null, null),
                creator
        );
    }

    private Ticket createFromProblem(ZkBioProblemDTO problem, User creator) {
        return createMonitoringTicket(
                normalizeSource(problem != null ? problem.getSource() : null, MonitoringConstants.SOURCE_ZKBIO),
                problem != null ? problem.getProblemId() : null,
                problem != null ? problem.getDescription() : null,
                problem != null ? problem.getSeverity() : null,
                null,
                resolveResourceRef(problem != null ? problem.getHost() : null, null, null),
                creator
        );
    }

    private Ticket createMonitoringTicket(
            String monitoringSource,
            String externalProblemId,
            String description,
            String severity,
            String hostId,
            String resourceRef,
            User creator
    ) {
        if (externalProblemId == null || externalProblemId.isBlank()) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_PROBLEM_ID", "External problem ID is required");
        }

        String source = normalizeSource(monitoringSource, MonitoringConstants.SOURCE_ZABBIX);
        User ticketCreator = creator != null ? creator : authenticatedOrSystemUser();
        String normalizedTitle = description != null && !description.isBlank() ? description : "Monitoring incident";
        Priority mappedPriority = mapSeverity(severity);

        Optional<Ticket> byExternalId = ticketRepository.findByMonitoringSourceAndExternalProblemId(source, externalProblemId);
        if (byExternalId.isPresent()) {
            Ticket updated = refreshMonitoringTicket(byExternalId.get(), description, hostId, resourceRef, mappedPriority, externalProblemId);
            notify("TICKET_UPDATED", updated);
            return updated;
        }

        Optional<Ticket> similarOpenTicket = ticketRepository
                .findFirstByMonitoringSourceAndResourceRefAndTitleAndArchivedFalseOrderByCreationDateDesc(
                        source,
                        resourceRef,
                        normalizedTitle
                )
                .filter(this::isDedupCandidate);

        if (similarOpenTicket.isPresent()) {
            Ticket merged = refreshMonitoringTicket(similarOpenTicket.get(), description, hostId, resourceRef, mappedPriority, externalProblemId);
            notify("TICKET_UPDATED", merged);
            return merged;
        }

        Ticket ticket = Ticket.builder()
                .title(normalizedTitle)
                .description(description)
                .hostId(parseHostId(hostId))
                .priority(mappedPriority)
                .status(TicketStatus.OPEN)
                .statusChangedAt(LocalDateTime.now())
                .externalProblem(true)
                .monitoringSource(source)
                .externalProblemId(externalProblemId)
                .resourceRef(resourceRef)
                .creationDate(LocalDateTime.now())
                .createdBy(ticketCreator)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created monitoring ticket {} from external problem {} for creator {}", saved.getId(), externalProblemId, ticketCreator.getUsername());
        notify("NEW_TICKET", saved);
        ticketNotificationService.notifyImportantTicketCreated(saved, ticketCreator);
        return saved;
    }

    private Ticket refreshMonitoringTicket(
            Ticket existing,
            String description,
            String hostId,
            String resourceRef,
            Priority mappedPriority,
            String externalProblemId
    ) {
        if (description != null && !description.isBlank()) {
            existing.setTitle(description);
            existing.setDescription(description);
        }

        Long parsedHostId = parseHostId(hostId);
        if (parsedHostId != null) {
            existing.setHostId(parsedHostId);
        }

        if (resourceRef != null && !resourceRef.isBlank()) {
            existing.setResourceRef(resourceRef);
        }

        if (mappedPriority != null && isHigherPriority(mappedPriority, existing.getPriority())) {
            existing.setPriority(mappedPriority);
        }

        if (externalProblemId != null && !externalProblemId.isBlank()) {
            existing.setExternalProblemId(externalProblemId);
        }

        if (existing.getStatus() == TicketStatus.CLOSED || existing.getStatus() == TicketStatus.REJECTED) {
            markStatusTransition(existing, TicketStatus.OPEN);
        }

        return ticketRepository.save(existing);
    }

    private boolean isDedupCandidate(Ticket ticket) {
        return ticket != null
                && Boolean.FALSE.equals(ticket.getArchived())
                && ticket.getStatus() != TicketStatus.CLOSED
                && ticket.getStatus() != TicketStatus.VALIDATED;
    }

    private boolean isHigherPriority(Priority incoming, Priority current) {
        if (incoming == null) {
            return false;
        }
        if (current == null) {
            return true;
        }

        return priorityWeight(incoming) > priorityWeight(current);
    }

    private int priorityWeight(Priority priority) {
        return switch (priority) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
            case CRITICAL -> 4;
        };
    }

    private User authenticatedOrSystemUser() {
        try {
            return authenticatedUserService.getCurrentUser();
        } catch (Exception exception) {
            return getOrCreateSystemUser();
        }
    }

    private User getOrCreateSystemUser() {
        Role systemRole = ensureSystemRole();

        return userRepository.findByUsername(SYSTEM_USERNAME)
                .map(existing -> ensureSystemUserRole(existing, systemRole))
                .orElseGet(() -> {
                    User systemUser = new User();
                    systemUser.setUsername(SYSTEM_USERNAME);
                    systemUser.setEmail(SYSTEM_EMAIL);
                    systemUser.setEnabled(true);
                    systemUser.setRole(systemRole);
                    systemUser.setRoles(new HashSet<>(Set.of(systemRole)));
                    return userRepository.save(systemUser);
                });
    }

    private User ensureSystemUserRole(User user, Role systemRole) {
        boolean changed = false;
        if (user.getRole() == null || user.getRole().getName() != RoleName.SYSTEM) {
            user.setRole(systemRole);
            changed = true;
        }

        if (user.getRoles() == null || user.getRoles().stream().noneMatch(role -> role != null && role.getName() == RoleName.SYSTEM)) {
            Set<Role> nextRoles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
            nextRoles.add(systemRole);
            user.setRoles(nextRoles);
            changed = true;
        }

        if (!user.isEnabled()) {
            user.setEnabled(true);
            changed = true;
        }

        return changed ? userRepository.save(user) : user;
    }

    private Role ensureSystemRole() {
        return roleRepository.findByName(RoleName.SYSTEM)
                .orElseGet(() -> {
                    try {
                        Role role = new Role();
                        role.setName(RoleName.SYSTEM);
                        role.setPermissions(new ArrayList<>(RolePermissionMatrix.permissionsFor(RoleName.SYSTEM)));
                        return roleRepository.save(role);
                    } catch (DataIntegrityViolationException exception) {
                        log.error(
                                "Unable to persist SYSTEM role. Legacy schema likely does not allow role.name='SYSTEM'. Run the manual SQL migration for role.name before retrying monitoring ticket creation."
                        );
                        throw exception;
                    }
                });
    }

    private String normalizeSource(String source, String defaultSource) {
        if (source == null || source.isBlank()) {
            return defaultSource;
        }
        return source.trim().toUpperCase();
    }

    private Long parseHostId(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(hostId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveResourceRef(String host, String hostId, String ip) {
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        if (host != null && !host.isBlank()) {
            return host;
        }
        return hostId;
    }

    // ================= CREATE MANUAL =================
    @Override
    @Transactional
    public Ticket createManual(Ticket ticket) {
        User creator = authenticatedUserService.getCurrentUser();
        ticket.setCreationDate(LocalDateTime.now());
        ticket.setCreatedBy(creator);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setStatusChangedAt(LocalDateTime.now());
        ticket.setInterventions(ticket.getInterventions() == null ? new ArrayList<>() : ticket.getInterventions());

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created manual ticket {} by {}", saved.getId(), creator.getUsername());
        notify("NEW_TICKET", saved);
        ticketNotificationService.notifyImportantTicketCreated(saved, creator);

        return saved;
    }

    // ================= ASSIGN =================
    @Override
    @Transactional
    public Ticket assign(Long ticketId, Long userId) {

        Ticket ticket = getTicketOrThrow(ticketId);
        User user = getUserOrThrow(userId);

        ticket.setAssignedTo(user);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            markStatusTransition(ticket, TicketStatus.IN_PROGRESS);
        }

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, user, "ASSIGNMENT", "Ticket assigned to " + user.getUsername(), "ASSIGNED");
        log.info("Assigned ticket {} to {}", saved.getId(), user.getUsername());
        notify("ASSIGNED", saved);
        ticketNotificationService.notifyAssignment(saved, authenticatedUserService.getCurrentUser());

        return saved;
    }

    // ================= STATUS UPDATE =================
    @Override
    @Transactional
    public Ticket updateStatus(Long ticketId, TicketStatus status, String resolution) {

        Ticket ticket = getTicketOrThrow(ticketId);
        ensureTransitionAllowed(ticket.getStatus(), status);
        markStatusTransition(ticket, status);
        if (resolution != null && !resolution.isBlank()) {
            ticket.setResolution(resolution.trim());
        }

        Ticket saved = ticketRepository.save(ticket);
        log.info("Updated ticket {} status from {} to {}", saved.getId(), ticket.getStatus(), status);
        notify("STATUS_UPDATED", saved);
        ticketNotificationService.notifyStatusOrValidation(saved, authenticatedUserService.getCurrentUser(), "STATUS_UPDATED");

        return saved;
    }

    // ================= VALIDATE =================
    @Override
    @Transactional
    public Ticket validate(Long ticketId) {
        User admin = authenticatedUserService.getCurrentUser();
        Ticket ticket = getTicketOrThrow(ticketId);

        ensureTransitionAllowed(ticket.getStatus(), TicketStatus.VALIDATED);
        markStatusTransition(ticket, TicketStatus.VALIDATED);
        ticket.setValidatedBy(admin);

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, admin, "VALIDATION", "Ticket validated", "VALIDATED");
        log.info("Validated ticket {} by {}", saved.getId(), admin.getUsername());
        notify("VALIDATED", saved);
        ticketNotificationService.notifyStatusOrValidation(saved, admin, "VALIDATED");

        return saved;
    }

    // ================= REJECT =================
    @Override
    @Transactional
    public Ticket reject(Long ticketId, String reason) {
        User admin = authenticatedUserService.getCurrentUser();
        Ticket ticket = getTicketOrThrow(ticketId);

        ensureTransitionAllowed(ticket.getStatus(), TicketStatus.REJECTED);
        markStatusTransition(ticket, TicketStatus.REJECTED);
        ticket.setResolution(reason);
        ticket.setValidatedBy(admin);

        Ticket saved = ticketRepository.save(ticket);
        recordIntervention(saved, admin, "REJECTION", reason, "REJECTED");
        log.info("Rejected ticket {} by {} with reason {}", saved.getId(), admin.getUsername(), reason);
        notify("REJECTED", saved);
        ticketNotificationService.notifyStatusOrValidation(saved, admin, "REJECTED");

        return saved;
    }

    // ================= COMMENT =================
    @Override
    @Transactional
    public Ticket addComment(Long ticketId, String comment) {
        User user = authenticatedUserService.getCurrentUser();
        Ticket ticket = getTicketOrThrow(ticketId);

        recordIntervention(ticket, user, "COMMENT", comment, null);
        Ticket saved = ticketRepository.save(ticket);
        log.info("Added comment to ticket {} by {}", saved.getId(), user.getUsername());
        notify("COMMENT_ADDED", saved);

        return saved;
    }

    @Override
    @Transactional
    public Intervention addIntervention(Long ticketId, String action, String comment, String result) {
        User user = authenticatedUserService.getCurrentUser();
        Ticket ticket = getTicketOrThrow(ticketId);

        Intervention intervention = recordIntervention(ticket, user, action, comment, result);
        ticketRepository.save(ticket);
        notify("INTERVENTION_ADDED", ticket);
        log.info("Added intervention {} to ticket {} by {}", intervention.getId(), ticket.getId(), user.getUsername());
        return intervention;
    }

    // ================= GET =================
    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> getAll(Pageable pageable) {
        return search(null, null, null, "active", pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> getByStatus(TicketStatus status, Pageable pageable) {
        return search(status, null, null, "active", pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Ticket> search(TicketStatus status, Priority priority, String source, String archived, Pageable pageable) {
        User currentUser = authenticatedUserService.getCurrentUser();
        Set<Permission> effectivePermissions = resolveEffectivePermissions(currentUser);
        boolean allowAllTickets = effectivePermissions.contains(Permission.VIEW_ALL_TICKETS);
        boolean allowAssignedTickets = effectivePermissions.contains(Permission.VIEW_ASSIGNED_TICKETS);

        Specification<Ticket> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String archivedMode = archived == null ? "active" : archived.trim().toLowerCase();
            if ("archived".equals(archivedMode)) {
                predicates.add(criteriaBuilder.isTrue(root.get("archived")));
            } else if (!"all".equals(archivedMode)) {
                predicates.add(criteriaBuilder.isFalse(root.get("archived")));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), priority));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("monitoringSource")), source.trim().toUpperCase()));
            }

            if (!allowAllTickets) {
                Predicate createdByPredicate = criteriaBuilder.equal(root.get("createdBy").get("id"), currentUser.getId());
                if (allowAssignedTickets) {
                    Predicate assignedToPredicate = criteriaBuilder.equal(root.get("assignedTo").get("id"), currentUser.getId());
                    predicates.add(criteriaBuilder.or(createdByPredicate, assignedToPredicate));
                } else {
                    predicates.add(createdByPredicate);
                }
            }

            query.orderBy(criteriaBuilder.desc(root.get("creationDate")));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return ticketRepository.findAll(specification, pageable);
    }

    @Override
    @Transactional
    public Ticket archive(Long ticketId) {
        Ticket ticket = getTicketOrThrow(ticketId);
        ticket.setArchived(true);
        if (ticket.getArchivedAt() == null) {
            ticket.setArchivedAt(LocalDateTime.now());
        }
        Ticket saved = ticketRepository.save(ticket);
        notify("ARCHIVED", saved);
        return saved;
    }

    @Override
    @Transactional
    public Ticket unarchive(Long ticketId) {
        Ticket ticket = getTicketOrThrow(ticketId);
        ticket.setArchived(false);
        ticket.setArchivedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        notify("UNARCHIVED", saved);
        return saved;
    }

    private Set<Permission> resolveEffectivePermissions(User user) {
        if (user == null) {
            return Set.of();
        }

        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(role -> role != null && role.getPermissions() != null)
                    .forEach(role -> effective.addAll(role.getPermissions()));
        }
        if (user.getRole() != null && user.getRole().getPermissions() != null) {
            effective.addAll(user.getRole().getPermissions());
        }
        if (user.getExtraPermissions() != null) {
            effective.addAll(user.getExtraPermissions());
        }
        if (user.getRevokedPermissions() != null) {
            effective.removeAll(user.getRevokedPermissions());
        }

        return Set.copyOf(effective);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> getById(Long id) {
        return ticketRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAssignableUsers() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @Override
    @Transactional
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
                "data", ticketMapper.toResponse(ticket)
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

    private void markStatusTransition(Ticket ticket, TicketStatus targetStatus) {
        if (ticket == null || targetStatus == null) {
            return;
        }

        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(targetStatus);

        if (previous != targetStatus) {
            ticket.setStatusChangedAt(LocalDateTime.now());
        }

        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (targetStatus == TicketStatus.VALIDATED) {
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
            ticket.setValidatedAt(LocalDateTime.now());
        } else if (targetStatus == TicketStatus.REJECTED) {
            ticket.setValidatedAt(null);
        } else if (targetStatus == TicketStatus.OPEN || targetStatus == TicketStatus.IN_PROGRESS) {
            ticket.setResolvedAt(null);
            ticket.setValidatedAt(null);
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
