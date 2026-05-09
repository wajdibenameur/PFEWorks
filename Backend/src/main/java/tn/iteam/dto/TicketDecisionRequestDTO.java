package tn.iteam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Corps de requete pour valider ou rejeter un ticket")
public class TicketDecisionRequestDTO {
    @Schema(description = "Raison du rejet si le ticket est rejete", example = "Informations insuffisantes")
    private String reason;
}
