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
        permissionsByPrefix.put("/topic/zabbix", Permission.VIEW_ZABBIX);
        permissionsByPrefix.put("/topic/snmp", Permission.VIEW_SNMP);
        permissionsByPrefix.put("/topic/camera", Permission.VIEW_CAMERA);
        permissionsByPrefix.put("/topic/access-point", Permission.VIEW_ACCESS_POINT);
        permissionsByPrefix.put("/topic/tickets", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/topic/chat.room.", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/topic/chat.presence.", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/user/queue/chat", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/user/queue/chat.presence", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/app/chat.", Permission.VIEW_TICKETS);
        permissionsByPrefix.put("/user/queue/notifications", Permission.VIEW_TICKETS);
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
