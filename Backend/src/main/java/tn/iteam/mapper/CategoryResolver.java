package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.snmp.SnmpSubnetClassifier;
import tn.iteam.util.MonitoringConstants;

@Component
public class CategoryResolver {
    private final SnmpSubnetClassifier subnetClassifier;

    public CategoryResolver(SnmpSubnetClassifier subnetClassifier) {
        this.subnetClassifier = subnetClassifier;
    }

    public String resolve(ServiceStatusDTO dto) {
        if (dto == null || dto.getSource() == null) {
            return MonitoringConstants.UNKNOWN;
        }

        switch (dto.getSource().toUpperCase()) {
            case "ZABBIX":
                return "ZABBIX";
            case "SNMP":
                if (!subnetClassifier.isIncludedInScope(dto.getIp())) {
                    return MonitoringConstants.UNKNOWN;
                }
                return subnetClassifier.resolveCategory(dto.getIp());
            case "CAMERA":
                return "CAMERA";
            case "ZKBIO":
                return "ACCESS_CONTROL";
            default:
                return MonitoringConstants.UNKNOWN;
        }
    }

}
