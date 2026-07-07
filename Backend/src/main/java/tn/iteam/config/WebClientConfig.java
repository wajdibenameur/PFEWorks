package tn.iteam.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    @Primary
    public WebClient webClient(
            @Value("${integration.webclient.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${integration.webclient.response-timeout-ms:10000}") long responseTimeoutMs,
            @Value("${integration.webclient.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${integration.webclient.write-timeout-ms:10000}") long writeTimeoutMs
    ) {
        return buildWebClient(connectTimeoutMs, responseTimeoutMs, readTimeoutMs, writeTimeoutMs);
    }

    @Bean("zabbixMetricsWebClient")
    public WebClient zabbixMetricsWebClient(
            @Value("${integration.webclient.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${zabbix.metrics.webclient.response-timeout-ms:20000}") long responseTimeoutMs,
            @Value("${zabbix.metrics.webclient.read-timeout-ms:20000}") long readTimeoutMs,
            @Value("${integration.webclient.write-timeout-ms:10000}") long writeTimeoutMs
    ) {
        return buildWebClient(connectTimeoutMs, responseTimeoutMs, readTimeoutMs, writeTimeoutMs);
    }
    private HttpClient buildHttpClient(
            int connectTimeoutMs,
            long responseTimeoutMs,
            long readTimeoutMs,
            long writeTimeoutMs
    ) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));
    }

    private WebClient buildWebClient(
            int connectTimeoutMs,
            long responseTimeoutMs,
            long readTimeoutMs,
            long writeTimeoutMs
    ) {
        HttpClient httpClient = buildHttpClient(connectTimeoutMs, responseTimeoutMs, readTimeoutMs, writeTimeoutMs);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)
                )
                .build();
    }
}
