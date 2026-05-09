package tn.iteam.security.stomp;

import org.springframework.stereotype.Service;
import tn.iteam.enums.Permission;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StompDestinationAuthorizationService {

    private final Map<String, Permission> permissionsByPrefix = new LinkedHashMap<>();

    public StompDestinationAuthorizationService() {
        permissionsByPrefix.put("/topic/monitoring/problems", Permission.VIEW_ALERTS);
        permissionsByPrefix.put("/topic/monitoring/metrics", Permission.VIEW_METRICS);
        permissionsByPrefix.put("/topic/monitoring/sources", Permission.VIEW_DASHBOARD);
        permissionsByPrefix.put("/topic/tickets", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/topic/zkbio/problems", Permission.VIEW_ALERTS);
        permissionsByPrefix.put("/topic/zkbio/attendance", Permission.VIEW_LOGS);
        permissionsByPrefix.put("/topic/zkbio/devices", Permission.VIEW_HOSTS);
        permissionsByPrefix.put("/topic/zkbio/status", Permission.VIEW_HOSTS);
    }

    public Permission requiredPermission(String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }

        return permissionsByPrefix.entrySet().stream()
                .filter(entry -> destination.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
