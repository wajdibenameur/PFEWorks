package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class ObserviumMapper {

    private static final String HOSTNAME_FIELD = "hostname";
    private static final String TYPE_FIELD = "type";
    private static final String OS_FIELD = "os";
    private static final String HARDWARE_FIELD = "hardware";
    private static final String ALERT_ID_FIELD = "alert_id";
    private static final String MESSAGE_FIELD = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String OPEN_STATUS = "open";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OBSERVIUM_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Transforme un device JSON Observium en ServiceStatusDTO
     */
    public ServiceStatusDTO mapDeviceToDTO(JsonNode deviceNode) {
        ServiceStatusDTO dto = new ServiceStatusDTO();
        dto.setSource(MonitoringConstants.SOURCE_OBSERVIUM);

        dto.setName(readText(deviceNode, HOSTNAME_FIELD, MonitoringConstants.UNKNOWN));
        dto.setIp(readText(deviceNode, MonitoringConstants.IP_FIELD, MonitoringConstants.IP_UNKNOWN));
        dto.setPort(DEFAULT_HTTP_PORT);
        dto.setProtocol(MonitoringConstants.PROTOCOL_HTTP);
        dto.setCategory(resolveCategory(deviceNode));

        if (deviceNode.has(MonitoringConstants.STATUS_FIELD)) {
            dto.setStatus(deviceNode.get(MonitoringConstants.STATUS_FIELD).asInt() == 0
                    ? MonitoringConstants.STATUS_UP
                    : MonitoringConstants.STATUS_DOWN);
        } else {
            dto.setStatus(MonitoringConstants.UNKNOWN);
        }

        return dto;
    }

    /**
     * Transforme une alerte Observium en ObserviumProblemDTO
     */
    public ObserviumProblemDTO mapAlertToDTO(JsonNode alertNode) {
        Long startedAt = extractStartedAt(alertNode);
        Long resolvedAt = extractResolvedAt(alertNode);

        return ObserviumProblemDTO.builder()
                .problemId(alertNode.has(ALERT_ID_FIELD) ? alertNode.get(ALERT_ID_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .host(alertNode.has(HOSTNAME_FIELD) ? alertNode.get(HOSTNAME_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .hostId(resolveHostId(alertNode))
                .description(alertNode.has(MESSAGE_FIELD) ? alertNode.get(MESSAGE_FIELD).asText() : MonitoringConstants.NO_DESCRIPTION)
                .severity(alertNode.has(SEVERITY_FIELD) ? alertNode.get(SEVERITY_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .active(alertNode.has(MonitoringConstants.STATUS_FIELD)
                        && alertNode.get(MonitoringConstants.STATUS_FIELD).asText().equalsIgnoreCase(OPEN_STATUS))
                .source(MonitoringConstants.SOURCE_OBSERVIUM)
                .eventId(alertNode.has(ALERT_ID_FIELD) ? alertNode.get(ALERT_ID_FIELD).asLong() : 0L)
                .startedAt(startedAt)
                .startedAtFormatted(MonitoringNormalizeUtils.formatTimestamp(startedAt))
                .resolvedAt(resolvedAt)
                .resolvedAtFormatted(MonitoringNormalizeUtils.formatTimestamp(resolvedAt))
                .build();
    }

    private Long extractStartedAt(JsonNode node) {
        return firstEpoch(node,
                "alert_unixtime",
                "alert_timestamp_unixtime",
                "unixtime",
                "timestamp",
                "last_changed",
                "last_change",
                "changed",
                "opened",
                "opened_at",
                "start",
                "start_at",
                "started_at",
                "alert_timestamp");
    }

    private Long extractResolvedAt(JsonNode node) {
        return firstEpoch(node,
                "resolved_unixtime",
                "resolved_timestamp_unixtime",
                "resolved",
                "resolved_at",
                "recovered",
                "recovered_at",
                "recovery_timestamp",
                "closed",
                "closed_at",
                "ended",
                "ended_at");
    }

    private Long firstEpoch(JsonNode node, String... fields) {
        for (String field : fields) {
            Long value = parseEpoch(node.get(field));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long parseEpoch(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isIntegralNumber()) {
            long raw = valueNode.asLong();
            return normalizeEpoch(raw);
        }

        String text = valueNode.asText(null);
        if (text == null) {
            return null;
        }

        String normalized = text.trim();
        if (normalized.isEmpty()
                || "null".equalsIgnoreCase(normalized)
                || "0".equals(normalized)
                || "0000-00-00 00:00:00".equals(normalized)) {
            return null;
        }

        try {
            return normalizeEpoch(Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            // Try known date formats below.
        }

        for (DateTimeFormatter formatter : candidateFormatters()) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(normalized, formatter).toEpochSecond();
                }
                if (formatter == DateTimeFormatter.ISO_INSTANT) {
                    return Instant.parse(normalized).getEpochSecond();
                }
                return LocalDateTime.parse(normalized, formatter)
                        .atZone(ZoneId.systemDefault())
                        .toEpochSecond();
            } catch (DateTimeParseException ignored) {
                // Try next formatter.
            }
        }

        return null;
    }

    private List<DateTimeFormatter> candidateFormatters() {
        return List.of(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_INSTANT,
                OBSERVIUM_DATE_TIME
        );
    }

    private Long normalizeEpoch(long raw) {
        if (raw <= 0) {
            return null;
        }
        if (raw >= 1_000_000_000_000L) {
            return raw / 1000L;
        }
        return raw;
    }

    private String resolveCategory(JsonNode deviceNode) {
        String fingerprint = String.join(" ",
                readText(deviceNode, TYPE_FIELD, ""),
                readText(deviceNode, OS_FIELD, ""),
                readText(deviceNode, HARDWARE_FIELD, ""),
                readText(deviceNode, HOSTNAME_FIELD, ""))
                .toLowerCase();

        if (fingerprint.contains("printer") || fingerprint.contains("print")) {
            return MonitoringConstants.CATEGORY_PRINTER;
        }
        if (fingerprint.contains("access point")
                || fingerprint.contains("wireless")
                || fingerprint.contains("wifi")
                || fingerprint.contains("wlan")
                || fingerprint.contains("-ap")) {
            return MonitoringConstants.CATEGORY_ACCESS_POINT;
        }
        if (fingerprint.contains("switch")) {
            return MonitoringConstants.CATEGORY_SWITCH;
        }

        return MonitoringConstants.CATEGORY_SERVER;
    }

    private String readText(JsonNode node, String field, String fallback) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText(fallback);
        }
        return fallback;
    }

    private String resolveHostId(JsonNode node) {
        return firstNonBlank(
                readText(node, "device_id", ""),
                readText(node, HOSTNAME_FIELD, ""),
                readText(node, MonitoringConstants.IP_FIELD, ""),
                MonitoringConstants.UNKNOWN
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return MonitoringConstants.UNKNOWN;
    }
}
