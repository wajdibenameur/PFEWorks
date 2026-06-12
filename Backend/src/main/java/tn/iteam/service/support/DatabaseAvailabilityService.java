package tn.iteam.service.support;

import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import tn.iteam.service.SourceAvailabilityService;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DatabaseAvailabilityService {

    private static final String SOURCE = "DATABASE";
    private static final Set<String> CONNECTION_EXCEPTION_NAMES = Set.of(
            "CommunicationsException",
            "CJCommunicationsException",
            "JDBCConnectionException"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SourceAvailabilityService availabilityService;

    private volatile DatabaseAvailabilityStatus status = DatabaseAvailabilityStatus.DOWN;
    private final AtomicLong lastWarningLogAt = new AtomicLong(0L);

    @Value("${database.availability.warning-throttle-ms:30000}")
    private long warningThrottleMs = 30000L;

    public DatabaseAvailabilityService(
            JdbcTemplate jdbcTemplate,
            SourceAvailabilityService availabilityService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.availabilityService = availabilityService;
    }

    public boolean isDatabaseAvailable() {
        return status == DatabaseAvailabilityStatus.UP;
    }

    public DatabaseAvailabilityStatus getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return isDatabaseAvailable();
    }

    @Scheduled(fixedDelayString = "${database.availability.healthcheck-delay-ms:15000}")
    public void checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            markDatabaseUp();
        } catch (Exception ex) {
            markDatabaseDown(ex);
        }
    }

    public void markDatabaseDown(Throwable cause) {
        boolean wasAvailable = isDatabaseAvailable();
        status = DatabaseAvailabilityStatus.DOWN;
        availabilityService.markUnavailable(SOURCE, safeMessage(cause));

        if (wasAvailable || shouldLogWarning()) {
            log.warn("Database is DOWN. Persistence is temporarily disabled: {}", safeMessage(cause));
        }
    }

    public void markDatabaseUp() {
        boolean wasAvailable = isDatabaseAvailable();
        status = DatabaseAvailabilityStatus.UP;
        availabilityService.markAvailable(SOURCE);

        if (!wasAvailable) {
            log.info("Database is BACK online");
        }
    }

    public void markUnavailable(Throwable ex) {
        markDatabaseDown(ex);
    }

    public void markAvailable() {
        markDatabaseUp();
    }

    public boolean isDatabaseFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof CannotCreateTransactionException
                    || current instanceof PersistenceException
                    || current instanceof SQLTransientConnectionException
                    || current instanceof JDBCConnectionException) {
                return true;
            }

            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("08")) {
                    return true;
                }
            }

            String simpleName = current.getClass().getSimpleName();
            if (CONNECTION_EXCEPTION_NAMES.contains(simpleName)) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("connection refused")
                        || normalized.contains("communications link failure")
                        || normalized.contains("connection is not available")
                        || normalized.contains("unable to acquire jdbc connection")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private String safeMessage(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "Database unavailable";
        }
        return ex.getMessage();
    }

    private boolean shouldLogWarning() {
        long now = System.currentTimeMillis();
        long previous = lastWarningLogAt.get();
        if (now - previous < warningThrottleMs) {
            return false;
        }
        return lastWarningLogAt.compareAndSet(previous, now);
    }
}
