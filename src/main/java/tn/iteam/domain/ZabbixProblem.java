package tn.iteam.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zabbix_problem")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZabbixProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String problemId; // identifiant unique Zabbix
    private String host;
    private String description;
    private String severity;
    private Boolean active;
    private String ip;
    private Integer port;
    private String source = "Zabbix"; // pour référence

    private Long eventId; // optionnel selon besoin
}
