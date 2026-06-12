package tn.iteam.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.SourceAvailabilityService;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabasePersistenceGuard {

    private final DatabaseAvailabilityService databaseAvailabilityService;
    private final SourceAvailabilityService sourceAvailabilityService;

    public boolean safeSaveHosts(String source, List<ServiceStatusDTO> statuses, Runnable action) {
        return safeRun(source, "hosts", action);
    }

    public boolean safeSaveMetrics(String source, List<?> metrics, Runnable action) {
        return safeRun(source, "metrics", action);
    }

    public boolean safeSaveProblems(String source, List<?> problems, Runnable action) {
        return safeRun(source, "problems", action);
    }

    public boolean safeSaveServiceStatus(String source, List<ServiceStatusDTO> statuses, Runnable action) {
        return safeRun(source, "service-status", action);
    }

    public boolean safeRun(String source, String operation, Runnable action) {
        try {
            action.run();
            databaseAvailabilityService.markDatabaseUp();
            return true;
        } catch (RuntimeException exception) {
            if (!databaseAvailabilityService.isDatabaseFailure(exception)) {
                throw exception;
            }
            handleDatabaseFailure(source, operation, exception);
            return false;
        }
    }

    public <T> T safeLoad(String source, String operation, Supplier<T> supplier, T fallback) {
        try {
            T result = supplier.get();
            databaseAvailabilityService.markDatabaseUp();
            return result;
        } catch (RuntimeException exception) {
            if (!databaseAvailabilityService.isDatabaseFailure(exception)) {
                throw exception;
            }
            handleDatabaseFailure(source, operation, exception);
            return fallback;
        }
    }

    private void handleDatabaseFailure(String source, String operation, RuntimeException exception) {
        databaseAvailabilityService.markDatabaseDown(exception);
        sourceAvailabilityService.markDegraded(source, "Database unavailable, " + operation + " persistence skipped");
        log.debug("Database failure caught for source={} operation={} reason={}",
                source,
                operation,
                exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName());
    }
}
