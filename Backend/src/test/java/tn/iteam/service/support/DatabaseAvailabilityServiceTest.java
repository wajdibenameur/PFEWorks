package tn.iteam.service.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.iteam.service.SourceAvailabilityService;

import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DatabaseAvailabilityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SourceAvailabilityService sourceAvailabilityService;
    private DatabaseAvailabilityService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        sourceAvailabilityService = mock(SourceAvailabilityService.class);
        service = new DatabaseAvailabilityService(jdbcTemplate, sourceAvailabilityService);
    }

    @Test
    void marksDatabaseDownAndUp() {
        service.markDatabaseDown(new SQLTransientConnectionException("Connection refused"));

        assertThat(service.isDatabaseAvailable()).isFalse();
        assertThat(service.getStatus()).isEqualTo(DatabaseAvailabilityStatus.DOWN);
        verify(sourceAvailabilityService).markUnavailable(eq("DATABASE"), contains("Connection refused"));

        service.markDatabaseUp();

        assertThat(service.isDatabaseAvailable()).isTrue();
        assertThat(service.getStatus()).isEqualTo(DatabaseAvailabilityStatus.UP);
        verify(sourceAvailabilityService).markAvailable("DATABASE");
    }

    @Test
    void detectsTransientConnectionFailure() {
        boolean detected = service.isDatabaseFailure(new SQLTransientConnectionException("Connection refused"));

        assertThat(detected).isTrue();
    }
}
