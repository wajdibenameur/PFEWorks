package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.domain.Ticket;
import tn.iteam.dto.TicketAssignmentRequestDTO;
import tn.iteam.dto.TicketCreateRequestDTO;
import tn.iteam.dto.TicketDecisionRequestDTO;
import tn.iteam.dto.TicketInterventionRequestDTO;
import tn.iteam.dto.TicketResponseDTO;
import tn.iteam.dto.TicketStatusUpdateRequestDTO;
import tn.iteam.dto.TicketUserDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.enums.Priority;
import tn.iteam.enums.TicketStatus;
import tn.iteam.mapper.TicketMapper;
import tn.iteam.service.TicketService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;

    // ================= CREATE FROM ZABBIX =================
    @PostMapping("/from-problem")
    public ResponseEntity<TicketResponseDTO> createFromProblem(
            @RequestBody ZabbixProblemDTO problem,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.createFromProblem(problem, userId)));
    }

    // ================= CREATE MANUAL =================
    @PostMapping
    public ResponseEntity<TicketResponseDTO> createManual(
            @RequestBody TicketCreateRequestDTO request
    ) {
        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? Priority.valueOf(request.getPriority().toUpperCase()) : Priority.MEDIUM)
                .hostId(request.getHostId())
                .monitoringSource(request.getMonitoringSource())
                .externalProblemId(request.getExternalProblemId())
                .resourceRef(request.getResourceRef())
                .externalProblem(Boolean.TRUE.equals(request.getExternalProblem()))
                .build();

        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.createManual(ticket, request.getCreatorId())));
    }

    // ================= ASSIGN =================
    @PutMapping("/{id}/assign")
    public ResponseEntity<TicketResponseDTO> assign(
            @PathVariable Long id,
            @RequestBody TicketAssignmentRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.assign(id, request.getUserId())));
    }

    // ================= UPDATE STATUS =================
    @PutMapping("/{id}/status")
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody TicketStatusUpdateRequestDTO request
    ) {
        TicketStatus status = TicketStatus.valueOf(request.getStatus().toUpperCase());
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.updateStatus(id, status, request.getResolution())));
    }

    // ================= VALIDATE =================
    @PutMapping("/{id}/validate")
    public ResponseEntity<TicketResponseDTO> validate(
            @PathVariable Long id,
            @RequestBody TicketDecisionRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.validate(id, request.getAdminId())));
    }

    // ================= REJECT =================
    @PutMapping("/{id}/reject")
    public ResponseEntity<TicketResponseDTO> reject(
            @PathVariable Long id,
            @RequestBody TicketDecisionRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.reject(id, request.getAdminId(), request.getReason())));
    }

    // ================= ADD COMMENT =================
    @PostMapping("/{id}/comment")
    public ResponseEntity<TicketResponseDTO> addComment(
            @PathVariable Long id,
            @RequestParam String comment,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.addComment(id, comment, userId)));
    }

    @PostMapping("/{id}/interventions")
    public ResponseEntity<TicketResponseDTO> addIntervention(
            @PathVariable Long id,
            @RequestBody TicketInterventionRequestDTO request
    ) {
        ticketService.addIntervention(id, request.getUserId(), request.getAction(), request.getComment(), request.getResult());
        return ticketService.getById(id)
                .map(ticketMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ================= GET ALL (PAGINATION) =================
    @GetMapping
    public ResponseEntity<Page<TicketResponseDTO>> getAll(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String source,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.search(status, priority, source, pageable).map(ticketMapper::toResponse));
    }

    // ================= FILTER BY STATUS =================
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<TicketResponseDTO>> getByStatus(
            @PathVariable TicketStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getByStatus(status, pageable).map(ticketMapper::toResponse));
    }

    @GetMapping("/users")
    public ResponseEntity<List<TicketUserDTO>> getAssignableUsers() {
        return ResponseEntity.ok(ticketService.getAssignableUsers().stream().map(ticketMapper::toUser).toList());
    }

    // ================= GET BY ID =================
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> getById(@PathVariable Long id) {

        Optional<Ticket> ticket = ticketService.getById(id);

        return ticket.map(ticketMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ================= DELETE =================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
