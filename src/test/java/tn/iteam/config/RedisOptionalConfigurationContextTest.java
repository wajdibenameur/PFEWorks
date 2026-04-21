package tn.iteam.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.service.MonitoringCacheService;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisOptionalConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    RedisOptionalConfiguration.class,
                    InMemorySnapshotStore.class,
                    MonitoringCacheService.class
            );

    @Test
    void startsWithoutRedisPropertyAndUsesInMemorySnapshotStore() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SnapshotStore.class);
            assertThat(context.getBean(SnapshotStore.class)).isInstanceOf(InMemorySnapshotStore.class);
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
            assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
            assertThat(context).hasSingleBean(MonitoringCacheService.class);
        });
    }

    @Test
    void startsWhenRedisIsExplicitlyDisabledByProperty() {
        contextRunner
                .withPropertyValues("app.redis.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SnapshotStore.class);
                    assertThat(context.getBean(SnapshotStore.class)).isInstanceOf(InMemorySnapshotStore.class);
                    assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
                    assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
                });
    }

    @Test
    void startsWhenRedisIsEnabledButInaccessible() {
        contextRunner
                .withPropertyValues(
                        "app.redis.enabled=true",
                        "app.redis.host=127.0.0.1",
                        "app.redis.port=1",
                        "app.redis.connect-timeout=100ms",
                        "app.redis.command-timeout=100ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SnapshotStore.class);
                    assertThat(context.getBean(SnapshotStore.class)).isInstanceOf(InMemorySnapshotStore.class);
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                });
    }

    @Test
    void keepsServingMonitoringSnapshotsAfterRedisConnectionFailure() {
        contextRunner
                .withPropertyValues(
                        "app.redis.enabled=true",
                        "app.redis.host=127.0.0.1",
                        "app.redis.port=1",
                        "app.redis.connect-timeout=100ms",
                        "app.redis.command-timeout=100ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    StringRedisTemplate redisTemplate = context.getBean(StringRedisTemplate.class);
                    assertThatThrownBy(() -> redisTemplate.execute((RedisCallback<String>) connection -> connection.ping()))
                            .isInstanceOf(RedisConnectionFailureException.class);

                    SnapshotStore snapshotStore = context.getBean(SnapshotStore.class);
                    MonitoringCacheService cacheService = context.getBean(MonitoringCacheService.class);

                    snapshotStore.save(
                            "problems",
                            "ZABBIX",
                            StoredSnapshot.of(
                                    List.of(
                                            UnifiedMonitoringProblemDTO.builder()
                                                    .id("ZABBIX:p1")
                                                    .problemId("p1")
                                                    .description("redis-down-safe")
                                                    .build()
                                    ),
                                    false,
                                    Map.of("ZABBIX", "live")
                            )
                    );

                    MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> result =
                            cacheService.getProblems("ZABBIX");

                    assertThat(result.isDegraded()).isFalse();
                    assertThat(result.getFreshness()).containsEntry("ZABBIX", "live");
                    assertThat(result.getData())
                            .extracting(UnifiedMonitoringProblemDTO::getProblemId)
                            .containsExactly("p1");
                });
    }
}
