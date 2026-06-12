package tn.iteam.service.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.iteam.service.SourceAvailabilityService;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class DatabasePersistenceGuardTest {

    private DatabaseAvailabilityService databaseAvailabilityService;
    private SourceAvailabilityService sourceAvailabilityService;
    private DatabasePersistenceGuard guard;

    @BeforeEach
    void setUp() {
        databaseAvailabilityService = mock(DatabaseAvailabilityService.class);
        sourceAvailabilityService = mock(SourceAvailabilityService.class);
        guard = new DatabasePersistenceGuard(databaseAvailabilityService, sourceAvailabilityService);
    }

    @Test
    void swallowsDatabaseFailuresAndMarksSourceDegraded() {
        RuntimeException failure = new RuntimeException(new SQLTransientConnectionException("Connection refused"));
        when(databaseAvailabilityService.isDatabaseFailure(failure)).thenReturn(true);

        assertThatCode(() -> guard.safeRun("SNMP", "metrics", () -> {
            throw failure;
        })).doesNotThrowAnyException();

        verify(databaseAvailabilityService).markDatabaseDown(failure);
        verify(sourceAvailabilityService).markDegraded("SNMP", "Database unavailable, metrics persistence skipped");
    }

    @Test
    void rethrowsNonDatabaseFailures() {
        RuntimeException failure = new RuntimeException("boom");
        when(databaseAvailabilityService.isDatabaseFailure(failure)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                guard.safeRun("SNMP", "metrics", () -> {
                    throw failure;
                }));
    }
}
