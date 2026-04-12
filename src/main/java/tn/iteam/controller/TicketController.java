package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.Enums.TicketStatus;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.service.TicketService;

import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // ================= CREATE FROM ZABBIX =================
    @PostMapping("/from-problem")
    public ResponseEntity<Ticket> createFromProblem(
            @RequestBody ZabbixProblemDTO problem,
            @RequestParam Long userId
    ) {
        User creator = new User();
        creator.setId(userId); // ⚠️ simplification sans UserRepository

        return ResponseEntity.ok(ticketService.createFromProblem(problem, creator));
    }

    // ================= CREATE MANUAL =================
    @PostMapping
    public ResponseEntity<Ticket> createManual(
            @RequestBody Ticket ticket,
            @RequestParam Long userId
    ) {
        User creator = new User();
        creator.setId(userId);

        return ResponseEntity.ok(ticketService.createManual(ticket, creator));
    }

    // ================= ASSIGN =================
    @PutMapping("/{id}/assign/{userId}")
    public ResponseEntity<Ticket> assign(
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ticketService.assign(id, userId));
    }

    // ================= UPDATE STATUS =================
    @PutMapping("/{id}/status")
    public ResponseEntity<Ticket> updateStatus(
            @PathVariable Long id,
            @RequestParam TicketStatus status
    ) {
        return ResponseEntity.ok(ticketService.updateStatus(id, status));
    }

    // ================= VALIDATE =================
    @PutMapping("/{id}/validate")
    public ResponseEntity<Ticket> validate(
            @PathVariable Long id,
            @RequestParam Long adminId
    ) {
        User admin = new User();
        admin.setId(adminId);

        return ResponseEntity.ok(ticketService.validate(id, admin));
    }

    // ================= REJECT =================
    @PutMapping("/{id}/reject")
    public ResponseEntity<Ticket> reject(
            @PathVariable Long id,
            @RequestParam Long adminId,
            @RequestParam String reason
    ) {
        User admin = new User();
        admin.setId(adminId);

        return ResponseEntity.ok(ticketService.reject(id, admin, reason));
    }

    // ================= ADD COMMENT =================
    @PostMapping("/{id}/comment")
    public ResponseEntity<Ticket> addComment(
            @PathVariable Long id,
            @RequestParam String comment,
            @RequestParam Long userId
    ) {
        User user = new User();
        user.setId(userId);

        return ResponseEntity.ok(ticketService.addComment(id, comment, user));
    }

    // ================= GET ALL (PAGINATION) =================
    @GetMapping
    public ResponseEntity<Page<Ticket>> getAll(Pageable pageable) {
        return ResponseEntity.ok(ticketService.getAll(pageable));
    }

    // ================= FILTER BY STATUS =================
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<Ticket>> getByStatus(
            @PathVariable TicketStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getByStatus(status, pageable));
    }

    // ================= GET BY ID =================
    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getById(@PathVariable Long id) {

        Optional<Ticket> ticket = ticketService.getById(id);

        return ticket.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ================= DELETE =================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}