package tn.iteam.adapter.zabbix;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ZabbixClientResilienceProfileTest {

    @Test
    void lightOperationsUseLightResilienceProfile() throws Exception {
        assertResilienceProfile("getHosts", List.of(), "zabbixApiLight");
        assertResilienceProfile("getHostById", List.of(String.class), "zabbixApiLight");
        assertResilienceProfile("getRecentProblems", List.of(), "zabbixApiLight");
        assertResilienceProfile("getRecentProblemsByHost", List.of(String.class), "zabbixApiLight");
        assertResilienceProfile("getTriggerById", List.of(String.class), "zabbixApiLight");
        assertResilienceProfile("getTriggersByIds", List.of(List.class), "zabbixApiLight");
        assertResilienceProfile("getTriggersByHost", List.of(String.class), "zabbixApiLight");
        assertResilienceProfile("getVersion", List.of(), "zabbixApiLight");
    }

    @Test
    void heavyOperationsUseHeavyResilienceProfile() throws Exception {
        assertResilienceProfile("getItemsByHost", List.of(String.class), "zabbixApiHeavy");
        assertResilienceProfile("getItemsByHosts", List.of(List.class), "zabbixApiHeavy");
        assertResilienceProfile("getItemHistory", List.of(String.class, int.class, long.class, long.class), "zabbixApiHeavy");
        assertResilienceProfile("getHistoryBatch", List.of(List.class, int.class, long.class, long.class), "zabbixApiHeavy");
        assertResilienceProfile("getLastItemValue", List.of(String.class, int.class), "zabbixApiHeavy");
    }

    private void assertResilienceProfile(String methodName, List<Class<?>> parameterTypes, String expectedProfile)
            throws Exception {
        Method method = ZabbixClient.class.getMethod(methodName, parameterTypes.toArray(Class[]::new));
        Retry retry = method.getAnnotation(Retry.class);
        CircuitBreaker circuitBreaker = method.getAnnotation(CircuitBreaker.class);

        assertThat(retry).isNotNull();
        assertThat(circuitBreaker).isNotNull();
        assertThat(retry.name()).isEqualTo(expectedProfile);
        assertThat(circuitBreaker.name()).isEqualTo(expectedProfile);
    }
}
