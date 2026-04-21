package tn.iteam.util;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

public final class IntegrationClientSupport {

    public static final String STATUS_FIELD = "status";
    public static final String DEVICES_FIELD = "devices";
    public static final String ALERTS_FIELD = "alerts";
    public static final String RESULT_FIELD = "result";
    public static final String ERROR_FIELD = "error";
    public static final String UNKNOWN = "UNKNOWN";

    private static final String HTTP_PREFIX = "HTTP ";
    private static final String ON_SEPARATOR = " on ";
    private static final String DURING_SEPARATOR = " during ";
    private static final String API_SUFFIX = " API";
    private static final String TIMEOUT_ON_PREFIX = "Timeout on ";
    private static final String INVALID_JSON_ON_PREFIX = "Invalid JSON on ";
    private static final String TRANSPORT_ERROR_ON_PREFIX = "Transport error on ";
    private static final String UNEXPECTED_PREFIX = "Unexpected ";
    private static final String ERROR_SUFFIX = " error";
    private static final String RETURNED_HTTP_SEPARATOR = " returned HTTP ";
    private static final String TIMEOUT_SEPARATOR = " timeout on ";
    private static final String UNREACHABLE_SEPARATOR = " unreachable on ";
    private static final String INVALID_JSON_RESPONSE_PREFIX = "Invalid JSON response from ";

    private IntegrationClientSupport() {
    }

    public static String apiTarget(String responseField) {
        return responseField + API_SUFFIX;
    }

    public static String httpOn(String target, int statusCode) {
        return HTTP_PREFIX + statusCode + ON_SEPARATOR + target;
    }

    public static String returnedHttp(String sourceLabel, int statusCode, String target) {
        return sourceLabel + RETURNED_HTTP_SEPARATOR + statusCode + ON_SEPARATOR + target;
    }

    public static String returnedHttpDuring(String sourceLabel, int statusCode, String context) {
        return sourceLabel + RETURNED_HTTP_SEPARATOR + statusCode + DURING_SEPARATOR + context;
    }

    public static String timeoutOn(String target) {
        return TIMEOUT_ON_PREFIX + target;
    }

    public static String timeout(String sourceLabel, String target) {
        return sourceLabel + TIMEOUT_SEPARATOR + target;
    }

    public static String timeoutDuring(String sourceLabel, String context) {
        return sourceLabel + " timeout" + DURING_SEPARATOR + context;
    }

    public static String invalidJsonOn(String target) {
        return INVALID_JSON_ON_PREFIX + target;
    }

    public static String invalidJsonResponse(String sourceLabel, String target) {
        return INVALID_JSON_RESPONSE_PREFIX + sourceLabel + ON_SEPARATOR + target;
    }

    public static String transportErrorOn(String target) {
        return TRANSPORT_ERROR_ON_PREFIX + target;
    }

    public static String unreachable(String sourceLabel, String target) {
        return sourceLabel + UNREACHABLE_SEPARATOR + target;
    }

    public static String unexpectedErrorOn(String target) {
        return UNEXPECTED_PREFIX + target + ERROR_SUFFIX;
    }

    public static String unexpectedError(String sourceLabel, String target) {
        return UNEXPECTED_PREFIX + sourceLabel + ' ' + target + ERROR_SUFFIX;
    }

    public static String during(String context) {
        return DURING_SEPARATOR + context;
    }

    public static String duringMessage(String prefix, String context) {
        return prefix + DURING_SEPARATOR + context;
    }

    public static String parenthesized(String value) {
        return '(' + value + ')';
    }

    public static String stableFallbackReason(String sourceLabel, String defaultReason, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return sourceLabel + " circuit breaker open";
        }

        if (throwable != null && throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            return throwable.getMessage();
        }

        return defaultReason;
    }
}
