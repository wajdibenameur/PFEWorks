package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.domain.ApiResponse;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ObserviumClientXFallbackTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IntegrationCacheService integrationCacheService;
    private SourceAvailabilityService availabilityService;
    private ObserviumClientX observiumClientX;

    @BeforeEach
    void setUp() {
        integrationCacheService = mock(IntegrationCacheService.class);
        availabilityService = mock(SourceAvailabilityService.class);
        observiumClientX = new ObserviumClientX(
                mock(RestTemplate.class),
                OBJECT_MAPPER,
                integrationCacheService,
                availabilityService,
                "http://observium.local",
                "token"
        );
    }

    @Test
    void apiDownRedisUpReturnsSnapshotAndMarksObserviumDegraded() {
        JsonNode snapshot = OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode().put("device", "sw1"));
        when(integrationCacheService.getSnapshot("OBSERVIUM", "devices", JsonNode.class))
                .thenReturn(Optional.of(snapshot));

        @SuppressWarnings("unchecked")
        ApiResponse<JsonNode> response = (ApiResponse<JsonNode>) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "cachedFallbackResponse",
                "devices",
                "Devices fetched successfully",
                new IntegrationUnavailableException("OBSERVIUM", "Observium API down")
        );

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(snapshot);
        assertThat(response.getMessage()).isEqualTo("Devices fetched successfully");
        verify(availabilityService).markDegraded(
                eq("OBSERVIUM"),
                eq("Observium API down")
        );
    }

    @Test
    void apiDownRedisDownThrowsAndMarksObserviumUnavailable() {
        when(integrationCacheService.getSnapshot("OBSERVIUM", "alerts", JsonNode.class))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "cachedFallbackResponse",
                "alerts",
                "Alerts fetched successfully",
                new IntegrationUnavailableException("OBSERVIUM", "Observium API down")
        )).isInstanceOf(IntegrationUnavailableException.class)
                .hasMessageContaining("Observium API down");

        verify(availabilityService).markUnavailable(
                eq("OBSERVIUM"),
                eq("Observium API down")
        );
    }

    @Test
    void circuitBreakerOpenUsesRedisFallbackAndMarksObserviumDegraded() {
        JsonNode snapshot = OBJECT_MAPPER.createArrayNode().add(OBJECT_MAPPER.createObjectNode().put("device", "fw1"));
        when(integrationCacheService.getSnapshot("OBSERVIUM", "devices", JsonNode.class))
                .thenReturn(Optional.of(snapshot));

        CallNotPermittedException callNotPermittedException =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("observiumApi"));

        @SuppressWarnings("unchecked")
        ApiResponse<JsonNode> response = (ApiResponse<JsonNode>) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "getDevicesFallback",
                callNotPermittedException
        );

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(snapshot);
        verify(availabilityService).markDegraded(
                eq("OBSERVIUM"),
                eq("CircuitBreaker 'observiumApi' is CLOSED and does not permit further calls")
        );
    }
}
