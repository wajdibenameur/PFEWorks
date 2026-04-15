package tn.iteam.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZabbixProblemDTO {

    private String problemId;
    private String host;
    private Integer port;
    private String hostId;
    private String description;
    private String severity;
    private Boolean active;
    private String source;
    private Long eventId;
    private String ip;

    private Long startedAt;
    private String startedAtFormatted;

    private Long resolvedAt;
    private String resolvedAtFormatted;

    private String status; // ACTIVE / RESOLVED
}
