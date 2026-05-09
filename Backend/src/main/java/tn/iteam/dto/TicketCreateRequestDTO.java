package tn.iteam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Corps de requete pour la creation manuelle d un ticket")
public class TicketCreateRequestDTO {
    @Schema(description = "Titre du ticket", example = "Perte de connectivite sur un routeur")
    private String title;

    @Schema(description = "Description detaillee du ticket", example = "Le routeur principal ne repond plus au ping.")
    private String description;

    @Schema(description = "Priorite du ticket", example = "HIGH")
    private String priority;

    @Schema(description = "Identifiant de l hote concerne si disponible", example = "10101")
    private Long hostId;

    @Schema(description = "Source de supervision associee", example = "ZABBIX")
    private String monitoringSource;

    @Schema(description = "Identifiant externe du probleme si disponible", example = "evt-445")
    private String externalProblemId;

    @Schema(description = "Reference technique ou ressource concernee", example = "router-core-01")
    private String resourceRef;

    @Schema(description = "Indique si le ticket provient d un probleme externe", example = "false")
    private Boolean externalProblem;
}
