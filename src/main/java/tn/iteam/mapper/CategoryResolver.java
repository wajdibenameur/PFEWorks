package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;

@Component
public class CategoryResolver {

    public String resolve(ServiceStatusDTO dto) {
        // Exemple de règle simple
        switch (dto.getSource().toUpperCase()) {
            case "ZABBIX":
                return "ZABBIX";
            case "OBSERVIUM":
                return "SERVER";
            case "CAMERA":
                return "CAMERA";
            case "ZKBIO":
                return "ACCESS_CONTROL";
            default:
                return "UNKNOWN";
        }
    }
}
