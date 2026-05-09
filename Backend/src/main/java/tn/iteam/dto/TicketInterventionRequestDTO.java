package tn.iteam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Corps de requete pour ajouter une intervention sur un ticket")
public class TicketInterventionRequestDTO {
    @Schema(description = "Action realisee", example = "Redemarrage de l equipement")
    private String action;

    @Schema(description = "Commentaire complementaire", example = "Intervention realisee a distance")
    private String comment;

    @Schema(description = "Resultat de l intervention", example = "Service retabli")
    private String result;
}
