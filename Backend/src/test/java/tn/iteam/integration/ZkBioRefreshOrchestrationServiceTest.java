package tn.iteam.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZkBioRefreshOrchestrationServiceTest {

    @Mock
    private ZkBioIntegrationOperations zkBioIntegrationOperations;

    @Test
    void refreshMonitoringAndAttendanceAsyncExecutesMainRefreshBeforeAttendance() {
        ZkBioRefreshOrchestrationService service =
                new ZkBioRefreshOrchestrationService(zkBioIntegrationOperations);

        when(zkBioIntegrationOperations.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioIntegrationOperations.refreshAttendanceAsync()).thenReturn(Mono.empty());

        service.refreshMonitoringAndAttendanceAsync().block();

        InOrder inOrder = inOrder(zkBioIntegrationOperations);
        inOrder.verify(zkBioIntegrationOperations).refreshAsync();
        inOrder.verify(zkBioIntegrationOperations).refreshAttendanceAsync();
    }
}
