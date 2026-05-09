package tn.iteam.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;
import tn.iteam.monitoring.MonitoringSourceType;

@SpringBootTest
class ZkBioIntegrationServiceReactiveTest {

    @Autowired
    private ZkBioIntegrationService service;

    @Test
    void refreshAsync_shouldCompleteSuccessfully() {
        StepVerifier.create(service.refreshAsync())
                .verifyComplete();
    }

    @Test
    void refreshAttendanceAsync_shouldCompleteSuccessfully() {
        StepVerifier.create(service.refreshAttendanceAsync())
                .verifyComplete();
    }

    @Test
    void refreshAllAndPublishAsync_shouldCompleteSuccessfully() {
        StepVerifier.create(service.refreshAllAndPublishAsync())
                .verifyComplete();
    }
}