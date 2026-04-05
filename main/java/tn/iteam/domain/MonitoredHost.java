package tn.iteam.domain;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoredHost {

    private String hostId;
    private String name;
    private String ip;
    private Integer port;
    private String source;
}