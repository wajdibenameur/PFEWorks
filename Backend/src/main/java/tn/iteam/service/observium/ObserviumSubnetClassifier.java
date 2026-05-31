package tn.iteam.service.observium;

import org.springframework.stereotype.Component;
import tn.iteam.config.ObserviumSnmpProperties;
import tn.iteam.util.MonitoringConstants;

import java.util.List;

@Component
public class ObserviumSubnetClassifier {

    private final ObserviumSnmpProperties properties;

    public ObserviumSubnetClassifier(ObserviumSnmpProperties properties) {
        this.properties = properties;
    }

    public boolean isIncludedInScope(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }
        String ip = ipAddress.trim();
        for (String subnet : properties.getExcludedSubnets()) {
            if (matchesSubnet(ip, subnet)) {
                return false;
            }
        }
        return true;
    }

    public String resolveCategory(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return MonitoringConstants.UNKNOWN;
        }
        String ip = ipAddress.trim();

        if (matchesAny(ip, properties.getSwitchesSubnets())) {
            return MonitoringConstants.CATEGORY_SWITCH;
        }
        if (matchesAny(ip, properties.getRoutersSubnets())) {
            return MonitoringConstants.CATEGORY_ROUTER;
        }
        if (matchesAny(ip, properties.getFirewallsSubnets())) {
            return MonitoringConstants.CATEGORY_FIREWALL;
        }
        if (matchesAny(ip, properties.getLoadBalancersSubnets())) {
            return MonitoringConstants.CATEGORY_LOAD_BALANCER;
        }
        if (matchesAny(ip, properties.getWifiAccessPointsSubnets())) {
            return MonitoringConstants.CATEGORY_WIFI_ACCESS_POINT;
        }
        if (matchesAny(ip, properties.getNetworkControllersSubnets())) {
            return MonitoringConstants.CATEGORY_NETWORK_CONTROLLER;
        }
        if (matchesAny(ip, properties.getPrintersSubnets())) {
            return MonitoringConstants.CATEGORY_PRINTER;
        }
        return MonitoringConstants.UNKNOWN;
    }

    private boolean matchesAny(String ip, List<String> subnets) {
        if (subnets == null || subnets.isEmpty()) {
            return false;
        }
        for (String subnet : subnets) {
            if (matchesSubnet(ip, subnet)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSubnet(String ip, String subnet) {
        if (subnet == null || subnet.isBlank()) {
            return false;
        }
        String normalized = subnet.trim();
        return ip.startsWith(normalized.endsWith(".") ? normalized : normalized + ".");
    }
}

