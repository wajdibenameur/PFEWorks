package tn.iteam.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObserviumClientXFallbackTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void normalizeObjectCollectionTransformsObjectValuesToArray() {
        ObserviumClientX observiumClientX =new ObserviumClientX(
                mock(WebClient.class),
                OBJECT_MAPPER,
                "http://observium.local",
                "admin",
                "password"
        );


        JsonNode objectCollection = OBJECT_MAPPER.createObjectNode()
                .set("1", OBJECT_MAPPER.createObjectNode().put("device", "sw1"));

        JsonNode normalized = (JsonNode) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "normalizeObjectCollection",
                objectCollection
        );

        assertThat(normalized).isNotNull();
        assertThat(normalized.isArray()).isTrue();
        assertThat(normalized).hasSize(1);
        assertThat(normalized.get(0).get("device").asText()).isEqualTo("sw1");
    }

    @Test
    void mapTransportExceptionMapsTimeoutsToIntegrationTimeoutException() {
        ObserviumClientX observiumClientX = new ObserviumClientX(
                mock(WebClient.class),
                OBJECT_MAPPER,
                "http://observium.local",
                "admin",
                "password"
        );
        RuntimeException transportFailure = new RuntimeException(new TimeoutException("read timed out"));

        RuntimeException mapped = (RuntimeException) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "mapTransportException",
                "/api/v0/devices",
                transportFailure
        );

        assertThat(mapped).isInstanceOf(IntegrationTimeoutException.class);
        assertThat(mapped.getCause()).isSameAs(transportFailure);
    }

    @Test
    void mapTransportExceptionMapsNonTimeoutWebClientFailuresToIntegrationUnavailableException() {
        ObserviumClientX observiumClientX = new ObserviumClientX(
                mock(WebClient.class),
                OBJECT_MAPPER,
                "http://observium.local",
                "admin",
                "password"
        );
        WebClientRequestException transportFailure = new WebClientRequestException(
                new RuntimeException("connection refused"),
                HttpMethod.GET,
                URI.create("http://observium.local/api/v0/devices"),
                HttpHeaders.EMPTY
        );

        RuntimeException mapped = (RuntimeException) ReflectionTestUtils.invokeMethod(
                observiumClientX,
                "mapTransportException",
                "/api/v0/devices",
                transportFailure
        );

        assertThat(mapped).isInstanceOf(IntegrationUnavailableException.class);
        assertThat(mapped).isNotInstanceOf(IntegrationTimeoutException.class);
        assertThat(mapped.getCause()).isSameAs(transportFailure);
    }
}
