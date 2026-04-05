package tn.iteam.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZabbixProblemDTO {

    private String problemId;
    private String host;
    private String description;
    private String severity;
    private Boolean active;
    private String source;
    private Long eventId;
    private String ip;
}
