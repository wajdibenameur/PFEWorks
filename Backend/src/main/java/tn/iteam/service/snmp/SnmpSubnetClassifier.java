package tn.iteam.service.snmp;

import org.springframework.stereotype.Component;
import tn.iteam.config.SnmpProperties;
import tn.iteam.util.MonitoringConstants;

import java.util.List;

@Component
public class SnmpSubnetClassifier {

    private final SnmpProperties properties;

    public SnmpSubnetClassifier(SnmpProperties properties) {
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

        if (matchesAddress(ip, properties.getAccessControlAddresses())) {
            return MonitoringConstants.CATEGORY_ACCESS_CONTROL;
        }
        if (matchesAddress(ip, properties.getServerAddresses())) {
            return MonitoringConstants.CATEGORY_SERVER;
        }
        if (matchesAddress(ip, properties.getUpsAddresses())) {
            return MonitoringConstants.CATEGORY_UPS;
        }
        if (matchesAddress(ip, properties.getFirewallAddresses())) {
            return MonitoringConstants.CATEGORY_FIREWALL;
        }
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

    public String resolveConfiguredCategory(String ipAddress) {
        return resolveCategory(ipAddress);
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

    private boolean matchesAddress(String ip, List<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return false;
        }
        for (String address : addresses) {
            if (address != null && !address.isBlank() && ip.equals(address.trim())) {
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
