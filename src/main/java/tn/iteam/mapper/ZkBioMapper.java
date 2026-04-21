package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class ZkBioMapper {

    private static final String ID_FIELD = "id";
    private static final String DEVICE_NAME_FIELD = "device_name";
    private static final String MESSAGE_FIELD = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String ACTIVE_STATUS = "active";
    private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;
    private static final DateTimeFormatter[] DATE_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    public ZkBioProblemDTO mapAlertToDTO(JsonNode alertNode) {
        boolean active = alertNode.has(MonitoringConstants.STATUS_FIELD)
                && alertNode.get(MonitoringConstants.STATUS_FIELD).asText().equalsIgnoreCase(ACTIVE_STATUS);
        Long startedAt = extractTimestamp(alertNode);

        return ZkBioProblemDTO.builder()
                .problemId(alertNode.has(ID_FIELD) ? alertNode.get(ID_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .host(alertNode.has(DEVICE_NAME_FIELD) ? alertNode.get(DEVICE_NAME_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .description(alertNode.has(MESSAGE_FIELD) ? alertNode.get(MESSAGE_FIELD).asText() : MonitoringConstants.NO_DESCRIPTION)
                .severity(alertNode.has(SEVERITY_FIELD) ? alertNode.get(SEVERITY_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .active(active)
                .status(active ? "ACTIVE" : "RESOLVED")
                .startedAt(startedAt)
                .startedAtFormatted(formatTimestamp(startedAt))
                .resolvedAt(null)
                .resolvedAtFormatted(null)
                .source(MonitoringConstants.SOURCE_ZKBIO)
                .eventId(alertNode.has(ID_FIELD) ? alertNode.get(ID_FIELD).asLong() : 0L)
                .build();
    }

    private Long extractTimestamp(JsonNode alertNode) {
        Long timestamp = extractNumericField(alertNode, "started_at");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "startedAt");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "created_at");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "createdAt");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "event_time");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "eventTime");
        if (timestamp != null) {
            return timestamp;
        }

        timestamp = extractNumericField(alertNode, "timestamp");
        if (timestamp != null) {
            return timestamp;
        }

        return Instant.now().getEpochSecond();
    }

    private Long extractNumericField(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        JsonNode field = node.get(fieldName);
        if (field.isNumber()) {
            return normalizeEpoch(field.asLong());
        }

        if (field.isTextual()) {
            return parseTimestampValue(field.asText());
        }

        return null;
    }

    private Long parseTimestampValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isBlank()) {
            return null;
        }

        try {
            return normalizeEpoch(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            // Continue with date parsing.
        }

        try {
            return Instant.parse(value).getEpochSecond();
        } catch (DateTimeParseException ignored) {
            // Continue with local date-time parsing.
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter)
                        .atZone(ZoneId.systemDefault())
                        .toEpochSecond();
            } catch (DateTimeParseException ignored) {
                // Try next known format.
            }
        }

        return null;
    }

    private Long normalizeEpoch(Long value) {
        if (value == null) {
            return null;
        }
        return value >= EPOCH_MILLIS_THRESHOLD ? value / 1000 : value;
    }

    private String formatTimestamp(Long epoch) {
        if (epoch == null) {
            return null;
        }

        try {
            return Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            return null;
        }
    }
}
