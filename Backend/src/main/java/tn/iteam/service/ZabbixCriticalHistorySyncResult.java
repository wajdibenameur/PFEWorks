package tn.iteam.service;

public record ZabbixCriticalHistorySyncResult(
        boolean enabled,
        int found,
        int inserted,
        int duplicatesIgnored,
        int invalidIgnored,
        String message
) {

    public static ZabbixCriticalHistorySyncResult disabled() {
        return new ZabbixCriticalHistorySyncResult(false, 0, 0, 0, 0, "critical history sync disabled");
    }

    public static ZabbixCriticalHistorySyncResult emptyEnabled() {
        return new ZabbixCriticalHistorySyncResult(true, 0, 0, 0, 0, "0 critical events found");
    }
}
