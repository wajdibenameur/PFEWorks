package tn.iteam.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Format standard de réponse de l'API")
public class ApiResponse<T> {

    @Schema(description = "Indique si l'opération a réussi")
    private boolean success;
    @Schema(description = "Message fonctionnel retourné par l'API")
    private String message;
    @Schema(description = "Source métier ou technique associée à la réponse")
    private String source;
    @Schema(description = "Données retournées par l'API")
    private T data;
    @Schema(description = "Code d'erreur applicatif éventuel")
    private String errorCode;
}
