package tn.iteam.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(AppRedisProperties.class)
public class RedisOptionalConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory(AppRedisProperties properties) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                properties.getHost(),
                properties.getPort()
        );
        standalone.setDatabase(properties.getDatabase());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            standalone.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            standalone.setPassword(properties.getPassword());
        }

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(properties.getConnectTimeout())
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(TimeoutOptions.enabled(properties.getCommandTimeout()))
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(properties.getCommandTimeout())
                        .clientOptions(clientOptions);

        if (properties.isSsl()) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, builder.build());
        // Important: bean creation must not fail just because Redis is absent.
        factory.setValidateConnection(false);
        factory.setShareNativeConnection(true);
        return factory;
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
