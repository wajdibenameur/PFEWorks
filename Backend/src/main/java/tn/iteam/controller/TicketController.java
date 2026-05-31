package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Tickets", description = "API de creation, suivi et traitement des tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;

    @PostMapping("/from-problem")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).CREATE_TICKET)")
    @Operation(summary = "Creer un ticket depuis un incident", description = "Cree un ticket a partir d un incident de supervision existant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket cree avec succes"),
            @ApiResponse(responseCode = "400", description = "Requete invalide")
    })
    public ResponseEntity<TicketResponseDTO> createFromProblem(@RequestBody ZabbixProblemDTO problem) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.createFromProblem(problem)));
    }

    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).CREATE_TICKET)")
    @Operation(summary = "Creer un ticket manuel", description = "Cree un ticket manuel sans dependre d un incident externe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket cree avec succes"),
            @ApiResponse(responseCode = "400", description = "Requete invalide")
    })
    public ResponseEntity<TicketResponseDTO> createManual(@RequestBody TicketCreateRequestDTO request) {
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

        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.createManual(ticket)));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).ASSIGN_TICKET)")
    @Operation(summary = "Assigner un ticket", description = "Assigne un ticket a un utilisateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket assigne avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> assign(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @RequestBody TicketAssignmentRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.assign(id, request.getUserId())));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).EDIT_TICKET)")
    @Operation(summary = "Modifier le statut d un ticket", description = "Met a jour le statut d un ticket et sa resolution eventuelle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut mis a jour avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> updateStatus(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @RequestBody TicketStatusUpdateRequestDTO request
    ) {
        TicketStatus status = TicketStatus.valueOf(request.getStatus().toUpperCase());
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.updateStatus(id, status, request.getResolution())));
    }

    @PutMapping("/{id}/validate")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VALIDATE_TICKET)")
    @Operation(summary = "Valider un ticket", description = "Valide un ticket par un administrateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket valide avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> validate(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @RequestBody TicketDecisionRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.validate(id)));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VALIDATE_TICKET)")
    @Operation(summary = "Rejeter un ticket", description = "Rejette un ticket et enregistre eventuellement la raison du rejet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket rejete avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> reject(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @RequestBody TicketDecisionRequestDTO request
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.reject(id, request.getReason())));
    }

    @PostMapping("/{id}/comment")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).ADD_COMMENT)")
    @Operation(summary = "Ajouter un commentaire", description = "Ajoute un commentaire a un ticket existant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Commentaire ajoute avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> addComment(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @Parameter(description = "Commentaire a ajouter", required = true)
            @RequestParam String comment
    ) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.addComment(id, comment)));
    }

    @PostMapping("/{id}/interventions")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).ADD_COMMENT)")
    @Operation(summary = "Ajouter une intervention", description = "Ajoute une intervention detaillee a un ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Intervention ajoutee avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> addIntervention(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id,
            @RequestBody TicketInterventionRequestDTO request
    ) {
        ticketService.addIntervention(id, request.getAction(), request.getComment(), request.getResult());
        return ticketService.getById(id)
                .map(ticketMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    @Operation(summary = "Rechercher des tickets", description = "Retourne la liste paginee des tickets avec filtres optionnels.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tickets recuperes avec succes")
    })
    public ResponseEntity<Page<TicketResponseDTO>> getAll(
            @Parameter(description = "Filtre optionnel sur le statut du ticket")
            @RequestParam(required = false) TicketStatus status,
            @Parameter(description = "Filtre optionnel sur la priorite du ticket")
            @RequestParam(required = false) Priority priority,
            @Parameter(description = "Filtre optionnel sur la source de supervision")
            @RequestParam(required = false) String source,
            @Parameter(description = "Filtre archivage: active|archived|all")
            @RequestParam(required = false, defaultValue = "active") String archived,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.search(status, priority, source, archived, pageable).map(ticketMapper::toResponse));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    @Operation(summary = "Lister les tickets par statut", description = "Retourne les tickets pagines correspondant au statut demande.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tickets recuperes avec succes")
    })
    public ResponseEntity<Page<TicketResponseDTO>> getByStatus(
            @Parameter(description = "Statut du ticket a filtrer", required = true)
            @PathVariable TicketStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getByStatus(status, pageable).map(ticketMapper::toResponse));
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_USERS)")
    @Operation(summary = "Lister les utilisateurs assignables", description = "Retourne la liste des utilisateurs pouvant etre assignes a un ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Utilisateurs recuperes avec succes")
    })
    public ResponseEntity<List<TicketUserDTO>> getAssignableUsers() {
        return ResponseEntity.ok(ticketService.getAssignableUsers().stream().map(ticketMapper::toUser).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    @Operation(summary = "Consulter un ticket", description = "Retourne le detail d un ticket a partir de son identifiant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket recupere avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<TicketResponseDTO> getById(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id
    ) {
        Optional<Ticket> ticket = ticketService.getById(id);

        return ticket.map(ticketMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VALIDATE_TICKET)")
    @Operation(summary = "Archiver un ticket", description = "Archive un ticket et enregistre la date d archivage.")
    public ResponseEntity<TicketResponseDTO> archive(@PathVariable Long id) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.archive(id)));
    }

    @PutMapping("/{id}/unarchive")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VALIDATE_TICKET)")
    @Operation(summary = "Desarchiver un ticket", description = "Retire un ticket des archives.")
    public ResponseEntity<TicketResponseDTO> unarchive(@PathVariable Long id) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketService.unarchive(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).DELETE_TICKET)")
    @Operation(summary = "Supprimer un ticket", description = "Supprime definitivement un ticket.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ticket supprime avec succes"),
            @ApiResponse(responseCode = "404", description = "Ticket introuvable")
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifiant du ticket", required = true)
            @PathVariable Long id
    ) {
        ticketService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
