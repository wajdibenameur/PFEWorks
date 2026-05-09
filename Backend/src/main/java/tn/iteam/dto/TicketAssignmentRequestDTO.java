package tn.iteam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Corps de requête pour l'assignation d'un ticket")
public class TicketAssignmentRequestDTO {
    @Schema(description = "Identifiant de l'utilisateur à affecter", example = "5")
    private Long userId;
}
