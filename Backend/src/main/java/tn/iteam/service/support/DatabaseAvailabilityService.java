package tn.iteam.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.iteam.service.SourceAvailabilityService;

@Slf4j
@Service
public class DatabaseAvailabilityService {

    private static final String SOURCE = "DATABASE";

    private final JdbcTemplate jdbcTemplate;
    private final SourceAvailabilityService availabilityService;

    private volatile boolean available = false;

    public DatabaseAvailabilityService(
            JdbcTemplate jdbcTemplate,
            SourceAvailabilityService availabilityService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.availabilityService = availabilityService;
    }

    public boolean isAvailable() {
        return available;
    }

    // 🔥 هذا أهم جزء: check كل 5 ثواني
    @Scheduled(fixedDelay = 5000)
    public void checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            if (!available) {
                log.info(" Database is BACK online");
            }

            available = true;
            availabilityService.markAvailable(SOURCE);

        } catch (Exception ex) {
            if (available) {
                log.warn(" Database DOWN: {}", safeMessage(ex));
            }

            available = false;
            availabilityService.markUnavailable(SOURCE, safeMessage(ex));
        }
    }

    public void markUnavailable(Throwable ex) {
        available = false;
        availabilityService.markUnavailable(SOURCE, safeMessage(ex));
    }

    public void markAvailable() {
        available = true;
        availabilityService.markAvailable(SOURCE);
    }

    private String safeMessage(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "Database unavailable";
        }
        return ex.getMessage();
    }
}