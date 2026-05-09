package tn.iteam.util;

public final class MonitoringConstants {

    public static final String SOURCE_ZABBIX = "ZABBIX";
    public static final String SOURCE_OBSERVIUM = "OBSERVIUM";
    public static final String SOURCE_ZKBIO = "ZKBIO";
    public static final String SOURCE_CAMERA = "CAMERA";

    public static final String SOURCE_LABEL_ZABBIX = "Zabbix";
    public static final String SOURCE_LABEL_OBSERVIUM = "Observium";
    public static final String SOURCE_LABEL_ZKBIO = "ZKBio";
    public static final String SOURCE_LABEL_CAMERA = "Camera";

    public static final String STATUS_FIELD = "status";
    public static final String HOST_FIELD = "host";
    public static final String HOST_ID_FIELD = "hostid";
    public static final String IP_FIELD = "ip";
    public static final String PORT_FIELD = "port";
    public static final String MAIN_FIELD = "main";
    public static final String CLOCK_FIELD = "clock";
    public static final String EVENT_ID_FIELD = "eventid";
    public static final String NAME_FIELD = "name";

    public static final String UNKNOWN = "UNKNOWN";
    public static final String IP_UNKNOWN = "IP_UNKNOWN";
    public static final String NO_DESCRIPTION = "No description";
    public static final String NO_DESCRIPTION_CODE = "NO_DESCRIPTION";
    public static final String ZERO_STRING = "0";

    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RESOLVED = "RESOLVED";

    public static final String PROTOCOL_HTTP = "HTTP";
    public static final String PROTOCOL_HTTPS = "HTTPS";
    public static final String PROTOCOL_RTSP = "RTSP";

    public static final String CATEGORY_SERVER = "SERVER";
    public static final String CATEGORY_CAMERA = "CAMERA";
    public static final String CATEGORY_ACCESS = "ACCESS";
    public static final String CATEGORY_ACCESS_POINT = "ACCESS_POINT";
    public static final String CATEGORY_PRINTER = "PRINTER";
    public static final String CATEGORY_SWITCH = "SWITCH";

    public static final String COLLECTION_FAILED_TEMPLATE = "{} collection failed: {}";
    public static final String UNEXPECTED_COLLECTION_ERROR_TEMPLATE = "Unexpected error collecting %s data";

    private MonitoringConstants() {
    }
}
