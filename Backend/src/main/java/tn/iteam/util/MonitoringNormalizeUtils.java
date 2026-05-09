package tn.iteam.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class MonitoringNormalizeUtils {

    private static final DateTimeFormatter DEFAULT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MonitoringNormalizeUtils() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeText(String value, String... nullEquivalents) {
        String normalized = normalizeText(value);
        if (normalized == null || nullEquivalents == null) {
            return normalized;
        }

        for (String nullEquivalent : nullEquivalents) {
            if (nullEquivalent != null && nullEquivalent.equalsIgnoreCase(normalized)) {
                return null;
            }
        }
        return normalized;
    }

    public static String normalizeIp(String value) {
        return normalizeText(value, MonitoringConstants.IP_UNKNOWN);
    }

    public static String formatTimestamp(Long epoch) {
        if (epoch == null) {
            return null;
        }

        try {
            return Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
                    .format(DEFAULT_TIMESTAMP_FORMATTER);
        } catch (Exception ignored) {
            return null;
        }
    }
}
