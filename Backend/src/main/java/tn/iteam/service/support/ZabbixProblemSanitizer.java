package tn.iteam.service.support;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.util.Locale;

@Component
public class ZabbixProblemSanitizer {

    private static final String EMPTY_PROBLEM_ID_LOG_TEMPLATE = "Skipping problem with empty problemId: {}";
    private static final String MISSING_HOST_ID_LOG_TEMPLATE = "Skipping problem {} because hostId is missing";
    private static final String MISSING_STARTED_AT_LOG_TEMPLATE =
            "Problem {} missing startedAt from Zabbix, applying fallback current time";
    private static final String MISSING_RESOLVED_AT_LOG_TEMPLATE =
            "Problem {} marked RESOLVED without resolvedAt, applying fallback current time";
    private static final String MISSING_SEVERITY_LOG_TEMPLATE =
            "Problem {} missing severity, applying fallback severity={}";
    private static final String MISSING_EVENT_ID_LOG_TEMPLATE =
            "Problem {} missing eventId, applying fallback value={}";

    public ZabbixProblemDTO sanitize(ZabbixProblemDTO dto, Logger log) {
        if (dto == null) {
            return null;
        }

        if (isBlank(dto.getProblemId())) {
            log.warn(EMPTY_PROBLEM_ID_LOG_TEMPLATE, dto);
            return null;
        }

        if (isBlank(dto.getHostId())) {
            log.warn(MISSING_HOST_ID_LOG_TEMPLATE, dto.getProblemId());
            return null;
        }

        long fallbackNow = Instant.now().getEpochSecond();
        Long startedAt = normalizeStartedAt(dto, log, fallbackNow);
        boolean active = dto.getActive() == null || dto.getActive();
        String status = normalizeStatus(dto.getStatus(), active);
        Long resolvedAt = normalizeResolvedAt(dto, log, status, fallbackNow);
        String severity = normalizeSeverity(dto, log);
        Long eventId = normalizeEventId(dto, log, fallbackNow);

        return ZabbixProblemDTO.builder()
                .problemId(dto.getProblemId())
                .host(defaultIfBlank(dto.getHost(), MonitoringConstants.UNKNOWN))
                .port(dto.getPort())
                .hostId(dto.getHostId())
                .description(defaultIfBlank(dto.getDescription(), MonitoringConstants.NO_DESCRIPTION_CODE))
                .severity(severity)
                .active(active)
                .source(defaultIfBlank(dto.getSource(), MonitoringConstants.SOURCE_LABEL_ZABBIX))
                .eventId(eventId)
                .ip(dto.getIp())
                .startedAt(startedAt)
                .startedAtFormatted(dto.getStartedAtFormatted())
                .resolvedAt(resolvedAt)
                .resolvedAtFormatted(dto.getResolvedAtFormatted())
                .status(status.toUpperCase(Locale.ROOT))
                .build();
    }

    private Long normalizeStartedAt(ZabbixProblemDTO dto, Logger log, long fallbackNow) {
        Long startedAt = dto.getStartedAt();
        if (startedAt == null || startedAt <= 0) {
            log.warn(MISSING_STARTED_AT_LOG_TEMPLATE, dto.getProblemId());
            return fallbackNow;
        }
        return startedAt;
    }

    private String normalizeStatus(String status, boolean active) {
        if (isBlank(status)) {
            return active ? MonitoringConstants.STATUS_ACTIVE : MonitoringConstants.STATUS_RESOLVED;
        }
        return status;
    }

    private Long normalizeResolvedAt(ZabbixProblemDTO dto, Logger log, String status, long fallbackNow) {
        Long resolvedAt = dto.getResolvedAt();
        if (MonitoringConstants.STATUS_RESOLVED.equalsIgnoreCase(status) && (resolvedAt == null || resolvedAt <= 0)) {
            log.warn(MISSING_RESOLVED_AT_LOG_TEMPLATE, dto.getProblemId());
            return fallbackNow;
        }
        if (MonitoringConstants.STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return null;
        }
        return resolvedAt;
    }

    private String normalizeSeverity(ZabbixProblemDTO dto, Logger log) {
        String severity = dto.getSeverity();
        if (isBlank(severity)) {
            log.warn(MISSING_SEVERITY_LOG_TEMPLATE, dto.getProblemId(), MonitoringConstants.ZERO_STRING);
            return MonitoringConstants.ZERO_STRING;
        }
        return severity;
    }

    private Long normalizeEventId(ZabbixProblemDTO dto, Logger log, long fallbackNow) {
        Long eventId = dto.getEventId();
        if (eventId == null || eventId <= 0) {
            try {
                eventId = Long.parseLong(dto.getProblemId());
            } catch (NumberFormatException ex) {
                eventId = fallbackNow;
            }
            log.warn(MISSING_EVENT_ID_LOG_TEMPLATE, dto.getProblemId(), eventId);
        }
        return eventId;
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
