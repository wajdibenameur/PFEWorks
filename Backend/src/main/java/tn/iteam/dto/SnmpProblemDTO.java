package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SnmpProblemDTO {

    private String problemId;
    private String host;
    private String hostId;
    private String description;
    private String severity;
    private boolean active;
    private String source;
    private Long eventId;
    private Long startedAt;
    private String startedAtFormatted;
    private Long lastObservedAt;
    private String lastObservedAtFormatted;
    private Long resolvedAt;
    private String resolvedAtFormatted;
}
