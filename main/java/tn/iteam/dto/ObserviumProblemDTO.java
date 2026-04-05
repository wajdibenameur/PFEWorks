package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObserviumProblemDTO {
    private String problemId;
    private String host;
    private String description;
    private String severity;
    private boolean active;
    private String source;  // "OBSERVIUM"
    private long eventId;
}