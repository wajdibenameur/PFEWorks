package tn.iteam.service.snmp;

import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.service.SnmpObservedStateService;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SnmpPollingServiceRetryClassificationTest {

    private ThreadPoolTaskExecutor executor;
    private SnmpPollingService service;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.initialize();

        SnmpProperties properties = new SnmpProperties();
        properties.setDefaultPort(161);
        properties.setDefaultCommunity("public");
        properties.setTimeoutMs(2000);
        properties.setRetries(1);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(java.time.Duration.ofMillis(1))
                .retryExceptions(IntegrationTimeoutException.class, IntegrationUnavailableException.class)
                .ignoreExceptions(IntegrationResponseException.class)
                .build();

        service = new SnmpPollingService(
                mock(SnmpDeviceRepository.class),
                mock(SnmpObservedStateService.class),
                properties,
                executor,
                TimeLimiterRegistry.ofDefaults(),
                RetryRegistry.of(retryConfig)
        );
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void classifyIoException_mapsSocketTimeoutToIntegrationTimeoutException() {
        SnmpDevice device = device("192.168.1.10", true, "public");

        RuntimeException result = ReflectionTestUtils.invokeMethod(
                service,
                "classifyRetryableException",
                device,
                new SocketTimeoutException("Read timed out")
        );

        assertThat(result).isInstanceOf(IntegrationTimeoutException.class);
    }

    @Test
    void classifyIoException_mapsInterruptedIoToIntegrationTimeoutException() {
        SnmpDevice device = device("192.168.1.11", true, "public");

        RuntimeException result = ReflectionTestUtils.invokeMethod(
                service,
                "classifyRetryableException",
                device,
                new InterruptedIOException("Interrupted")
        );

        assertThat(result).isInstanceOf(IntegrationTimeoutException.class);
    }

    @Test
    void classifyIoException_mapsGenericIoToIntegrationUnavailableException() {
        SnmpDevice device = device("192.168.1.12", true, "public");

        RuntimeException result = ReflectionTestUtils.invokeMethod(
                service,
                "classifyRetryableException",
                device,
                new IOException("No route to host")
        );

        assertThat(result).isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    void pollDeviceWithTimeout_rejectsInvalidConfigurationWithoutRetryableException() {
        SnmpDevice device = device(null, true, "");
        device.setSnmpPort(161);

        Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(
                Throwable.class,
                () -> ReflectionTestUtils.invokeMethod(service, "pollDeviceWithTimeout", device)
        );

        Throwable cause = unwrap(thrown);
        assertThat(cause).isInstanceOf(IntegrationResponseException.class);
    }

    private SnmpDevice device(String ip, boolean enabled, String community) {
        SnmpDevice device = new SnmpDevice();
        device.setIpAddress(ip);
        device.setEnabled(enabled);
        device.setHostname("device");
        device.setSnmpPort(161);
        device.setSnmpCommunity(community);
        device.setStatus(DeviceStatus.UNKNOWN);
        return device;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.lang.reflect.InvocationTargetException
                || current instanceof RuntimeException)) {
            current = current.getCause();
        }
        return current;
    }
}
