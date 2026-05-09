package tn.iteam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Corps de requête pour mettre à jour le statut d'un ticket")
public class TicketStatusUpdateRequestDTO {
    @Schema(description = "Nouveau statut du ticket", example = "IN_PROGRESS")
    private String status;
    @Schema(description = "Texte de résolution éventuel", example = "Redémarrage du service effectué")
    private String resolution;
}
