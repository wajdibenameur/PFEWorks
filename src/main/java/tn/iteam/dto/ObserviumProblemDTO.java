package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObserviumProblemDTO {

    private String problemId;
    private String host;
    private Long hostId;
    private String description;
    private String severity;
    private boolean active;
    private String source;
    private Long eventId;
}