package tn.iteam.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ZkBioClientXExceptionMappingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mapTransportExceptionMapsTimeoutsToIntegrationTimeoutException() {
        ZkBioClientX zkBioClientX = new ZkBioClientX(mock(WebClient.class), OBJECT_MAPPER);
        RuntimeException transportFailure = new RuntimeException(new TimeoutException("read timed out"));

        RuntimeException mapped = (RuntimeException) ReflectionTestUtils.invokeMethod(
                zkBioClientX,
                "mapTransportException",
                "/api/v1/status",
                transportFailure
        );

        assertThat(mapped).isInstanceOf(IntegrationTimeoutException.class);
        assertThat(mapped.getCause()).isSameAs(transportFailure);
    }

    @Test
    void mapTransportExceptionMapsNonTimeoutWebClientFailuresToIntegrationUnavailableException() {
        ZkBioClientX zkBioClientX = new ZkBioClientX(mock(WebClient.class), OBJECT_MAPPER);
        WebClientRequestException transportFailure = new WebClientRequestException(
                new RuntimeException("connection refused"),
                HttpMethod.GET,
                URI.create("http://zkbio.local/api/v1/status"),
                HttpHeaders.EMPTY
        );

        RuntimeException mapped = (RuntimeException) ReflectionTestUtils.invokeMethod(
                zkBioClientX,
                "mapTransportException",
                "/api/v1/status",
                transportFailure
        );

        assertThat(mapped).isInstanceOf(IntegrationUnavailableException.class);
        assertThat(mapped).isNotInstanceOf(IntegrationTimeoutException.class);
        assertThat(mapped.getCause()).isSameAs(transportFailure);
    }
}
