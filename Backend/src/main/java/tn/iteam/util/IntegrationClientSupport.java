package tn.iteam.util;

import org.springframework.web.reactive.function.client.WebClientException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public final class IntegrationClientSupport {

    public static final String STATUS_FIELD = "status";
    public static final String DEVICES_FIELD = "devices";
    public static final String ALERTS_FIELD = "alerts";
    public static final String RESULT_FIELD = "result";
    public static final String ERROR_FIELD = "error";
    public static final String UNKNOWN = "UNKNOWN";

    private static final String ON_SEPARATOR = " on ";
    private static final String DURING_SEPARATOR = " during ";
    private static final String API_SUFFIX = " API";
    private static final String UNEXPECTED_PREFIX = "Unexpected ";
    private static final String ERROR_SUFFIX = " error";
    private static final String TIMEOUT_SEPARATOR = " timeout on ";
    private static final String UNREACHABLE_SEPARATOR = " unreachable on ";
    private static final String INVALID_JSON_RESPONSE_PREFIX = "Invalid JSON response from ";

    private IntegrationClientSupport() {
    }

    public static String apiTarget(String responseField) {
        return responseField + API_SUFFIX;
    }

    public static String returnedHttp(String sourceLabel, int statusCode, String target) {
        return sourceLabel + " returned HTTP " + statusCode + ON_SEPARATOR + target;
    }

    public static String timeout(String sourceLabel, String target) {
        return sourceLabel + TIMEOUT_SEPARATOR + target;
    }

    public static String timeoutDuring(String sourceLabel, String context) {
        return sourceLabel + " timeout" + DURING_SEPARATOR + context;
    }

    public static String invalidJsonResponse(String sourceLabel, String target) {
        return INVALID_JSON_RESPONSE_PREFIX + sourceLabel + ON_SEPARATOR + target;
    }

    public static String unreachable(String sourceLabel, String target) {
        return sourceLabel + UNREACHABLE_SEPARATOR + target;
    }

    public static String unexpectedError(String sourceLabel, String target) {
        return UNEXPECTED_PREFIX + sourceLabel + ' ' + target + ERROR_SUFFIX;
    }

    public static String during(String context) {
        return DURING_SEPARATOR + context;
    }

    public static String parenthesized(String value) {
        return '(' + value + ')';
    }

    public static boolean isTimeoutException(Throwable throwable) {
        return hasCause(throwable, current ->
                current instanceof TimeoutException
                        || current instanceof SocketTimeoutException
                        || current instanceof ReadTimeoutException
                        || current instanceof ConnectTimeoutException
                        || (current instanceof WebClientException exception
                        && exception.getMessage() != null
                        && exception.getMessage().toLowerCase().contains("timed out"))
        );
    }

    public static boolean containsInterruptedException(Throwable throwable) {
        return hasCause(throwable, InterruptedException.class::isInstance);
    }

    public static RuntimeException mapTransportException(
            String source,
            String sourceLabel,
            String context,
            Throwable throwable
    ) {
        if (throwable instanceof IntegrationUnavailableException e) {
            return e;
        }
        if (throwable instanceof IntegrationTimeoutException e) {
            return e;
        }
        if (throwable instanceof IntegrationResponseException e) {
            return e;
        }

        if (containsInterruptedException(throwable)) {
            Thread.currentThread().interrupt();
            return new IntegrationUnavailableException(
                    source,
                    sourceLabel + " request interrupted" + during(context),
                    throwable
            );
        }

        if (isTimeoutException(throwable)) {
            return new IntegrationTimeoutException(
                    source,
                    timeoutDuring(sourceLabel, context),
                    throwable
            );
        }

        if (throwable instanceof WebClientException) {
            return new IntegrationUnavailableException(
                    source,
                    unreachable(sourceLabel, context),
                    throwable
            );
        }

        return new IntegrationUnavailableException(
                source,
                unexpectedError(sourceLabel, context),
                throwable
        );
    }

    private static boolean hasCause(Throwable throwable, Predicate<Throwable> predicate) {
        Throwable current = throwable;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            if (predicate.test(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
