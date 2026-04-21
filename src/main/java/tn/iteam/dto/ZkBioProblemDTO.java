package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZkBioProblemDTO {
    private String problemId;
    private String host;
    private String description;
    private String severity;
    private boolean active;
    private String status;
    private Long startedAt;
    private String startedAtFormatted;
    private Long resolvedAt;
    private String resolvedAtFormatted;
    private String source;  // "ZKBIO"
    private long eventId;
}
