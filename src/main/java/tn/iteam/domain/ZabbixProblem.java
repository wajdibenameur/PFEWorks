package tn.iteam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class ZabbixProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;          // clé technique (DB)
    private String problemId;
    private Long hostId;
    private String host;
    private String description;
    private String severity;
    private Boolean active;
    private String ip;
    private Integer port;
    private String source = "Zabbix";
    private Long eventId;
}
